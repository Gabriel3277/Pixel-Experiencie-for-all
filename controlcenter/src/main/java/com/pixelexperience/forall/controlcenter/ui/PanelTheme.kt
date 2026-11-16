package com.pixelexperience.forall.controlcenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pixelexperience.forall.controlcenter.R

/** Quicksand Bold: mesma fonte da UI da lockscreen (cópia local do arquivo). */
internal val CcFont = FontFamily(Font(R.font.cc_quicksand_bold, FontWeight.Bold))

/**
 * Aplica a fonte da lockscreen em todo o painel mantendo as cores e formas do tema atual.
 * Textos sem estilo explícito herdam bodyLarge, então também ficam em Quicksand.
 */
@Composable
internal fun PanelTypography(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val typography = remember(base) {
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = CcFont),
            displayMedium = base.displayMedium.copy(fontFamily = CcFont),
            displaySmall = base.displaySmall.copy(fontFamily = CcFont),
            headlineLarge = base.headlineLarge.copy(fontFamily = CcFont),
            headlineMedium = base.headlineMedium.copy(fontFamily = CcFont),
            headlineSmall = base.headlineSmall.copy(fontFamily = CcFont),
            titleLarge = base.titleLarge.copy(fontFamily = CcFont),
            titleMedium = base.titleMedium.copy(fontFamily = CcFont),
            titleSmall = base.titleSmall.copy(fontFamily = CcFont),
            bodyLarge = base.bodyLarge.copy(fontFamily = CcFont),
            bodyMedium = base.bodyMedium.copy(fontFamily = CcFont),
            bodySmall = base.bodySmall.copy(fontFamily = CcFont),
            labelLarge = base.labelLarge.copy(fontFamily = CcFont),
            labelMedium = base.labelMedium.copy(fontFamily = CcFont),
            labelSmall = base.labelSmall.copy(fontFamily = CcFont),
        )
    }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = typography,
        content = content,
    )
}
