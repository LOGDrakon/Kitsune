package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kitsune.core.data.local.entities.ModerationLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModerationLogDao {

    @Query("SELECT * FROM moderation_logs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ModerationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ModerationLogEntity)
}
