package com.pixelexperience.forall.themes

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Tema do Pixel Experience (Material 3 Expressive).
 *
 * @param dynamicColor true usa as cores do sistema (Material You, Android 12+);
 * false usa a paleta Pixel fixa.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> PixelDarkColors
        else -> PixelLightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        shapes = PixelShapes,
        typography = PixelTypography,
        content = content,
    )
}
