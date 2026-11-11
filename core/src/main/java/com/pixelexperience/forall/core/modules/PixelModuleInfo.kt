package com.pixelexperience.forall.core.modules

import androidx.annotation.StringRes

/** Identificadores de todos os módulos planejados da plataforma. */
enum class PixelModuleId { LAUNCHER, THEMES, WIDGETS, CONTROL_CENTER, NOTIFICATIONS, LOCKSCREEN, SOUNDS, EXTRAS }

enum class ModuleStatus {
    /** Implementado e funcional nesta versão. */
    AVAILABLE,

    /** Planejado; ainda não implementado. */
    PLANNED,
}

data class PixelModuleInfo(
    val id: PixelModuleId,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val status: ModuleStatus,
)

/** Estado de um módulo para a UI: informação estática + se está ativado. */
data class ModuleState(
    val info: PixelModuleInfo,
    val enabled: Boolean,
)
