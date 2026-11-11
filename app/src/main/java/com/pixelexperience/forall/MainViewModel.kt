package com.pixelexperience.forall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixelexperience.forall.core.modules.ModuleState
import com.pixelexperience.forall.core.modules.PixelModuleId
import com.pixelexperience.forall.core.settings.PixelSettings
import com.pixelexperience.forall.core.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val settings: PixelSettings = PixelSettings(),
    val modules: List<ModuleState> = emptyList(),
) {
    val themesEnabled: Boolean
        get() = modules.firstOrNull { it.info.id == PixelModuleId.THEMES }?.enabled ?: true

    /** Com o módulo Temas desativado, o app volta ao tema do sistema e à paleta Pixel. */
    val effectiveThemeMode: ThemeMode
        get() = if (themesEnabled) settings.themeMode else ThemeMode.SYSTEM

    val effectiveDynamicColor: Boolean
        get() = themesEnabled && settings.dynamicColor
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val core = (application as PixelApplication).core

    val uiState: StateFlow<MainUiState> = combine(
        core.settings.settings,
        core.modules.modules,
    ) { settings, modules -> MainUiState(settings, modules) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { core.settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { core.settings.setDynamicColor(enabled) }
    }

    fun setModuleEnabled(id: PixelModuleId, enabled: Boolean) {
        viewModelScope.launch { core.modules.setEnabled(id, enabled) }
    }
}
