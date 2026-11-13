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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.graphics.BlurEffect
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
import com.pixelexperience.forall.lockscreen.ui.theme.LockMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

fun ShortcutType.icon(torchOn: Boolean): ImageVector = when (this) {
    ShortcutType.CAMERA -> Icons.Filled.CameraAlt
    ShortcutType.FLASHLIGHT -> if (torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff
    ShortcutType.DIALER -> Icons.Filled.Call
    ShortcutType.ALARM -> Icons.Filled.Alarm
    ShortcutType.ASSISTANT -> Icons.Filled.Mic
    ShortcutType.NONE -> Icons.Filled.Call
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
    val clockTint = when (settings.clockColor) {
        ClockColor.DYNAMIC -> palette.clockTint
        ClockColor.ACCENT -> palette.accent
        ClockColor.WHITE -> Color.White
    }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val unlockDist = (size.height * 0.30f).coerceAtLeast(1f)
    val requestUnlock by rememberUpdatedState(onRequestUnlock)

    suspend fun settle(vy: Float) {
        if (preview) return
        if (drag.value < -unlockDist * 0.45f || (drag.value < 0f && vy < -1800f)) {
            drag.animateTo(-unlockDist * 1.2f, LockMotion.spatial())
            requestUnlock({ ok -> if (!ok) scope.launch { drag.animateTo(0f, LockMotion.bouncy()) } })
        } else if (drag.value != 0f) {
            drag.animateTo(0f, LockMotion.bouncy())
        }
    }

    fun dragBy(dy: Float) {
        scope.launch { drag.snapTo((drag.value + dy).coerceIn(-unlockDist * 1.2f, 0f)) }
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
    val backdropImage by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, wallpaper, settings.cardBlur) {
        value = wallpaper?.let { w ->
            withContext(Dispatchers.Default) { BackdropBlur.make(w.bitmap, settings.cardBlur).asImageBitmap() }
        }
    }
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
                Image(
                    bitmap = w.image, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val p = (-drag.value / unlockDist).coerceIn(0f, 1f)
                        val sc = 1.06f + 0.07f * p
                        scaleX = sc
                        scaleY = sc
                        val r = (settings.bgBlur * 36f + 18f * p).dp.toPx()
                        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                    },
                )
            }
        }
        // 2) scrim para legibilidade
        Box(
            Modifier.fillMaxSize().drawBehind {
                val d = settings.bgDim
                val p = (-drag.value / unlockDist).coerceIn(0f, 1f)
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = (d * 0.6f + 0.1f * p).coerceIn(0f, 1f)),
                            Color.Black.copy(alpha = (d * 0.25f + 0.05f * p).coerceIn(0f, 1f)),
                            Color.Black.copy(alpha = (d * 0.9f + 0.1f + 0.1f * p).coerceIn(0f, 1f)),
                        )
                    )
                )
            }
        )
        // 3) conteúdo
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            val insets = if (preview) Modifier.padding(top = 28.dp, bottom = 10.dp)
            else Modifier.statusBarsPadding().navigationBarsPadding()
            val clockTop = with(density) { (settings.clockY * size.height).toDp() }
            val animatedTop by animateDpAsState(clockTop, LockMotion.spatial(), label = "clockY")

            Column(Modifier.fillMaxSize().then(insets)) {
                Spacer(Modifier.height(animatedTop))
                LockClock(
                    style = settings.clockStyle, scale = settings.clockScale, align = settings.clockAlign, tint = clockTint,
                    modifier = Modifier.padding(horizontal = 28.dp).layerFx(e1::value, dragFn, 0.35f, 1.4f, distFn),
                )
                Spacer(Modifier.height(16.dp))
                NotificationsList(
                    items = shown,
                    grouped = settings.groupNotifications,
                    opacity = settings.cardOpacity,
                    tint = palette.cardTint,
                    accent = palette.accent,
                    preview = preview,
                    onOpen = onOpenNotification,
                    onDismiss = onDismissNotification,
                    onClearAll = onClearAll,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp).layerFx(e2::value, dragFn, 0.6f, 1.2f, distFn),
                )
                val bob = if (preview) 0f else {
                    val t = rememberInfiniteTransition(label = "hint")
                    val v by t.animateFloat(
                        0f, -6f,
                        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "bob",
                    )
                    v
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 22.dp)
                        .layerFx(e3::value, dragFn, 1f, 1.6f, distFn),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShortcutSlot(settings.leftShortcut, torchOn, palette, settings, onShortcut)
                    Box(
                        Modifier.offset { IntOffset(0, bob.dp.roundToPx()) }
                            .width(40.dp).height(5.dp).clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.55f))
                    )
                    ShortcutSlot(settings.rightShortcut, torchOn, palette, settings, onShortcut)
                }
            }
        }
    }
}

@Composable
private fun ShortcutSlot(
    type: ShortcutType, torchOn: Boolean, palette: LockPalette, settings: LockSettings, onShortcut: (ShortcutType) -> Unit,
) {
    if (type == ShortcutType.NONE) {
        Spacer(Modifier.size(60.dp))
    } else {
        ShortcutButton(
            icon = type.icon(torchOn), description = type.label,
            active = type == ShortcutType.FLASHLIGHT && torchOn,
            accent = palette.accent, onAccent = palette.onAccent, tint = palette.cardTint,
            opacity = settings.cardOpacity, onClick = { onShortcut(type) },
        )
    }
}
