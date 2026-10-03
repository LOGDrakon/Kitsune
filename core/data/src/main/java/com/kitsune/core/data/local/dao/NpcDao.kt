package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kitsune.core.data.local.entities.NpcEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NpcDao {
    @Query("SELECT * FROM npcs WHERE universeId = :universeId ORDER BY importance DESC, name ASC")
    fun getByUniverse(universeId: String): Flow<List<NpcEntity>>

    @Query("SELECT * FROM npcs WHERE factionId = :factionId ORDER BY importance DESC, name ASC")
    fun getByFaction(factionId: String): Flow<List<NpcEntity>>

    @Query("SELECT * FROM npcs WHERE locationId = :locationId ORDER BY importance DESC, name ASC")
    fun getByLocation(locationId: String): Flow<List<NpcEntity>>

    @Query("SELECT * FROM npcs WHERE id = :id")
    suspend fun getById(id: String): NpcEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(npc: NpcEntity)

    @Update
    suspend fun update(npc: NpcEntity)

    @Delete
    suspend fun delete(npc: NpcEntity)
}
