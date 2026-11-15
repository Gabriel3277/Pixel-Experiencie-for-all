@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.ui.text.ExperimentalTextApi::class)
package com.pixelexperience.forall.lockscreen.ui.theme

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.google.android.material.color.utilities.CorePalette
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

/** Fonte unica da UI da lockscreen (tudo, menos o relogio). */
val UiFont: FontFamily = DateFont

@Suppress("UNUSED_PARAMETER")
private fun TextStyle.g(w: Int) = copy(fontFamily = UiFont)

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
 * Esquema Material You completo a partir da cor semente (wallpaper): paletas tonais HCT
 * (CorePalette, mesmo núcleo do Dynamic Color) com papéis M3 nos tons da especificação.
 */
fun lockColorScheme(seed: Color, dark: Boolean = true): ColorScheme {
    val p = CorePalette.of(seed.toArgb())
    fun a1(t: Int) = Color(p.a1.tone(t))
    fun a2(t: Int) = Color(p.a2.tone(t))
    fun a3(t: Int) = Color(p.a3.tone(t))
    fun n1(t: Int) = Color(p.n1.tone(t))
    fun n2(t: Int) = Color(p.n2.tone(t))
    fun er(t: Int) = Color(p.error.tone(t))
    if (!dark) {
        return lightColorScheme(
            primary = a1(40), onPrimary = a1(100), primaryContainer = a1(90), onPrimaryContainer = a1(10),
            inversePrimary = a1(80),
            secondary = a2(40), onSecondary = a2(100), secondaryContainer = a2(90), onSecondaryContainer = a2(10),
            tertiary = a3(40), onTertiary = a3(100), tertiaryContainer = a3(90), onTertiaryContainer = a3(10),
            background = n1(98), onBackground = n1(10),
            surface = n1(98), onSurface = n1(10),
            surfaceVariant = n2(90), onSurfaceVariant = n2(30),
            inverseSurface = n1(20), inverseOnSurface = n1(95),
            outline = n2(50), outlineVariant = n2(80),
            error = er(40), onError = er(100), errorContainer = er(90), onErrorContainer = er(10),
            surfaceDim = n1(87), surfaceBright = n1(98),
            surfaceContainerLowest = n1(100), surfaceContainerLow = n1(96),
            surfaceContainer = n1(94), surfaceContainerHigh = n1(92), surfaceContainerHighest = n1(90),
        )
    }
    return darkColorScheme(
        primary = a1(80), onPrimary = a1(20), primaryContainer = a1(30), onPrimaryContainer = a1(90),
        inversePrimary = a1(40),
        secondary = a2(80), onSecondary = a2(20), secondaryContainer = a2(30), onSecondaryContainer = a2(90),
        tertiary = a3(80), onTertiary = a3(20), tertiaryContainer = a3(30), onTertiaryContainer = a3(90),
        background = n1(6), onBackground = n1(90),
        surface = n1(6), onSurface = n1(90),
        surfaceVariant = n2(30), onSurfaceVariant = n2(80),
        inverseSurface = n1(90), inverseOnSurface = n1(20),
        outline = n2(60), outlineVariant = n2(30),
        error = er(80), onError = er(20), errorContainer = er(30), onErrorContainer = er(90),
        surfaceDim = n1(6), surfaceBright = n1(24),
        surfaceContainerLowest = n1(4), surfaceContainerLow = n1(10),
        surfaceContainer = n1(12), surfaceContainerHigh = n1(17), surfaceContainerHighest = n1(22),
    )
}

/** seed != null: wallpaper próprio do app. seed == null: Dynamic Color do Android. */
@Composable
fun LockTheme(seed: Color? = null, dark: Boolean = true, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (seed != null) remember(seed, dark) { lockColorScheme(seed, dark) }
    else if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialExpressiveTheme(
        colorScheme = scheme,
        shapes = LockShapes,
        typography = remember { lockTypography() },
        content = content,
    )
}
