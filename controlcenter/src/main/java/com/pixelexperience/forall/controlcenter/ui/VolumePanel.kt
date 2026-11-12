package com.pixelexperience.forall.controlcenter.ui

import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixelexperience.forall.controlcenter.R
import com.pixelexperience.forall.controlcenter.VolStream
import com.pixelexperience.forall.controlcenter.VolumeState
import kotlinx.coroutines.delay

/** Estado visual do painel de volume (quem mostra/esconde). */
internal class VolumeUi {
    var visible by mutableStateOf(false)
    var expanded by mutableStateOf(false)
    var pickerOpen by mutableStateOf(false)
    var tick by mutableIntStateOf(0)

    fun poke() {
        tick++
    }

    fun reset() {
        visible = false
        expanded = false
        pickerOpen = false
    }
}

internal class VolumeCallbacks(
    val onExpandedLayout: (Boolean) -> Unit,
    val onGone: () -> Unit,
    val onNeedPolicy: () -> Unit,
    val onOpenSound: () -> Unit,
    val onOpenCaptions: () -> Unit,
)

private class VolColors(
    val sheet: Color,
    val row: Color,
    val chip: Color,
    val pillBg: Color,
    val fill: Color,
    val fillDark: Color,
    val onFill: Color,
    val mint: Color,
    val onMint: Color,
    val track: Color,
    val text: Color,
    val textSecondary: Color,
    val icon: Color,
    val handle: Color,
)

@Composable
private fun rememberVolColors(): VolColors {
    val p = rememberPanelColors()
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return remember(p, cs, dark) {
        VolColors(
            sheet = p.panel,
            row = if (dark) lerp(p.panel, Color.White, 0.08f) else lerp(p.panel, Color.White, 0.4f),
            chip = if (dark) lerp(p.panel, Color.White, 0.14f) else lerp(Color.White, cs.primary, 0.06f),
            pillBg = if (dark) lerp(p.panel, Color.White, 0.12f) else cs.primaryContainer,
            fill = p.tileOn,
            fillDark = lerp(p.tileOn, Color.Black, 0.35f),
            onFill = p.onTileOn,
            mint = cs.primaryContainer,
            onMint = cs.onPrimaryContainer,
            track = if (dark) lerp(p.panel, Color.White, 0.1f) else lerp(Color.White, cs.primary, 0.05f),
            text = p.onPanel,
            textSecondary = p.onPanel.copy(alpha = 0.7f),
            icon = p.onTileOff,
            handle = if (dark) cs.onSurface else lerp(Color.Black, cs.primary, 0.5f),
        )
    }
}

private val bouncy = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)

@Composable
internal fun VolumePanel(
    state: VolumeState,
    ui: VolumeUi,
    topInset: Dp,
    cb: VolumeCallbacks,
) {
    val c = rememberVolColors()
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appeared = true
        while (true) {
            state.refresh()
            delay(350)
        }
    }
    // Some sozinho depois de uns segundos (a não ser que esteja expandido).
    LaunchedEffect(ui.tick, ui.expanded) {
        if (!ui.expanded) {
            delay(3500)
            ui.pickerOpen = false
            ui.visible = false
        }
    }
    LaunchedEffect(ui.expanded) {
        if (ui.expanded) {
            cb.onExpandedLayout(true)
        } else {
            delay(350)
            cb.onExpandedLayout(false)
        }
    }
    LaunchedEffect(ui.visible) {
        if (!ui.visible) {
            delay(500)
            if (!ui.visible) cb.onGone()
        }
    }

    val selectMode: (Int) -> Unit = { mode ->
        if (!state.setRingerMode(mode) && mode == AudioManager.RINGER_MODE_SILENT) cb.onNeedPolicy()
        ui.poke()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = ui.visible && appeared && !ui.expanded,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 8.dp),
            enter = slideInHorizontally(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { it } + fadeIn(),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(220)),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ModePicker(state, ui, c, selectMode)
                VerticalCapsule(
                    value = state.level(VolStream.MEDIA),
                    c = c,
                    onChange = {
                        state.setLevel(VolStream.MEDIA, it)
                        ui.poke()
                    },
                    onIconClick = { ui.expanded = true },
                )
            }
        }

        if (ui.expanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { ui.expanded = false; ui.poke() },
            )
        }

        AnimatedVisibility(
            visible = ui.visible && appeared && ui.expanded,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = scaleIn(bouncy, initialScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 0f)) +
                fadeIn(tween(180)),
            exit = scaleOut(tween(200), targetScale = 0.95f, transformOrigin = TransformOrigin(0.5f, 0f)) +
                fadeOut(tween(160)),
        ) {
            Sheet(state, ui, c, cb, topInset, selectMode)
        }
    }
}

// ----------------------------------------------------------------------
// Barra lateral
// ----------------------------------------------------------------------

private val modes = listOf(
    Triple(AudioManager.RINGER_MODE_SILENT, R.drawable.cc_vp_mute, R.string.cc_vp_mode_silent),
    Triple(AudioManager.RINGER_MODE_VIBRATE, R.drawable.cc_vp_vibrate, R.string.cc_vp_mode_vibrate),
    Triple(AudioManager.RINGER_MODE_NORMAL, R.drawable.cc_vp_bell, R.string.cc_vp_mode_ring),
)

@Composable
private fun ModePicker(
    state: VolumeState,
    ui: VolumeUi,
    c: VolColors,
    onSelect: (Int) -> Unit,
) {
    val open = ui.pickerOpen
    val bg by animateColorAsState(if (open) c.pillBg else Color.Transparent, label = "pickerBg")
    val pad by animateDpAsState(
        if (open) 3.dp else 0.dp,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "pickerPad",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(pad),
    ) {
        for ((mode, icon, label) in modes) {
            val selected = state.ringerMode == mode
            AnimatedVisibility(
                visible = open || selected,
                enter = expandHorizontally(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkHorizontally(tween(200)) + fadeOut(tween(150)),
            ) {
                ModeButton(
                    icon = icon,
                    description = stringResource(label),
                    selected = selected,
                    c = c,
                    modifier = Modifier.padding(horizontal = 3.dp),
                    onClick = {
                        if (!open) {
                            ui.pickerOpen = true
                            ui.poke()
                        } else {
                            onSelect(mode)
                            ui.pickerOpen = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    icon: Int,
    description: String,
    selected: Boolean,
    c: VolColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val radius by animateDpAsState(
        if (selected) 19.dp else 12.dp,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "modeRadius",
    )
    val bg by animateColorAsState(if (selected) c.fill else c.chip, label = "modeBg")
    Box(
        modifier = modifier
            .size(38.dp)
            .pressScale(onClick)
            .clip(RoundedCornerShape(radius.coerceAtLeast(0.dp)))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (selected) c.onFill else c.icon,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun VerticalCapsule(
    value: Float,
    c: VolColors,
    onChange: (Float) -> Unit,
    onIconClick: () -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val onChangeNow by rememberUpdatedState(onChange)
    val shown by animateFloatAsState(
        targetValue = drag ?: value,
        animationSpec = if (drag != null) snap<Float>() else spring<Float>(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium),
        label = "capsule",
    )
    Box(
        Modifier
            .size(width = 54.dp, height = 210.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(c.mint)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(c.track)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var f = (1f - down.position.y / size.height).coerceIn(0f, 1f)
                        drag = f
                        onChangeNow(f)
                        do {
                            val ch = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (ch.pressed) {
                                f = (1f - ch.position.y / size.height).coerceIn(0f, 1f)
                                drag = f
                                onChangeNow(f)
                                ch.consume()
                            }
                        } while (ch.pressed)
                        drag = null
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(shown.coerceIn(0f, 1f))
                    .background(c.fill),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(c.fillDark),
                )
            }
            Icon(
                painter = painterResource(R.drawable.cc_vp_music),
                contentDescription = null,
                tint = c.handle,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .size(28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onIconClick,
                    )
                    .padding(4.dp),
            )
        }
    }
}

// ----------------------------------------------------------------------
// Painel expandido
// ----------------------------------------------------------------------

private val streamRows = listOf(
    Triple(VolStream.MEDIA, R.string.cc_vp_media, R.drawable.cc_vp_music),
    Triple(VolStream.CALL, R.string.cc_vp_call, R.drawable.cc_vp_phone),
    Triple(VolStream.RING, R.string.cc_vp_ring, R.drawable.cc_vp_bell),
    Triple(VolStream.NOTIFICATION, R.string.cc_vp_notification, R.drawable.cc_vp_bell),
    Triple(VolStream.ALARM, R.string.cc_vp_alarm, R.drawable.cc_ic_alarm),
)

@Composable
private fun Sheet(
    state: VolumeState,
    ui: VolumeUi,
    c: VolColors,
    cb: VolumeCallbacks,
    topInset: Dp,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = topInset + 8.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(c.sheet)
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.chip)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(R.string.cc_vp_plays_on),
                    color = c.textSecondary,
                    fontSize = 10.sp,
                )
                Text(
                    text = state.outputName ?: stringResource(R.string.cc_vp_this_phone),
                    color = c.text,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            for ((mode, icon, label) in modes) {
                ModeButton(
                    icon = icon,
                    description = stringResource(label),
                    selected = state.ringerMode == mode,
                    c = c,
                    onClick = { onSelect(mode) },
                )
            }
        }

        for ((stream, label, icon) in streamRows) {
            StreamRow(
                label = stringResource(label),
                icon = icon,
                value = state.level(stream),
                c = c,
                onChange = {
                    state.setLevel(stream, it)
                    ui.poke()
                },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .pressScale(cb.onOpenSound)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.chip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.cc_ic_settings),
                    contentDescription = stringResource(R.string.cc_vp_settings),
                    tint = c.icon,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .pressScale(cb.onOpenCaptions)
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.chip),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.cc_vp_captions),
                    color = c.icon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Box(
                Modifier
                    .weight(1.1f)
                    .height(38.dp)
                    .pressScale { ui.expanded = false; ui.poke() }
                    .clip(RoundedCornerShape(13.dp))
                    .background(c.mint),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.cc_vp_done),
                    color = c.onMint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun StreamRow(
    label: String,
    icon: Int,
    value: Float,
    c: VolColors,
    onChange: (Float) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.row)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = c.text, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = c.icon,
                modifier = Modifier.size(18.dp),
            )
        }
        HorizontalSlider(value, c, onChange)
    }
}

@Composable
private fun HorizontalSlider(value: Float, c: VolColors, onChange: (Float) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val onChangeNow by rememberUpdatedState(onChange)
    val shown by animateFloatAsState(
        targetValue = drag ?: value,
        animationSpec = if (drag != null) snap<Float>() else spring<Float>(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium),
        label = "hslider",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var f = (down.position.x / size.width).coerceIn(0f, 1f)
                    drag = f
                    onChangeNow(f)
                    do {
                        val ch = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (ch.pressed) {
                            f = (ch.position.x / size.width).coerceIn(0f, 1f)
                            drag = f
                            onChangeNow(f)
                            ch.consume()
                        }
                    } while (ch.pressed)
                    drag = null
                }
            },
    ) {
        val trackH = 14.dp.toPx()
        val top = (size.height - trackH) / 2f
        val gap = 7.dp.toPx()
        val r = CornerRadius(7.dp.toPx())
        val x = size.width * shown.coerceIn(0f, 1f)
        val fillW = (x - gap).coerceAtLeast(0f)
        if (fillW > 0f) drawRoundRect(c.fill, Offset(0f, top), Size(fillW, trackH), r)
        val remLeft = x + gap
        if (size.width - remLeft > 0f) {
            drawRoundRect(c.mint, Offset(remLeft, top), Size(size.width - remLeft, trackH), r)
        }
        val hw = 3.dp.toPx()
        drawRoundRect(c.handle, Offset(x - hw / 2f, 0f), Size(hw, size.height), CornerRadius(hw / 2f))
    }
}

/** Clique com efeito elástico: encolhe ao pressionar e volta com mola. */
@Composable
private fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}
