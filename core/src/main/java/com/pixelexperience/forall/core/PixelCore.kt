package com.pixelexperience.forall.core

import android.content.Context
import androidx.room.Room
import com.pixelexperience.forall.core.data.CoreDatabase
import com.pixelexperience.forall.core.modules.ModuleRepository
import com.pixelexperience.forall.core.settings.SettingsRepository

/**
 * Ponto de entrada do módulo core. Injeção manual (sem framework de DI) para manter
 * o app leve; tudo é criado sob demanda.
 */
class PixelCore(context: Context) {
    private val appContext = context.applicationContext

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    private val database: CoreDatabase by lazy {
        Room.databaseBuilder(appContext, CoreDatabase::class.java, "pixel_core.db").build()
    }

    val modules: ModuleRepository by lazy { ModuleRepository(database.moduleStateDao()) }
}
