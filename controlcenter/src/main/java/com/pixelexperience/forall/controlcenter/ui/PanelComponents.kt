package com.pixelexperience.forall.controlcenter.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixelexperience.forall.controlcenter.PanelActions
import com.pixelexperience.forall.controlcenter.PanelState
import com.pixelexperience.forall.controlcenter.R
import kotlinx.coroutines.delay
import kotlin.math.min

// Componentes do painel da Central de controle (sliders, tiles e botões animados).
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
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Base de todo botão que liga e desliga: a forma morfa de círculo para
 * quadrado arredondado com overshoot elástico, a cor faz cross-fade,
 * o ícone dá um "pulinho" e o botão encolhe um pouco ao ser pressionado.
 *
 * Se [onClick] for null, o botão não é clicável (quem o contém cuida do clique).
 */
@Composable
private fun ToggleBox(
    modifier: Modifier,
    active: Boolean,
    colors: PanelColors,
    inactiveRadius: Dp,
    activeRadius: Dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.(foreground: Color, pop: Modifier) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press",
    )
    val container by animateColorAsState(
        targetValue = if (active) colors.tileOn else colors.tileOff,
        animationSpec = tween(300),
        label = "container",
    )
    val foreground by animateColorAsState(
        targetValue = if (active) colors.onTileOn else colors.onTileOff,
        animationSpec = tween(300),
        label = "foreground",
    )
    val radius by animateDpAsState(
        targetValue = if (active) activeRadius else inactiveRadius,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "radius",
    )

    // Pulinho do ícone: só quando o estado muda (não na primeira composição).
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(active) {
        if (first) {
            first = false
        } else {
            pop.snapTo(0f)
            pop.animateTo(
                1f,
                spring(dampingRatio = 0.45f, stiffness = 300f),
            )
        }
    }
    val popModifier = Modifier.graphicsLayer {
        val p = pop.value
        scaleX = 0.5f + 0.5f * p
        scaleY = 0.5f + 0.5f * p
        rotationZ = -25f * (1f - p)
        alpha = (0.3f + 0.7f * p).coerceIn(0f, 1f)
    }

    var boxModifier = modifier
        .graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
        .clip(RoundedCornerShape(radius.coerceAtLeast(0.dp)))
        .background(container)

    if (onClick != null) {
        boxModifier = boxModifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    }

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center,
    ) {
        content(foreground, popModifier)
    }
}

@Composable
internal fun CircleButton(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    activeRadius: Dp,
    colors: PanelColors,
    onClick: () -> Unit,
) {
    ToggleBox(
        modifier = Modifier.size(56.dp),
        active = active,
        colors = colors,
        inactiveRadius = 28.dp,
        activeRadius = activeRadius,
        onClick = onClick,
    ) { fg, pop ->
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = fg,
            modifier = Modifier
                .size(24.dp)
                .then(pop),
        )
    }
}

/**
 * Slider em pílula (Android 17): preenchimento verde-claro, um espaço, uma alça
 * vertical fina e o resto da trilha escuro. A alça afina e cresce enquanto
 * o dedo está na barra.
 */
@Composable
internal fun PillSlider(
    modifier: Modifier,
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
    var tapPressed by remember { mutableStateOf(false) }
    var dragPressed by remember { mutableStateOf(false) }
    val pressed = tapPressed || dragPressed

    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(160),
        label = "sliderPress",
    )
    // Segue o dedo na hora; quando o valor muda por fora, desliza suave.
    val shown by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = if (pressed) snap() else tween(250),
        label = "sliderValue",
    )
    val handleColor = lerp(fillColor, Color.White, 0.55f)

    Box(
        modifier = modifier
            .height(56.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            tapPressed = true
                            tryAwaitRelease()
                            tapPressed = false
                        },
                        onTap = { offset ->
                            onValueChange((offset.x / widthPx).coerceIn(0f, 1f))
                        },
                    )
                } else {
                    detectTapGestures { onDisabledClick() }
                }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragPressed = true
                            onValueChange((offset.x / widthPx).coerceIn(0f, 1f))
                        },
                        onDragEnd = { dragPressed = false },
                        onDragCancel = { dragPressed = false },
                    ) { change, _ ->
                        change.consume()
                        onValueChange((change.position.x / widthPx).coerceIn(0f, 1f))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (enabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val gap = 8.dp.toPx()

                val handleW = 5.dp.toPx() - 1.5f.dp.toPx() * press
                val handleTop = 4.dp.toPx() - 2.dp.toPx() * press
                val edge = 2.dp.toPx() + handleW / 2f
                val x = (shown * w).coerceIn(edge, w - edge)

                val fillRight = x - gap
                if (fillRight > 6.dp.toPx()) {
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset.Zero,
                        size = Size(fillRight, h),
                        cornerRadius = CornerRadius(min(h / 2f, fillRight / 2f)),
                    )
                }

                val restLeft = x + gap
                val restWidth = w - restLeft
                if (restWidth > 6.dp.toPx()) {
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(restLeft, 0f),
                        size = Size(restWidth, h),
                        cornerRadius = CornerRadius(min(h / 2f, restWidth / 2f)),
                    )
                }

                drawRoundRect(
                    color = handleColor,
                    topLeft = Offset(x - handleW / 2f, handleTop),
                    size = Size(handleW, h - 2f * handleTop),
                    cornerRadius = CornerRadius(handleW / 2f),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(trackColor),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = disabledHint,
                    color = hintColor,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

/**
 * Tile largo: o fundo fica sempre escuro e só o quadradinho do ícone
 * liga (verde-claro, cantos arredondados) e desliga (círculo escuro).
 */
@Composable
internal fun WideTile(
    modifier: Modifier,
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    active: Boolean,
    colors: PanelColors,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "widePress",
    )

    Row(
        modifier = modifier
            .height(76.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(38.dp))
            .background(colors.tileOff)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToggleBox(
            modifier = Modifier.size(52.dp),
            active = active,
            colors = colors,
            inactiveRadius = 26.dp,
            activeRadius = 18.dp,
            onClick = null,
        ) { fg, pop ->
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = fg,
                modifier = Modifier
                    .size(24.dp)
                    .then(pop),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.onTileOff,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = colors.onTileOff.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SmallTile(
    modifier: Modifier,
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    colors: PanelColors,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ToggleBox(
        modifier = modifier.height(64.dp),
        active = active,
        colors = colors,
        inactiveRadius = 32.dp,
        activeRadius = 22.dp,
        enabled = enabled,
        onClick = onClick,
    ) { fg, pop ->
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = fg.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier
                .size(26.dp)
                .then(pop),
        )
    }
}
