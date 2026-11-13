@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.pixelexperience.forall.lockscreen.ui.settings

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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            if (WallpaperRepository.saveCustom(ctx, uri)) {
                set { it.copy(wallpaperId = "custom", wallpaperVersion = it.wallpaperVersion + 1) }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text("Tela de bloqueio Pixel", style = MaterialTheme.typography.titleLarge)
        }
        PhonePreview(s, wallpaper, demo)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
                    ctx.startActivity(LockScreenModule.notificationAccessIntent())
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

            Section("Relógio") {
                ChipRow(ClockStyle.entries, s.clockStyle, { it.label }) { v -> set { it.copy(clockStyle = v) } }
                ChipRow(ClockAlign.entries, s.clockAlign, { it.label }) { v -> set { it.copy(clockAlign = v) } }
                ChipRow(ClockColor.entries, s.clockColor, { it.label }) { v -> set { it.copy(clockColor = v) } }
                SliderRow("Tamanho", s.clockScale, 0.7f..1.4f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(clockScale = v) } }
                SliderRow("Posição vertical", s.clockY, 0.02f..0.45f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(clockY = v) } }
            }

            Section("Blur do fundo") {
                SliderRow("Desfoque", s.bgBlur, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(bgBlur = v) } }
                SliderRow("Escurecimento", s.bgDim, 0f..0.7f, { "${(it * 100).roundToInt()}%" }) { v -> set { it.copy(bgDim = v) } }
            }

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

            Section("Atalhos inferiores") {
                Text("Esquerda", style = MaterialTheme.typography.labelLarge)
                ChipRow(ShortcutType.entries, s.leftShortcut, { it.label }) { v -> set { it.copy(leftShortcut = v) } }
                Text("Direita", style = MaterialTheme.typography.labelLarge)
                ChipRow(ShortcutType.entries, s.rightShortcut, { it.label }) { v -> set { it.copy(rightShortcut = v) } }
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
        if (!granted) FilledTonalButton(onClick = onClick) { Text("Conceder") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}
