package com.pixelexperience.forall.core.modules

import com.pixelexperience.forall.core.R

/**
 * Catálogo central de módulos. Para entregar um módulo novo em uma parte futura,
 * basta trocar o status dele para [ModuleStatus.AVAILABLE] e implementar a tela.
 */
object ModuleCatalog {
    val all: List<PixelModuleInfo> = listOf(
        PixelModuleInfo(PixelModuleId.LAUNCHER, R.string.module_launcher_title, R.string.module_launcher_desc, ModuleStatus.AVAILABLE),
        PixelModuleInfo(PixelModuleId.THEMES, R.string.module_themes_title, R.string.module_themes_desc, ModuleStatus.AVAILABLE),
        PixelModuleInfo(PixelModuleId.WIDGETS, R.string.module_widgets_title, R.string.module_widgets_desc, ModuleStatus.PLANNED),
        PixelModuleInfo(PixelModuleId.CONTROL_CENTER, R.string.module_controlcenter_title, R.string.module_controlcenter_desc, ModuleStatus.PLANNED),
        PixelModuleInfo(PixelModuleId.NOTIFICATIONS, R.string.module_notifications_title, R.string.module_notifications_desc, ModuleStatus.PLANNED),
        PixelModuleInfo(PixelModuleId.LOCKSCREEN, R.string.module_lockscreen_title, R.string.module_lockscreen_desc, ModuleStatus.PLANNED),
        PixelModuleInfo(PixelModuleId.SOUNDS, R.string.module_sounds_title, R.string.module_sounds_desc, ModuleStatus.PLANNED),
        PixelModuleInfo(PixelModuleId.EXTRAS, R.string.module_extras_title, R.string.module_extras_desc, ModuleStatus.PLANNED),
    )

    fun find(id: PixelModuleId): PixelModuleInfo? = all.firstOrNull { it.id == id }
}
