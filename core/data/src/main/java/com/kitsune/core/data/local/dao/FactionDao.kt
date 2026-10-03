package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kitsune.core.data.local.entities.FactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FactionDao {
    @Query("SELECT * FROM factions WHERE universeId = :universeId ORDER BY name")
    fun getByUniverse(universeId: String): Flow<List<FactionEntity>>

    @Query("SELECT * FROM factions WHERE id = :id")
    suspend fun getById(id: String): FactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(faction: FactionEntity)

    @Update
    suspend fun update(faction: FactionEntity)

    @Delete
    suspend fun delete(faction: FactionEntity)
}
