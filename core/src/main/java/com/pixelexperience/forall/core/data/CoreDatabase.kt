package com.pixelexperience.forall.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ModuleStateEntity::class], version = 1, exportSchema = false)
abstract class CoreDatabase : RoomDatabase() {
    abstract fun moduleStateDao(): ModuleStateDao
}
