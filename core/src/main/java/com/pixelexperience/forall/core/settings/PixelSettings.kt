package com.pixelexperience.forall.core.settings

/** Configurações centrais do Pixel Experience. */
data class PixelSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** true = cores do sistema (Material You); false = paleta Pixel fixa. */
    val dynamicColor: Boolean = false,
)
