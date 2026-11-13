@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.pixelexperience.forall.lockscreen.ui.theme

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Física "expressiva": molas com leve overshoot nos movimentos espaciais, amortecidas nos efeitos. */
object LockMotion {
    fun <T> spatial() = spring<T>(dampingRatio = 0.72f, stiffness = 380f)
    fun <T> bouncy() = spring<T>(dampingRatio = 0.55f, stiffness = 500f)
    fun <T> effects() = spring<T>(dampingRatio = 1f, stiffness = 1400f)
}

val LockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Usa "google-sans" quando o aparelho tiver (Pixel); em outros aparelhos o sistema cai na fonte padrão. */
@OptIn(ExperimentalTextApi::class)
fun lockFont(weight: Int): FontFamily =
    FontFamily(Font(DeviceFontFamilyName("google-sans"), FontWeight(weight)))

@Composable
fun LockTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = dynamicDarkColorScheme(LocalContext.current),
        shapes = LockShapes,
        content = content,
    )
}
