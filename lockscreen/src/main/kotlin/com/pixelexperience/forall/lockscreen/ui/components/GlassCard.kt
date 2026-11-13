package com.pixelexperience.forall.lockscreen.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.pixelexperience.forall.lockscreen.ui.theme.LockMotion
import kotlin.math.max
import kotlin.math.roundToInt

/** Miniatura borrada do papel de parede + geometria da tela, usada para o "vidro" dos cards. */
class Backdrop(val image: ImageBitmap) {
    var rootSize by mutableStateOf(Size.Zero)
    var origin by mutableStateOf(Offset.Zero)
    var coordScale by mutableStateOf(1f)
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Card translúcido: desenha a fatia do papel de parede borrado que está atrás dele + tinta com opacidade. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color,
    opacity: Float,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .clip(shape)
            .onGloballyPositioned { pos = it.positionInRoot() }
            .drawBehind {
                val bd = backdrop
                if (bd != null && bd.rootSize.width > 0f) {
                    val bw = bd.image.width.toFloat()
                    val bh = bd.image.height.toFloat()
                    val sc = max(bd.rootSize.width / bw, bd.rootSize.height / bh)
                    val ox = (bd.rootSize.width - bw * sc) / 2f
                    val oy = (bd.rootSize.height - bh * sc) / 2f
                    val local = (pos - bd.origin) / bd.coordScale
                    withTransform({
                        translate(ox - local.x, oy - local.y)
                        scale(sc, sc, Offset.Zero)
                    }) {
                        drawImage(bd.image, filterQuality = FilterQuality.Medium)
                    }
                }
                drawRect(tint.copy(alpha = opacity.coerceIn(0f, 1f)))
            }
            .border(0.7.dp, Color.White.copy(alpha = 0.10f), shape),
        content = content,
    )
}

/** Botão circular estilo Pixel: mola ao pressionar, canto que "morfa" e haptic. */
@Composable
fun ShortcutButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    accent: Color,
    onAccent: Color,
    tint: Color,
    opacity: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press")
    val corner by animateFloatAsState(if (pressed) 18f else if (active) 30f else 50f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "corner")
    val haptic = LocalHapticFeedback.current
    GlassCard(
        modifier = modifier
            .size(60.dp)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clickable(interactionSource = src, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        shape = RoundedCornerShape(percent = corner.roundToInt().coerceIn(0, 50)),
        tint = tint,
        opacity = if (active) 0f else opacity,
    ) {
        if (active) Box(Modifier.fillMaxSize().background(accent))
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) onAccent else Color.White,
            modifier = Modifier.align(Alignment.Center).size(26.dp),
        )
    }
}
