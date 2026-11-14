@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
package com.pixelexperience.forall.lockscreen.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.pixelexperience.forall.lockscreen.R
import java.util.concurrent.ConcurrentHashMap

/** Física do Material 3 Expressive: molas espaciais (com overshoot) para movimento, amortecidas para efeitos. */
object LockMotion {
    fun <T> spatial() = spring<T>(dampingRatio = 0.75f, stiffness = 380f)      // padrão
    fun <T> fastSpatial() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)   // toques, morph de forma
    fun <T> slowSpatial() = spring<T>(dampingRatio = 0.8f, stiffness = 200f)   // entrada de blocos grandes
    fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = 500f)
    fun <T> effects() = spring<T>(dampingRatio = 1f, stiffness = 1600f)         // cor / alpha: sem overshoot
}

val LockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val fontCache = ConcurrentHashMap<String, FontFamily>()

/**
 * Google Sans Flex embutida (fonte variável): funciona igual em qualquer aparelho, não só no Pixel.
 * Eixos: wght (peso), wdth (largura), ROND (arredondamento — 100 = estilo do relógio Pixel).
 */
fun lockFont(weight: Int, width: Float = 100f, round: Float = 0f): FontFamily {
    val w = weight.coerceIn(1, 1000)
    return fontCache.getOrPut("$w/$width/$round") {
        FontFamily(
            Font(
                R.font.google_sans_flex, FontWeight(w),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(w), FontVariation.width(width), FontVariation.Setting("ROND", round),
                ),
            )
        )
    }
}

/** Quicksand Bold: fonte da data e do clima na tela de bloqueio. */
val DateFont = FontFamily(Font(R.font.quicksand_bold, FontWeight.Bold))

private fun TextStyle.g(w: Int) = copy(fontFamily = lockFont(w))

/** Hierarquia tipográfica única (Google Sans Flex) para todo o módulo. */
private fun lockTypography(): Typography {
    val b = Typography()
    return b.copy(
        displayLarge = b.displayLarge.g(500), displayMedium = b.displayMedium.g(500), displaySmall = b.displaySmall.g(500),
        headlineLarge = b.headlineLarge.g(500), headlineMedium = b.headlineMedium.g(500), headlineSmall = b.headlineSmall.g(500),
        titleLarge = b.titleLarge.g(500), titleMedium = b.titleMedium.g(600), titleSmall = b.titleSmall.g(600),
        bodyLarge = b.bodyLarge.g(400), bodyMedium = b.bodyMedium.g(400), bodySmall = b.bodySmall.g(400),
        labelLarge = b.labelLarge.g(600), labelMedium = b.labelMedium.g(600), labelSmall = b.labelSmall.g(600),
    )
}

/**
 * Esquema escuro tonal gerado a partir da cor do wallpaper (mesma ideia do Dynamic Color:
 * uma matiz, papéis primary/secondary/tertiary em tons fixos). Funciona também com wallpaper personalizado.
 */
fun lockColorScheme(seed: Color): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val h = hsl[0]
    val s = hsl[1].coerceIn(0.35f, 0.80f)
    fun t(sat: Float, l: Float, dh: Float = 0f) =
        Color(ColorUtils.HSLToColor(floatArrayOf((h + dh + 360f) % 360f, sat.coerceIn(0f, 1f), l)))
    val n = s * 0.22f   // neutros
    val v = s * 0.38f   // neutros-variantes / secundária
    val a = s * 0.75f   // terciária
    return darkColorScheme(
        primary = t(s, .80f), onPrimary = t(s, .14f),
        primaryContainer = t(s, .27f), onPrimaryContainer = t(s, .90f),
        secondary = t(v, .80f), onSecondary = t(v, .15f),
        secondaryContainer = t(v, .28f), onSecondaryContainer = t(v, .90f),
        tertiary = t(a, .80f, 60f), onTertiary = t(a, .14f, 60f),
        tertiaryContainer = t(a, .27f, 60f), onTertiaryContainer = t(a, .90f, 60f),
        background = t(n, .06f), onBackground = t(n, .92f),
        surface = t(n, .08f), onSurface = t(n, .92f),
        surfaceVariant = t(v, .26f), onSurfaceVariant = t(v, .80f),
        outline = t(v, .56f), outlineVariant = t(v, .30f),
        surfaceContainerLowest = t(n, .05f), surfaceContainerLow = t(n, .10f),
        surfaceContainer = t(n, .12f), surfaceContainerHigh = t(n, .16f),
        surfaceContainerHighest = t(n, .20f),
    )
}

@Composable
fun LockTheme(seed: Color? = null, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (seed != null) remember(seed) { lockColorScheme(seed) } else dynamicDarkColorScheme(ctx)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        shapes = LockShapes,
        typography = remember { lockTypography() },
        content = content,
    )
}
