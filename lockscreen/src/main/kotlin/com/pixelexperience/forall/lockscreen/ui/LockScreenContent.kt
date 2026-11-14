package com.pixelexperience.forall.lockscreen.ui

import android.app.KeyguardManager
import android.app.Notification
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pixelexperience.forall.lockscreen.data.WeatherUi
import com.pixelexperience.forall.lockscreen.ui.theme.lockFont
import java.util.Locale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixelexperience.forall.lockscreen.data.WeatherRepository
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.pixelexperience.forall.lockscreen.data.BackdropBlur
import com.pixelexperience.forall.lockscreen.data.ClockColor
import com.pixelexperience.forall.lockscreen.data.LockPalette
import com.pixelexperience.forall.lockscreen.data.LockSettings
import com.pixelexperience.forall.lockscreen.data.ShortcutType
import com.pixelexperience.forall.lockscreen.data.WallpaperRepository
import com.pixelexperience.forall.lockscreen.data.WallpaperUi
import com.pixelexperience.forall.lockscreen.notifications.LockNotification
import com.pixelexperience.forall.lockscreen.ui.clock.LockClock
import com.pixelexperience.forall.lockscreen.ui.components.Backdrop
import com.pixelexperience.forall.lockscreen.ui.components.LocalBackdrop
import com.pixelexperience.forall.lockscreen.ui.components.ShortcutButton
import androidx.compose.material3.ColorScheme
import com.pixelexperience.forall.lockscreen.ui.theme.LockMotion
import com.pixelexperience.forall.lockscreen.ui.theme.lockColorScheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

fun ShortcutType.icon(torchOn: Boolean): ImageVector = when (this) {
    ShortcutType.CAMERA -> Icons.Rounded.CameraAlt
    ShortcutType.FLASHLIGHT -> Icons.Rounded.FlashlightOn
    ShortcutType.DIALER -> Icons.Rounded.Call
    ShortcutType.ALARM -> Icons.Rounded.Alarm
    ShortcutType.ASSISTANT -> Icons.Rounded.Mic
    ShortcutType.NONE -> Icons.Rounded.Call
}

@Composable
fun rememberWallpaper(id: String, version: Int): WallpaperUi? {
    val app = LocalContext.current.applicationContext
    val state = produceState<WallpaperUi?>(initialValue = null, id, version) {
        value = WallpaperRepository.load(app, id, version)
    }
    return state.value
}

private fun Modifier.layerFx(enter: () -> Float, drag: () -> Float, parallax: Float, fade: Float, unlockDist: () -> Float): Modifier =
    graphicsLayer {
        val e = enter()
        val d = drag()
        val p = (-d / unlockDist()).coerceIn(0f, 1f)
        alpha = (e * (1f - p * fade)).coerceIn(0f, 1f)
        translationY = (1f - e) * 36.dp.toPx() + d * parallax
        val s = (0.94f + 0.06f * e) * (1f - 0.04f * p)
        scaleX = s
        scaleY = s
    }

/**
 * Tela de bloqueio Pixel. Usada de verdade na LockScreenActivity e, com preview=true, na tela de ajustes.
 * onRequestUnlock recebe um callback(ok): a Activity chama KeyguardManager.requestDismissKeyguard.
 */
@Composable
fun LockScreenContent(
    settings: LockSettings,
    wallpaper: WallpaperUi?,
    notifications: List<LockNotification>,
    torchOn: Boolean,
    modifier: Modifier = Modifier,
    unlockSignal: Boolean = false,
    onUnlockAnimated: () -> Unit = {},
    preview: Boolean = false,
    previewScale: Float = 1f,
    onShortcut: (ShortcutType) -> Unit = {},
    onOpenNotification: (LockNotification) -> Unit = {},
    onDismissNotification: (String) -> Unit = {},
    onClearAll: () -> Unit = {},
    onRequestUnlock: ((Boolean) -> Unit) -> Unit = {},
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val palette = wallpaper?.palette ?: LockPalette.Default
    // Dynamic Color a partir do wallpaper: todos os papéis (clock, cards, atalhos) vêm do mesmo esquema tonal
    val scheme = remember(palette.accent) { lockColorScheme(palette.accent) }
    val clockTint = when (settings.clockColor) {
        ClockColor.DYNAMIC -> scheme.onPrimaryContainer
        ClockColor.ACCENT -> scheme.primary
        ClockColor.WHITE -> Color.White
    }
    val clockTint2 = when (settings.clockColor) {
        ClockColor.DYNAMIC -> scheme.onTertiaryContainer
        ClockColor.ACCENT -> scheme.tertiary
        ClockColor.WHITE -> Color.White.copy(alpha = 0.92f)
    }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val unlockDist = (size.height * 0.30f).coerceAtLeast(1f)
    val requestUnlock by rememberUpdatedState(onRequestUnlock)
    // evita pedir o desbloqueio duas vezes (onDragStopped + onPostFling disparam juntos)
    var unlocking by remember { mutableStateOf(false) }
    val weather by WeatherRepository.state.collectAsStateWithLifecycle()
    LaunchedEffect(settings.showWeather) { if (settings.showWeather) WeatherRepository.refresh(ctx) }
    // desbloqueio por biometria nativa: o conteúdo sobe e some com a mesma mola do gesto
    LaunchedEffect(unlockSignal) {
        if (unlockSignal && !preview) {
            unlocking = true
            drag.animateTo(-(size.height * 0.30f).coerceAtLeast(1f) * 1.2f, LockMotion.spatial())
            onUnlockAnimated()
        }
    }

    suspend fun settle(vy: Float) {
        if (preview || unlocking) return
        if (drag.value < -unlockDist * 0.45f || (drag.value < 0f && vy < -1800f)) {
            unlocking = true
            drag.animateTo(-unlockDist * 1.2f, LockMotion.spatial())
            requestUnlock { ok ->
                if (!ok) {
                    unlocking = false
                    scope.launch { drag.animateTo(0f, LockMotion.bouncy()) }
                }
            }
        } else if (drag.value != 0f) {
            drag.animateTo(0f, LockMotion.bouncy())
        }
    }

    fun dragBy(dy: Float) {
        if (unlocking) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            drag.snapTo((drag.value + dy).coerceIn(-unlockDist * 1.2f, 0f))
        }
    }

    val connection = remember(unlockDist, preview) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!preview && source == NestedScrollSource.UserInput && drag.value < 0f && available.y > 0f) {
                    val c = min(available.y, -drag.value)
                    dragBy(c)
                    return Offset(0f, c)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!preview && source == NestedScrollSource.UserInput && available.y < 0f) {
                    dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                settle(available.y)
                return Velocity.Zero
            }
        }
    }

    // vidro dos cards: miniatura borrada do wallpaper (recalcula só quando o nível de blur muda)
    val backdropImage by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null) { value = null }
    val backdrop = remember(backdropImage) { backdropImage?.let { Backdrop(it) } }
    SideEffect {
        backdrop?.rootSize = Size(size.width.toFloat(), size.height.toFloat())
        backdrop?.coordScale = previewScale
    }

    // privacidade: com bloqueio seguro, esconde o conteúdo (a menos que o usuário permita)
    val km = remember { ctx.getSystemService(KeyguardManager::class.java) }
    val secure = !preview && km?.isDeviceSecure == true
    val shown = remember(notifications, secure, settings.hideOngoing, settings.showContentWhenSecure) {
        notifications
            .filter { !(settings.hideOngoing && it.ongoing) }
            .filter { !(secure && it.visibility == Notification.VISIBILITY_SECRET) }
            .map { n ->
                if (secure && !settings.showContentWhenSecure) n.copy(title = n.appName, text = "Conteúdo oculto", bigText = "")
                else n
            }
    }

    // animação de entrada escalonada
    var stage by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(40); stage = 1; delay(90); stage = 2; delay(90); stage = 3 }
    val e1 = animateFloatAsState(if (stage >= 1) 1f else 0f, LockMotion.spatial(), label = "e1")
    val e2 = animateFloatAsState(if (stage >= 2) 1f else 0f, LockMotion.spatial(), label = "e2")
    val e3 = animateFloatAsState(if (stage >= 3) 1f else 0f, LockMotion.spatial(), label = "e3")
    val dragFn = { drag.value }
    val distFn = { unlockDist }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size = it }
            .onGloballyPositioned { backdrop?.origin = it.positionInRoot() }
            .nestedScroll(connection)
            .draggable(
                orientation = Orientation.Vertical,
                enabled = !preview,
                state = rememberDraggableState { dy -> dragBy(dy) },
                onDragStopped = { v -> settle(v) },
            )
    ) {
        // 1) wallpaper: blur configurável + profundidade (escala e blur crescem ao arrastar)
        Crossfade(targetState = wallpaper, animationSpec = tween(500), label = "wp") { w ->
            if (w != null) {
                val blurDp = settings.bgBlur * 36f
                Box(Modifier.fillMaxSize()) {
                    // camada 1: wallpaper com blur leve + profundidade (escala cresce ao arrastar)
                    Image(
                        bitmap = w.image, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            val p = (-drag.value / unlockDist).coerceIn(0f, 1f)
                            val sc = 1.06f + 0.07f * p
                            scaleX = sc
                            scaleY = sc
                            val r = (blurDp * 0.35f + 14f * p).dp.toPx()
                            renderEffect = if (!preview && r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                        },
                    )
                    // camada 2: blur forte com máscara progressiva (transparente no topo -> opaco embaixo, onde ficam os cards)
                    if (!preview && blurDp > 1f) {
                        Image(
                            bitmap = w.image, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    val p = (-drag.value / unlockDist).coerceIn(0f, 1f)
                                    val sc = 1.06f + 0.07f * p
                                    scaleX = sc
                                    scaleY = sc
                                    val r = (blurDp * 1.1f + 10f + 20f * p).dp.toPx()
                                    renderEffect = BlurEffect(r, r, TileMode.Clamp)
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent, 0.32f to Color.Transparent,
                                            0.70f to Color.Black.copy(alpha = 0.85f), 1f to Color.Black,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                        )
                    }
                }
            }
        }
        // 2) scrim + integração com o relógio: gradiente de legibilidade, brilho tonal atrás dos números e vinheta
        Box(
            Modifier.fillMaxSize().drawBehind {
                val w = this.size.width
                val h = this.size.height
                val d = settings.bgDim
                val p = (-drag.value / unlockDist).coerceIn(0f, 1f)
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = (d * 0.55f + 0.08f + 0.1f * p).coerceIn(0f, 1f)),
                        0.35f to Color.Black.copy(alpha = (d * 0.30f + 0.10f + 0.05f * p).coerceIn(0f, 1f)),
                        0.70f to Color.Black.copy(alpha = (d * 0.16f + 0.02f + 0.08f * p).coerceIn(0f, 1f)),
                        1f to Color.Black.copy(alpha = (d * 0.42f + 0.06f + 0.1f * p).coerceIn(0f, 1f)),
                    )
                )
                val cy = settings.clockY * h + 150.dp.toPx()
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.12f * (1f - p)), Color.Transparent),
                        center = Offset(w / 2f, cy), radius = w * 0.95f,
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)),
                        center = Offset(w / 2f, h * 0.45f), radius = h * 0.78f,
                    )
                )
            }
        )
        // 3) conteúdo
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            val insets = if (preview) Modifier.padding(top = 28.dp, bottom = 10.dp)
            else Modifier.statusBarsPadding().navigationBarsPadding()
            val clockTop = with(density) { (settings.clockY * size.height).toDp() }
            val animatedTopRaw by animateDpAsState(clockTop, LockMotion.spatial(), label = "clockY")
            val animatedTop = animatedTopRaw.coerceAtLeast(0.dp) // a mola pode passar de 0 e gerar altura negativa

            Column(Modifier.fillMaxSize().then(insets)) {
                val gw = if (settings.showWeather) weather?.takeIf { it.tomorrowMax != null } else null
                var glanceH by remember { mutableIntStateOf(0) }
                if (gw != null) {
                    GlanceInfo(
                        gw, clockTint,
                        Modifier.padding(start = 28.dp, end = 28.dp, top = 8.dp).onSizeChanged { glanceH = it.height }
                            .layerFx(e1::value, dragFn, 0.25f, 1.4f, distFn),
                    )
                }
                val glanceDp = if (gw != null) with(density) { glanceH.toDp() } else 0.dp
                Spacer(Modifier.height((animatedTop - glanceDp).coerceAtLeast(if (gw != null) 16.dp else 0.dp)))
                LockClock(
                    style = settings.clockStyle, scale = settings.clockScale, align = settings.clockAlign, tint = clockTint, tint2 = clockTint2,
                    weather = if (settings.showWeather) weather else null,
                    modifier = Modifier.padding(horizontal = 28.dp).layerFx(e1::value, dragFn, 0.35f, 1.4f, distFn),
                )
                Spacer(Modifier.height(16.dp))
                NotificationsList(
                    items = shown,
                    grouped = settings.groupNotifications,
                    opacity = settings.cardOpacity,
                    tint = scheme.surfaceContainerHigh,
                    accent = scheme.primary,
                    preview = preview,
                    onOpen = onOpenNotification,
                    onDismiss = onDismissNotification,
                    onClearAll = onClearAll,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).layerFx(e2::value, dragFn, 0.6f, 1.2f, distFn),
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 20.dp, bottom = 32.dp)
                        .layerFx(e3::value, dragFn, 1f, 1.6f, distFn),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShortcutSlot(settings.leftShortcut, torchOn, scheme, settings, onShortcut)
                    Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        val bat = rememberBatteryText()
                        if (bat != null) {
                            Text(
                                bat, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontFamily = lockFont(600), fontSize = 15.sp, letterSpacing = 0.1.sp,
                                    color = Color.White.copy(alpha = 0.92f),
                                    shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 8f),
                                ),
                            )
                        }
                    }
                    ShortcutSlot(settings.rightShortcut, torchOn, scheme, settings, onShortcut)
                }
            }
        }
    }
}

@Composable
private fun ShortcutSlot(
    type: ShortcutType, torchOn: Boolean, scheme: ColorScheme, settings: LockSettings, onShortcut: (ShortcutType) -> Unit,
) {
    if (type == ShortcutType.NONE) {
        Spacer(Modifier.size(64.dp))
    } else {
        ShortcutButton(
            icon = type.icon(torchOn), description = type.label,
            active = type == ShortcutType.FLASHLIGHT && torchOn,
            accent = scheme.primary, onAccent = scheme.onPrimary,
            tint = scheme.onPrimaryContainer, opacity = 1f, iconColor = scheme.primaryContainer,
            onClick = { onShortcut(type) },
        )
    }
}

private fun isPt() = Locale.getDefault().language == "pt"

/** "At a Glance" do Pixel: previsão de amanhã e comparação com hoje (canto superior esquerdo). */
@Composable
private fun GlanceInfo(w: WeatherUi, tint: Color, modifier: Modifier = Modifier) {
    val unit = if (w.fahrenheit) "°F" else "°C"
    val tm = w.tomorrowMax ?: return
    val pt = isPt()
    val l1 = if (pt) "$tm$unit" + (w.city?.let { " em $it" } ?: "") + " amanhã"
    else "$tm$unit" + (w.city?.let { " in $it" } ?: "") + " tomorrow"
    val diff = w.todayMax?.let { tm - it }
    val l2 = when {
        diff == null -> null
        diff == 0 -> if (pt) "Mesma temperatura de hoje" else "Same temperature as today"
        diff > 0 -> if (pt) "$diff° mais quente que hoje" else "$diff° warmer than today"
        else -> if (pt) "${-diff}° mais frio que hoje" else "${-diff}° cooler than today"
    }
    val sh = Shadow(Color.Black.copy(alpha = 0.30f), Offset(0f, 2f), 10f)
    androidx.compose.foundation.layout.Column(modifier) {
        Text(l1, style = TextStyle(fontFamily = lockFont(500), fontSize = 20.sp, color = tint, shadow = sh), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (l2 != null) Text(l2, style = TextStyle(fontFamily = lockFont(400), fontSize = 16.sp, color = tint.copy(alpha = 0.85f), shadow = sh))
    }
}

/** Texto da bateria (como a linha central do Pixel): carregando / carregada / porcentagem. */
@Composable
private fun rememberBatteryText(): String? {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf<String?>(null) }
    DisposableEffect(ctx) {
        val pt = isPt()
        fun read(i: Intent?) {
            if (i == null) return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            if (level < 0) return
            val pct = level * 100 / scale
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val full = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_FULL
            text = when {
                full -> if (pt) "Carregada · 100%" else "Fully charged · 100%"
                plugged -> if (pt) "Carregando · $pct%" else "Charging · $pct%"
                else -> if (pt) "$pct% de bateria" else "$pct% battery"
            }
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = read(i)
        }
        read(ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED))
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    return text
}
