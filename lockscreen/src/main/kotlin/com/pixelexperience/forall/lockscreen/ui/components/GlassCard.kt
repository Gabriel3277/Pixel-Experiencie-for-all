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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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

/**
 * Vidro real: desenha a miniatura borrada do wallpaper (Backdrop) alinhada à posição do card na tela,
 * com tint tonal por cima e um fio de luz na borda. Sem Backdrop (ex.: ainda carregando) cai para cor sólida.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(32.dp),
    tint: Color,
    opacity: Float,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val a = opacity.coerceIn(0f, 1f)
    val glass = if (backdrop == null) {
        modifier.clip(shape).background(tint.copy(alpha = a), shape)
    } else {
        modifier
            .clip(shape)
            .onGloballyPositioned { coords[0] = it }
            .drawBehind {
                val c = coords[0]?.takeIf { it.isAttached } ?: return@drawBehind
                val rs = backdrop.rootSize
                if (rs.width < 1f || rs.height < 1f) return@drawBehind
                val img = backdrop.image
                val p = (c.positionInRoot() - backdrop.origin) / backdrop.coordScale
                // mesma geometria do wallpaper da tela (Crop + escala 1.06 no centro)
                val s = max(rs.width / img.width, rs.height / img.height) * 1.06f
                val dw = img.width * s
                val dh = img.height * s
                val ox = (rs.width - dw) / 2f - p.x
                val oy = (rs.height - dh) / 2f - p.y
                drawImage(
                    img,
                    dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
            .background(tint.copy(alpha = a), shape)
            .border(0.6.dp, Color.White.copy(alpha = 0.10f), shape)
    }
    Box(glass, content = content)
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
    val corner by animateFloatAsState(if (pressed) 18f else if (active) 24f else 32f, LockMotion.fastSpatial(), label = "corner")
    val fill by animateColorAsState(if (active) accent else accent.copy(alpha = 0f), LockMotion.effects(), label = "fill")
    val iconTint by animateColorAsState(if (active) onAccent else iconColor, LockMotion.effects(), label = "iconTint")
    val iconScale by animateFloatAsState(if (active) 1.12f else 1f, LockMotion.bouncy(), label = "iconScale")
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(corner.coerceAtLeast(0f).dp)
    GlassCard(
        modifier = modifier.size(64.dp).graphicsLayer { scaleX = sc; scaleY = sc }.shadow(4.dp, shape, clip = false),
        shape = shape,
        tint = tint,
        opacity = opacity,
    ) {
        Box(
            Modifier.fillMaxSize().background(fill).clickable(
                interactionSource = src, indication = ripple(color = iconTint), role = Role.Button, onClickLabel = description,
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
                modifier = Modifier.size(28.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            )
        }
    }
}
