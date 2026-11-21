package com.pixelexperience.forall.lockscreen.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.lockscreen.media.LockMediaState
import com.pixelexperience.forall.lockscreen.media.MediaInfo
import com.pixelexperience.forall.lockscreen.ui.theme.LockMotion
import com.pixelexperience.forall.lockscreen.ui.theme.UiFont
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Player de mídia da lockscreen (cartão horizontal compacto, que expande ao tocar).
 * Só aparece quando há uma sessão de mídia válida; todos os controles falam com o MediaController real.
 * glass = true usa o GlassCard (vidro); false usa cartão sólido, igual ao estilo das notificações.
 *
 * Desempenho: UMA animação (t: 0 = compacto, 1 = expandido) lida só nas fases de layout/desenho,
 * então expandir/recolher não recompõe nada por quadro. O tique do progresso (1 s) recompõe só a
 * barra e o texto do tempo, não o cartão inteiro.
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

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Altura = altura cheia × fração (lida só no layout). O conteúdo é medido uma vez e recortado. */
private fun Modifier.heightFraction(fraction: () -> Float): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val h = (p.height * fraction().coerceIn(0f, 1f)).roundToInt()
        layout(p.width, h) { p.place(0, 0) }
    }

/** Aparece (fade + sobe um pouco) a partir de `from` (0..1) do progresso t. Só fase de desenho. */
private fun Modifier.stagger(t: () -> Float, from: Float): Modifier = graphicsLayer {
    val a = ((t() - from) / (1f - from)).coerceIn(0f, 1f)
    alpha = a
    translationY = (1f - a) * 12.dp.toPx()
}

/** Capa: ocupa de 64 a 88 dp no layout, mas é medida/desenhada sempre em 88 dp e só escalada na GPU. */
private fun Modifier.coverBox(t: () -> Float): Modifier = layout { measurable, _ ->
    val big = 88.dp.roundToPx()
    val p = measurable.measure(Constraints.fixed(big, big))
    val s = mix(64.dp.toPx(), 88.dp.toPx(), t().coerceIn(0f, 1f)).roundToInt()
    layout(s, s) {
        val sc = s.toFloat() / big
        p.placeWithLayer(0, 0) {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = sc
            scaleY = sc
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
    // t: 0 = compacto, 1 = expandido. É um State lido só em layout/desenho (sem `by`, de propósito).
    val t = animateFloatAsState(if (expanded) 1f else 0f, tween<Float>(320, easing = FastOutSlowInEasing), label = "expand")
    val compactVisible by remember { derivedStateOf { t.value < 0.999f } }
    val expandedVisible by remember { derivedStateOf { t.value > 0.001f } }
    val playCorner by animateDpAsState(if (info.playing) 16.dp else 28.dp, LockMotion.fastSpatial(), label = "playShape")
    val heartTint by animateColorAsState(if (info.favorite) accent else onTint, LockMotion.effects(), label = "heart")
    val pos = rememberPositionMs(info)
    val fracOf: () -> Float = {
        if (info.durationMs > 0L) (pos.value.toFloat() / info.durationMs).coerceIn(0f, 1f) else 0f
    }

    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(info.art, info.playing, accent, onAccent, Modifier.coverBox { t.value })
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = info.title to info.artist,
                    transitionSpec = {
                        (fadeIn(LockMotion.effects()) + slideInVertically(LockMotion.spatial()) { it / 3 }) togetherWith
                            (fadeOut(LockMotion.effects()) + slideOutVertically(LockMotion.spatial()) { -it / 3 })
                    },
                    label = "track",
                ) { (ti, a) ->
                    Column {
                        Text(
                            ti, color = onTint, fontSize = 16.sp, fontFamily = UiFont,
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
                if (!expanded || compactVisible) {
                    Column(
                        Modifier
                            .heightFraction { 1f - t.value }
                            .graphicsLayer { alpha = (1f - t.value * 2f).coerceIn(0f, 1f) },
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ControlButton(Icons.Rounded.SkipPrevious, "Anterior", 36.dp, 22.dp, onTint, enabled = info.canPrev) { media.prev() }
                            PlayButton(info.playing, 44.dp, playCorner, accent, onAccent) { media.togglePlay() }
                            ControlButton(Icons.Rounded.SkipNext, "Próxima", 36.dp, 22.dp, onTint, enabled = info.canNext) { media.next() }
                        }
                    }
                }
            }
            if (info.canFavorite) {
                Spacer(Modifier.width(6.dp))
                ControlButton(
                    if (info.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (info.favorite) "Remover dos favoritos" else "Favoritar", 40.dp, 22.dp, heartTint, bg = chip, morph = true,
                ) { media.toggleFavorite() }
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(chip).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown, if (expanded) "Recolher" else "Expandir", tint = onTint,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = 180f * t.value },
                )
            }
        }

        // a barra é única: só muda de espessura/área de toque entre compacto e expandido
        if (expanded || info.durationMs > 0L) {
            Spacer(Modifier.height(10.dp))
            val seek: ((Float) -> Unit)? =
                if (expanded && info.canSeek) ({ f: Float -> media.seekTo((f * info.durationMs).toLong()) }) else null
            ProgressBar(fracOf, chip, accent, { t.value }, info.canSeek, seek)
        }

        if (expanded || expandedVisible) {
            Column(Modifier.fillMaxWidth().heightFraction { t.value }) {
                Row(
                    Modifier.fillMaxWidth().stagger({ t.value }, 0.15f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TimeText(pos, sub)
                    Text(if (info.durationMs > 0L) fmt(info.durationMs) else "", color = sub, fontSize = 12.sp, fontFamily = UiFont)
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().stagger({ t.value }, 0.3f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (info.canSeek) ControlButton(Icons.Rounded.Replay10, "Voltar 10 segundos", 40.dp, 24.dp, onTint) { media.seekTo(pos.value - 10_000L) }
                    ControlButton(Icons.Rounded.SkipPrevious, "Anterior", 44.dp, 28.dp, onTint, enabled = info.canPrev) { media.prev() }
                    PlayButton(info.playing, 56.dp, playCorner, accent, onAccent) { media.togglePlay() }
                    ControlButton(Icons.Rounded.SkipNext, "Próxima", 44.dp, 28.dp, onTint, enabled = info.canNext) { media.next() }
                    if (info.canSeek) ControlButton(Icons.Rounded.Forward10, "Avançar 10 segundos", 40.dp, 24.dp, onTint) { media.seekTo(pos.value + 10_000L) }
                }
                if (!info.appLabel.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        info.appLabel, color = sub, fontSize = 12.sp, fontFamily = UiFont,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().stagger({ t.value }, 0.5f),
                    )
                }
            }
        }
    }
}

/** Texto do tempo atual: só ele recompõe a cada segundo. */
@Composable
private fun TimeText(pos: State<Long>, color: Color) {
    Text(fmt(pos.value), color = color, fontSize = 12.sp, fontFamily = UiFont)
}

@Composable
private fun Cover(art: ImageBitmap?, playing: Boolean, accent: Color, onAccent: Color, modifier: Modifier = Modifier) {
    // capa "respira": levemente menor quando pausada (só desenho)
    val ps = animateFloatAsState(if (playing) 1f else 0.93f, LockMotion.spatial(), label = "coverScale")
    Box(
        modifier
            .size(88.dp)
            .graphicsLayer { scaleX = ps.value; scaleY = ps.value }
            .clip(RoundedCornerShape(24.dp))
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = art, animationSpec = LockMotion.effects(), label = "art") { a ->
            if (a != null) {
                Image(bitmap = a, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.MusicNote, null, tint = onAccent, modifier = Modifier.size(40.dp))
            }
        }
    }
}

/** Play/pause com morfose de forma: círculo quando pausado, quadrado arredondado quando tocando. */
@Composable
private fun PlayButton(playing: Boolean, size: Dp, corner: Dp, accent: Color, onAccent: Color, onClick: () -> Unit) {
    ControlButton(
        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        if (playing) "Pausar" else "Tocar", size, size * 0.55f, onAccent, bg = accent, corner = corner, morph = true, onClick = onClick,
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
    morph: Boolean = false,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.86f else 1f, LockMotion.fastSpatial(), label = "press")
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(size)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClickLabel = description) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) fg else fg.copy(alpha = 0.38f)
        if (morph) {
            // troca de ícone com escala+fade só onde importa (play/pause e coração)
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (fadeIn(tween(160, delayMillis = 40)) + scaleIn(tween(200, delayMillis = 40), initialScale = 0.6f)) togetherWith
                        (fadeOut(tween(80)) + scaleOut(tween(100), targetScale = 0.6f))
                },
                label = "icon",
            ) { ic ->
                Icon(ic, description, tint = tint, modifier = Modifier.size(iconSize))
            }
        } else {
            Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/**
 * Barra de progresso desenhada direto no canvas (sem recomposição nem layout por quadro).
 * Com onSeek (sessão aceita ACTION_SEEK_TO) vira interativa: toque e arraste.
 * t = 0 compacto (3 dp) → 1 expandido (6 dp; área de toque de 28 dp se canSeek).
 */
@Composable
private fun ProgressBar(
    frac: () -> Float,
    track: Color,
    fill: Color,
    t: () -> Float,
    canSeek: Boolean,
    onSeek: ((Float) -> Unit)?,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val interactive = onSeek != null
    val seekNow by rememberUpdatedState(onSeek) // evita reiniciar o gesto a cada tique do progresso
    val shown = animateFloatAsState(
        drag ?: frac(),
        if (drag != null) snap<Float>() else tween<Float>(900, easing = LinearEasing),
        label = "progress",
    )
    val dragT = animateFloatAsState(if (drag != null) 1f else 0f, tween<Float>(120), label = "dragGrow")
    Box(
        Modifier.fillMaxWidth()
            .layout { measurable, constraints ->
                val rest = 3.dp.toPx()
                val full = (if (canSeek) 28.dp else 6.dp).toPx()
                val h = mix(rest, full, t().coerceIn(0f, 1f)).roundToInt()
                val p = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                layout(p.width, p.height) { p.place(0, 0) }
            }
            .pointerInput(interactive) {
                if (interactive) detectTapGestures { o -> seekNow?.invoke((o.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(interactive) {
                if (interactive) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { drag?.let { seekNow?.invoke(it) }; drag = null },
                        onDragCancel = { drag = null },
                        onHorizontalDrag = { c, _ ->
                            c.consume()
                            drag = (c.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
            }
            .drawBehind {
                val bh = mix(3.dp.toPx(), 6.dp.toPx(), t().coerceIn(0f, 1f)) + dragT.value * 4.dp.toPx()
                val y = (size.height - bh) / 2f
                val r = CornerRadius(bh / 2f, bh / 2f)
                drawRoundRect(track, Offset(0f, y), Size(size.width, bh), r)
                val w = size.width * shown.value.coerceIn(0f, 1f)
                if (w > 0f) drawRoundRect(fill, Offset(0f, y), Size(w, bh), r)
            },
    )
}

/** Posição atual estimada (posição + tempo decorrido × velocidade). Só "tica" enquanto toca. Retorna State: quem lê decide o que recompor. */
@Composable
private fun rememberPositionMs(info: MediaInfo): State<Long> {
    val now = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(info.playing, info.updateTime, info.positionMs) {
        now.longValue = SystemClock.elapsedRealtime()
        while (info.playing && isActive) {
            delay(1000L)
            now.longValue = SystemClock.elapsedRealtime()
        }
    }
    val cur = rememberUpdatedState(info)
    return remember {
        derivedStateOf {
            val i = cur.value
            val n = now.longValue
            val p = if (i.playing && i.updateTime > 0L) {
                i.positionMs + ((n - i.updateTime) * i.speed).toLong()
            } else {
                i.positionMs
            }
            if (i.durationMs > 0L) p.coerceIn(0L, i.durationMs) else p.coerceAtLeast(0L)
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
