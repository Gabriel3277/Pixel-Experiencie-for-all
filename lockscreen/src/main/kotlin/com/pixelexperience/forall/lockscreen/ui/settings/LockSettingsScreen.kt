@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.pixelexperience.forall.lockscreen.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixelexperience.forall.lockscreen.LockScreenModule
import com.pixelexperience.forall.lockscreen.data.BuiltInWallpapers
import com.pixelexperience.forall.lockscreen.data.ClockAlign
import com.pixelexperience.forall.lockscreen.data.ClockColor
import com.pixelexperience.forall.lockscreen.data.ClockStyle
import com.pixelexperience.forall.lockscreen.data.LockSettings
import com.pixelexperience.forall.lockscreen.data.LockSettingsRepository
import com.pixelexperience.forall.lockscreen.data.ShortcutType
import com.pixelexperience.forall.lockscreen.data.WallpaperRepository
import com.pixelexperience.forall.lockscreen.data.WallpaperUi
import com.pixelexperience.forall.lockscreen.notifications.LockNotification
import com.pixelexperience.forall.lockscreen.ui.LockScreenContent
import com.pixelexperience.forall.lockscreen.ui.rememberWallpaper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun sample(): List<LockNotification> {
    val now = System.currentTimeMillis()
    fun n(k: String, pkg: String, app: String, title: String, text: String, big: String, color: Int) = LockNotification(
        key = k, packageName = pkg, appName = app, icon = null, color = color, title = title, text = text,
        bigText = big, postTime = now, clearable = true, ongoing = false, visibility = 1, contentIntent = null,
    )
    return listOf(
        n("s1", "demo.chat", "Mensagens", "Ana", "Bora jogar Minecraft hoje?", "Bora jogar Minecraft hoje? Já atualizei o modpack pra 1.21.11.", 0xFF2E9E6B.toInt()),
        n("s2", "demo.chat", "Mensagens", "Grupo", "Quem vai hoje?", "", 0xFF2E9E6B.toInt()),
        n("s3", "demo.agenda", "Agenda", "Reunião às 15:00", "Sala 2", "", 0xFF4A7BD9.toInt()),
    )
}

@Composable
fun LockSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { LockSettingsRepository.get(ctx) }
    val s by repo.settings.collectAsStateWithLifecycle()
    val wallpaper = rememberWallpaper(s.wallpaperId, s.wallpaperVersion)
    val demo = remember { sample() }
    val scope = rememberCoroutineScope()

    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val hasOverlay = remember(tick) { LockScreenModule.hasOverlayPermission(ctx) }
    val hasListener = remember(tick) { LockScreenModule.hasNotificationAccess(ctx) }

    fun set(block: (LockSettings) -> LockSettings) = repo.update(block)

    val postNotifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { com.pixelexperience.forall.lockscreen.data.WeatherRepository.refresh(ctx, force = true) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (WallpaperRepository.saveCustom(ctx, uri)) {
                set { it.copy(wallpaperId = "custom", wallpaperVersion = it.wallpaperVersion + 1) }
            }
        }
    }

    var page by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = page != null) { page = null }
    val scroll = remember(page) { ScrollState(0) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (page != null) page = null else onBack() }, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(page ?: "Tela de bloqueio Pixel", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (page != "Ativação") PhonePreview(s, wallpaper, demo)
            when (page) {
                null -> {
                    CatGroup(
                        listOf(Cat(Icons.Filled.PowerSettingsNew, P_BLUE, "Ativação", if (s.enabled) "Ativada" else "Desativada · permissões", "Ativação")),
                    ) { page = it }
                    CatGroup(
                        listOf(
                            Cat(Icons.Filled.Wallpaper, P_PURPLE, "Papel de parede e cores", "Fundos prontos ou da galeria", "Papel de parede e cores"),
                            Cat(Icons.Filled.Schedule, P_GREEN, "Relógio", s.clockStyle.label + " · " + s.clockAlign.label, "Relógio"),
                            Cat(Icons.Filled.BlurOn, P_CYAN, "Blur do fundo", "Desfoque e escurecimento", "Blur do fundo"),
                        ),
                    ) { page = it }
                    CatGroup(
                        listOf(
                            Cat(Icons.Filled.Notifications, P_ORANGE, "Notificações", "Cards, agrupamento e privacidade", "Notificações"),
                            Cat(Icons.Filled.Apps, P_PINK, "Atalhos inferiores", s.leftShortcut.label + " · " + s.rightShortcut.label, "Atalhos inferiores"),
                        ),
                    ) { page = it }
                }
                "Ativação" -> {
                Section("Ativação") {
                    Text(
                        "O Android não permite que apps troquem a tela de bloqueio do sistema. Esta tela aparece por cima dela " +
                            "quando a tela liga; PIN, digital e rosto continuam sendo do Android.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PermissionRow("Exibir sobre outros apps", hasOverlay) {
                        ctx.startActivity(LockScreenModule.overlaySettingsIntent(ctx))
                    }
                    PermissionRow("Acesso às notificações", hasListener) {
                        runCatching { ctx.startActivity(LockScreenModule.notificationAccessIntent(ctx)) }
                            .onFailure { ctx.startActivity(LockScreenModule.notificationAccessIntent()) }
                    }
                    SwitchRow("Ativar tela de bloqueio Pixel", s.enabled) { on ->
                        if (on && !hasOverlay) {
                            ctx.startActivity(LockScreenModule.overlaySettingsIntent(ctx))
                        } else {
                            if (on && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) postNotifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            set { it.copy(enabled = on) }
                            LockScreenModule.sync(ctx)
                        }
                    }
                }
                }
                "Papel de parede e cores" -> {
                Section("Papel de parede e cores") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BuiltInWallpapers.ids.forEach { id ->
                            val thumb = remember(id) { BuiltInWallpapers.render(id, 90, 195).asImageBitmap() }
                            val sel = s.wallpaperId == "builtin:$id"
                            Image(
                                thumb, BuiltInWallpapers.label(id), contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp, 100.dp).clip(RoundedCornerShape(16.dp))
                                    .border(if (sel) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(16.dp))
                                    .clickable { set { it.copy(wallpaperId = "builtin:$id") } },
                            )
                        }
                        Box(
                            Modifier.size(56.dp, 100.dp).clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .border(if (s.wallpaperId == "custom") BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(0.dp, Color.Transparent), RoundedCornerShape(16.dp))
                                .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.AddPhotoAlternate, "Escolher da galeria") }
                    }
                    Text(
                        "As cores do relógio e dos cards seguem o papel de parede escolhido.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
                "Relógio" -> {
                Section("Relógio") {
                    FilledTonalButton(
                        onClick = { ctx.startActivity(android.content.Intent(ctx, com.pixelexperience.forall.lockscreen.widget.ClockWidgetPickerActivity::class.java)) },
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("Escolher relógio de widget") }
                    ChipRow(ClockStyle.entries, s.clockStyle, { it.label }) { v -> set { it.copy(clockStyle = v) } }
                    ChipRow(ClockAlign.entries, s.clockAlign, { it.label }) { v -> set { it.copy(clockAlign = v) } }
                    ChipRow(ClockColor.entries, s.clockColor, { it.label }) { v -> set { it.copy(clockColor = v) } }
                    SliderRow("Tamanho", s.clockScale, 0.7f..1.4f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(clockScale = v) } }
                    SliderRow("Posição vertical", s.clockY, 0.02f..0.45f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(clockY = v) } }
                    SwitchRow("Mostrar clima ao lado da data", s.showWeather) { on ->
                        if (on && ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                        set { it.copy(showWeather = on) }
                    }
                    Text(
                        "Usa sua localização aproximada (última conhecida) e a Open-Meteo, sem chave nem conta. Só as coordenadas aproximadas são enviadas.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
                "Blur do fundo" -> {
                Section("Blur do fundo") {
                    SliderRow("Desfoque", s.bgBlur, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(bgBlur = v) } }
                    SliderRow("Escurecimento", s.bgDim, 0f..0.7f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(bgDim = v) } }
                }
                }
                "Notificações" -> {
                Section("Notificações") {
                    SliderRow("Transparência dos cards", s.cardOpacity, 0.1f..0.9f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(cardOpacity = v) } }
                    SliderRow("Blur dos cards", s.cardBlur, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(cardBlur = v) } }
                    SwitchRow("Agrupar por app", s.groupNotifications) { v -> set { it.copy(groupNotifications = v) } }
                    SwitchRow("Ocultar notificações em andamento", s.hideOngoing) { v -> set { it.copy(hideOngoing = v) } }
                    SwitchRow("Mostrar conteúdo com bloqueio seguro", s.showContentWhenSecure) { v -> set { it.copy(showContentWhenSecure = v) } }
                    Text(
                        "Desligado: com PIN/senha/digital configurados, o texto das notificações fica oculto até desbloquear.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                }
                "Atalhos inferiores" -> {
                Section("Atalhos inferiores") {
                    Text("Esquerda", style = MaterialTheme.typography.labelLarge)
                    ChipRow(ShortcutType.entries, s.leftShortcut, { it.label }) { v -> set { it.copy(leftShortcut = v) } }
                    Text("Direita", style = MaterialTheme.typography.labelLarge)
                    ChipRow(ShortcutType.entries, s.rightShortcut, { it.label }) { v -> set { it.copy(rightShortcut = v) } }
                }
                }
                else -> Unit
            }
        }
    }
}

/** Preview em tempo real: a própria LockScreenContent renderizada em 360x780dp e reduzida. */
@Composable
private fun PhonePreview(s: LockSettings, wallpaper: WallpaperUi?, demo: List<LockNotification>) {
    val vw = 360.dp
    val vh = 780.dp
    val h = 300.dp
    val scale = h / vh
    val shape = RoundedCornerShape(26.dp)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(vw * scale, h).clip(shape).border(2.dp, Color.White.copy(alpha = 0.2f), shape).background(Color.Black)) {
            Box(
                Modifier.requiredSize(vw, vh).align(Alignment.TopStart).graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            ) {
                LockScreenContent(
                    settings = s, wallpaper = wallpaper, notifications = demo, torchOn = false,
                    preview = true, previewScale = scale,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: (Float) -> String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(display(value), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (granted) "Concedida" else "Necessária",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        if (!granted) FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) { Text("Conceder") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            ToggleButton(checked = o == selected, onCheckedChange = { onSelect(o) }, shapes = ToggleButtonDefaults.shapes()) { Text(label(o)) }
        }
    }
}


private val P_BLUE = Color(0xFFB9D7FF)
private val P_PURPLE = Color(0xFFE4C9F7)
private val P_GREEN = Color(0xFFC5E8C9)
private val P_CYAN = Color(0xFFB5E6F2)
private val P_ORANGE = Color(0xFFFFE0A8)
private val P_PINK = Color(0xFFF7C6DD)

private class Cat(val icon: ImageVector, val color: Color, val title: String, val sub: String, val key: String)

@Composable
private fun CatGroup(items: List<Cat>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { i, c ->
            val top = if (i == 0) 28.dp else 6.dp
            val bottom = if (i == items.lastIndex) 28.dp else 6.dp
            CategoryRow(c.icon, c.color, c.title, c.sub, RoundedCornerShape(top, top, bottom, bottom)) { onOpen(c.key) }
        }
    }
}

@Composable
private fun CategoryRow(icon: ImageVector, iconBg: Color, title: String, subtitle: String, shape: Shape, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    Surface(
        onClick = onClick, interactionSource = src, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = sc; scaleY = sc },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color(0xFF1B1B1F), modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
