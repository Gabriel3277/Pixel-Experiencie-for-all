package com.pixelexperience.forall.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ModuleStateDao {
    @Query("SELECT * FROM module_state")
    fun observeAll(): Flow<List<ModuleStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ModuleStateEntity)
}
