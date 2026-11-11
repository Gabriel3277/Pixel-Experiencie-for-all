package com.pixelexperience.forall.launcher

/** Um app de tela inicial (launcher) instalado no aparelho. */
data class InstalledLauncher(
    val packageName: String,
    val label: String,
    /** Nome da activity HOME do app. */
    val homeActivity: String,
    val isLawnchair: Boolean,
    val isDefault: Boolean,
)

data class LauncherStatus(val launchers: List<InstalledLauncher>) {
    /** Primeiro Lawnchair encontrado (o fork do usuário costuma ser app.lawnchair*). */
    val lawnchair: InstalledLauncher? get() = launchers.firstOrNull { it.isLawnchair }
}
