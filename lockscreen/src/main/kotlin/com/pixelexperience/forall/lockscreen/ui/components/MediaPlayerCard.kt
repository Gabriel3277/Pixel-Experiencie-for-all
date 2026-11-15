package com.pixelexperience.forall.lockscreen.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.lockscreen.media.LockMediaState
import com.pixelexperience.forall.lockscreen.media.MediaInfo
import com.pixelexperience.forall.lockscreen.ui.theme.LockMotion
import com.pixelexperience.forall.lockscreen.ui.theme.UiFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Player de mídia da lockscreen (cartão horizontal compacto, que expande ao tocar).
 * Só aparece quando há uma sessão de mídia válida; todos os controles falam com o MediaController real.
 * glass = true usa o GlassCard (vidro); false usa cartão sólido, igual ao estilo das notificações.
 */
@Composable
fun MediaPlayerCard(
    media: LockMediaState,
    tint: Color,
    onTint: Color,
    accent: Color,
    onAccent: Color,
    glass: Boolean,
    glassOpacity: Float,
    modifier: Modifier = Modifier,
    subColor: Color? = null,
    chipColor: Color? = null,
) {
    val info = media.info
    // guarda o último estado para a animação de saída ter o que desenhar
    var last by remember { mutableStateOf<MediaInfo?>(null) }
    if (info != null) SideEffect { if (last !== info) last = info }
    val shown = info ?: last
    var expanded by rememberSaveable { mutableStateOf(false) }

    AnimatedVisibility(
        visible = info != null,
        modifier = modifier,
        enter = fadeIn(LockMotion.effects()) + expandVertically(LockMotion.spatial()),
        exit = fadeOut(LockMotion.effects()) + shrinkVertically(LockMotion.spatial()),
    ) {
        if (shown != null) {
            val shape = RoundedCornerShape(28.dp)
            val body: @Composable BoxScope.() -> Unit = {
                PlayerBody(shown, media, expanded, { expanded = !expanded }, tint, onTint, accent, onAccent, subColor, chipColor)
            }
            if (glass) {
                GlassCard(modifier = Modifier.fillMaxWidth(), shape = shape, tint = tint, opacity = glassOpacity, content = body)
            } else {
                Box(Modifier.fillMaxWidth().clip(shape).background(tint, shape), content = body)
            }
        }
    }
}

@Composable
private fun PlayerBody(
    info: MediaInfo,
    media: LockMediaState,
    expanded: Boolean,
    onToggle: () -> Unit,
    tint: Color,
    onTint: Color,
    accent: Color,
    onAccent: Color,
    subColor: Color? = null,
    chipColor: Color? = null,
) {
    val sub = subColor ?: lerp(onTint, tint, 0.35f) // texto secundário: mistura opaca, sem alpha
    val chip = chipColor ?: lerp(tint, onTint, 0.12f)
    val coverSize by animateDpAsState(if (expanded) 88.dp else 64.dp, LockMotion.spatial(), label = "cover")
    val playCorner by animateDpAsState(if (info.playing) 16.dp else 28.dp, LockMotion.fastSpatial(), label = "playShape")
    val heartTint by animateColorAsState(if (info.favorite) accent else onTint, LockMotion.effects(), label = "heart")
    val pos = rememberPositionMs(info)
    val frac = if (info.durationMs > 0L) (pos.toFloat() / info.durationMs).coerceIn(0f, 1f) else 0f
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, LockMotion.spatial(), label = "chevron")

    Column(Modifier.fillMaxWidth().padding(14.dp).animateContentSize(LockMotion.spatial())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(info.art, coverSize, accent, onAccent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = info.title to info.artist,
                    transitionSpec = {
                        (fadeIn(LockMotion.effects()) + slideInVertically(LockMotion.spatial()) { it / 3 }) togetherWith
                            (fadeOut(LockMotion.effects()) + slideOutVertically(LockMotion.spatial()) { -it / 3 })
                    },
                    label = "track",
                ) { (t, a) ->
                    Column {
                        Text(
                            t, color = onTint, fontSize = 16.sp, fontFamily = UiFont,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (a.isNotBlank()) {
                            Text(
                                a, color = sub, fontSize = 13.sp, fontFamily = UiFont,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (!expanded) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ControlButton(Icons.Rounded.SkipPrevious, "Anterior", 36.dp, 22.dp, onTint, enabled = info.canPrev) { media.prev() }
                        PlayButton(info.playing, 44.dp, playCorner, accent, onAccent) { media.togglePlay() }
                        ControlButton(Icons.Rounded.SkipNext, "Próxima", 36.dp, 22.dp, onTint, enabled = info.canNext) { media.next() }
                    }
                }
            }
            if (info.canFavorite) {
                Spacer(Modifier.width(6.dp))
                ControlButton(
                    if (info.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (info.favorite) "Remover dos favoritos" else "Favoritar", 40.dp, 22.dp, heartTint, bg = chip,
                ) { media.toggleFavorite() }
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(chip).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown, if (expanded) "Recolher" else "Expandir", tint = onTint,
                    modifier = Modifier.size(22.dp).rotate(chevron),
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            val seek: ((Float) -> Unit)? =
                if (info.canSeek) ({ f: Float -> media.seekTo((f * info.durationMs).toLong()) }) else null
            ProgressBar(frac, chip, accent, 6.dp, seek)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(pos), color = sub, fontSize = 12.sp, fontFamily = UiFont)
                Text(if (info.durationMs > 0L) fmt(info.durationMs) else "", color = sub, fontSize = 12.sp, fontFamily = UiFont)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (info.canSeek) ControlButton(Icons.Rounded.Replay10, "Voltar 10 segundos", 40.dp, 24.dp, onTint) { media.seekTo(pos - 10_000L) }
                ControlButton(Icons.Rounded.SkipPrevious, "Anterior", 44.dp, 28.dp, onTint, enabled = info.canPrev) { media.prev() }
                PlayButton(info.playing, 56.dp, playCorner, accent, onAccent) { media.togglePlay() }
                ControlButton(Icons.Rounded.SkipNext, "Próxima", 44.dp, 28.dp, onTint, enabled = info.canNext) { media.next() }
                if (info.canSeek) ControlButton(Icons.Rounded.Forward10, "Avançar 10 segundos", 40.dp, 24.dp, onTint) { media.seekTo(pos + 10_000L) }
            }
            if (!info.appLabel.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    info.appLabel, color = sub, fontSize = 12.sp, fontFamily = UiFont,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (info.durationMs > 0L) {
            Spacer(Modifier.height(10.dp))
            ProgressBar(frac, chip, accent, 3.dp, null)
        }
    }
}

@Composable
private fun Cover(art: ImageBitmap?, size: Dp, accent: Color, onAccent: Color) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(if (size > 70.dp) 22.dp else 18.dp)).background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = art, animationSpec = LockMotion.effects(), label = "art") { a ->
            if (a != null) {
                Image(bitmap = a, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = onAccent, modifier = Modifier.size(size * 0.45f))
            }
        }
    }
}

/** Play/pause com morfose de forma: círculo quando pausado, quadrado arredondado quando tocando. */
@Composable
private fun PlayButton(playing: Boolean, size: Dp, corner: Dp, accent: Color, onAccent: Color, onClick: () -> Unit) {
    ControlButton(
        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        if (playing) "Pausar" else "Tocar", size, size * 0.55f, onAccent, bg = accent, corner = corner, onClick = onClick,
    )
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    iconSize: Dp,
    fg: Color,
    bg: Color = Color.Transparent,
    corner: Dp = size / 2,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, LockMotion.fastSpatial(), label = "press")
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClickLabel = description) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (enabled) fg else fg.copy(alpha = 0.38f), modifier = Modifier.size(iconSize))
    }
}

/** Barra de progresso; com onSeek (sessão aceita ACTION_SEEK_TO) vira interativa: toque e arraste. */
@Composable
private fun ProgressBar(frac: Float, track: Color, fill: Color, height: Dp, onSeek: ((Float) -> Unit)?) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown by animateFloatAsState(
        drag ?: frac,
        if (drag != null) snap<Float>() else tween<Float>(900, easing = LinearEasing),
        label = "progress",
    )
    Box(
        Modifier.fillMaxWidth()
            .height(if (onSeek != null) 28.dp else height)
            .pointerInput(onSeek) {
                if (onSeek != null) detectTapGestures { o -> onSeek((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(onSeek) {
                if (onSeek != null) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { drag?.let { onSeek(it) }; drag = null },
                        onDragCancel = { drag = null },
                        onHorizontalDrag = { c, _ ->
                            c.consume()
                            drag = (c.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(track))
        Box(Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(fill))
    }
}

/** Posição atual estimada (posição + tempo decorrido × velocidade). Só "tica" enquanto toca. */
@Composable
private fun rememberPositionMs(info: MediaInfo): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(info.playing, info.updateTime, info.positionMs) {
        now = SystemClock.elapsedRealtime()
        while (info.playing && isActive) {
            delay(1000L)
            now = SystemClock.elapsedRealtime()
        }
    }
    val p = if (info.playing && info.updateTime > 0L) {
        info.positionMs + ((now - info.updateTime) * info.speed).toLong()
    } else {
        info.positionMs
    }
    return if (info.durationMs > 0L) p.coerceIn(0L, info.durationMs) else p.coerceAtLeast(0L)
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
