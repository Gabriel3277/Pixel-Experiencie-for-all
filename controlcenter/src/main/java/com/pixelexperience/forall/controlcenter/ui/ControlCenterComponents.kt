package com.pixelexperience.forall.controlcenter.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Clique com efeito elástico (encolhe ao pressionar). */
@Composable
private fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Raio do canto que morfa de círculo (inativo) para quadrado arredondado (ativo). */
@Composable
private fun morphRadius(active: Boolean, activeRadius: Dp, circleRadius: Dp): Dp {
    val r by animateDpAsState(
        targetValue = if (active) activeRadius else circleRadius,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "morph",
    )
    return r.coerceAtLeast(0.dp)
}

@Composable
internal fun HeaderButton(
    @DrawableRes icon: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .bounceClick(onClick = onClick)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Slider em pílula com alça. [value] vai de 0f a 1f. */
@Composable
internal fun PillSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    disabledHint: String,
    onDisabledClick: () -> Unit,
    trackColor: Color,
    fillColor: Color,
    hintColor: Color,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val onChange by rememberUpdatedState(onValueChange)
    val onDisabled by rememberUpdatedState(onDisabledClick)
    val frac = value.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(trackColor)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                detectTapGestures { offset ->
                    if (enabled) onChange((offset.x / widthPx).coerceIn(0f, 1f))
                    else onDisabled()
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onChange((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val fillW = size.width * frac
            // preenchimento
            drawRect(color = fillColor, size = Size(fillW, size.height))
            // alça
            val handleW = 6.dp.toPx()
            val handleH = 28.dp.toPx()
            val x = (fillW - 14.dp.toPx()).coerceAtLeast(10.dp.toPx())
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(x, (size.height - handleH) / 2f),
                size = Size(handleW, handleH),
                cornerRadius = CornerRadius(handleW / 2f),
            )
        }
        if (!enabled && disabledHint.isNotEmpty()) {
            Text(
                text = disabledHint,
                color = hintColor,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** Botão redondo que morfa para quadrado arredondado quando [active]. */
@Composable
internal fun CircleButton(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    activeRadius: Dp,
    colors: PanelColors,
    onClick: () -> Unit,
) {
    val radius = morphRadius(active, activeRadius, 28.dp)
    val bg by androidx.compose.animation.animateColorAsState(
        if (active) colors.tileOn else colors.tileOff, label = "bg",
    )
    Box(
        modifier = Modifier
            .size(56.dp)
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (active) colors.onTileOn else colors.onTileOff,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Tile largo: quadradinho do ícone (morfa ao ligar) + título e subtítulo. */
@Composable
internal fun WideTile(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    active: Boolean,
    colors: PanelColors,
    onClick: () -> Unit,
) {
    val radius = morphRadius(active, 16.dp, 24.dp)
    val iconBg by androidx.compose.animation.animateColorAsState(
        if (active) colors.tileOn else colors.panel, label = "iconBg",
    )
    Row(
        modifier = modifier
            .height(72.dp)
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(36.dp))
            .background(colors.tileOff)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(radius))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = title,
                tint = if (active) colors.onTileOn else colors.onTileOff,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                color = colors.onTileOff,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = colors.onTileOff.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Tile pequeno só com ícone; morfa de pílula para quadrado arredondado ao ligar. */
@Composable
internal fun SmallTile(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    colors: PanelColors,
    onClick: () -> Unit,
) {
    val radius = morphRadius(active, 20.dp, 36.dp)
    val bg by androidx.compose.animation.animateColorAsState(
        if (active) colors.tileOn else colors.tileOff, label = "bg",
    )
    Box(
        modifier = modifier
            .height(72.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .bounceClick(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(radius))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = if (active) colors.onTileOn else colors.onTileOff,
            modifier = Modifier.size(24.dp),
        )
    }
}
