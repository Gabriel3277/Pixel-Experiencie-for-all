package com.pixelexperience.forall.core.modules

import com.pixelexperience.forall.core.data.ModuleStateDao
import com.pixelexperience.forall.core.data.ModuleStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Guarda (Room) quais módulos o usuário ativou. Módulos planejados não podem ser ativados. */
class ModuleRepository(private val dao: ModuleStateDao) {

    val modules: Flow<List<ModuleState>> = dao.observeAll().map { rows ->
        val saved = rows.associate { it.moduleId to it.enabled }
        ModuleCatalog.all.map { info ->
            ModuleState(
                info = info,
                enabled = info.status == ModuleStatus.AVAILABLE && (saved[info.id.name] ?: true),
            )
        }
    }

    suspend fun setEnabled(id: PixelModuleId, enabled: Boolean) {
        val info = ModuleCatalog.find(id) ?: return
        if (info.status != ModuleStatus.AVAILABLE) return
        dao.upsert(ModuleStateEntity(moduleId = id.name, enabled = enabled))
    }
}
