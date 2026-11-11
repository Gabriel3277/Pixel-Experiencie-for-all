package com.pixelexperience.forall.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "module_state")
data class ModuleStateEntity(
    @PrimaryKey val moduleId: String,
    val enabled: Boolean,
)
