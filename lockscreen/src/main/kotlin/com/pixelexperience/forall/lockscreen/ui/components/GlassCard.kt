package com.pixelexperience.forall.lockscreen.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.ripple
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
    shape: Shape = RoundedCornerShape(32.dp),
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
                // realce tonal no topo: dá volume de superfície elevada (M3)
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.07f * opacity.coerceIn(0f, 1f) * 2f),
                        0.5f to Color.Transparent,
                    )
                )
            }
            .border(0.8.dp, Color.White.copy(alpha = 0.12f), shape),
        content = content,
    )
}

/**
 * Atalho inferior estilo Pixel (60dp) com M3 Expressive em todos os estados:
 * repouso = círculo translúcido; pressionado = forma "morfa" para squircle + escala por mola + ripple recortado;
 * ativo (ex.: lanterna ligada) = squircle preenchido com a cor primária dinâmica e ícone com mola.
 */
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
    iconColor: Color = Color.White,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.88f else 1f, LockMotion.fastSpatial(), label = "press")
    val corner by animateFloatAsState(if (pressed) 28f else if (active) 38f else 50f, LockMotion.fastSpatial(), label = "corner")
    val fill by animateColorAsState(if (active) accent else Color.Transparent, LockMotion.effects(), label = "fill")
    val iconTint by animateColorAsState(if (active) onAccent else iconColor, LockMotion.effects(), label = "iconTint")
    val iconScale by animateFloatAsState(if (active) 1.12f else 1f, LockMotion.bouncy(), label = "iconScale")
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(percent = corner.roundToInt().coerceIn(0, 50))
    GlassCard(
        modifier = modifier.size(64.dp).graphicsLayer { scaleX = sc; scaleY = sc }.shadow(8.dp, shape, clip = false),
        shape = shape,
        tint = tint,
        opacity = opacity,
    ) {
        Box(
            Modifier.fillMaxSize().background(fill).clickable(
                interactionSource = src, indication = ripple(color = Color.White), role = Role.Button, onClickLabel = description,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = iconTint,
                modifier = Modifier.size(30.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            )
        }
    }
}
