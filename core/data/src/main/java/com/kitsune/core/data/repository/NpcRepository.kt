package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.NpcEntity
import kotlinx.coroutines.flow.Flow

interface NpcRepository {
    fun getByUniverse(universeId: String): Flow<List<NpcEntity>>
    fun getByFaction(factionId: String): Flow<List<NpcEntity>>
    fun getByLocation(locationId: String): Flow<List<NpcEntity>>
    suspend fun getById(id: String): NpcEntity?
    suspend fun insert(npc: NpcEntity)
    suspend fun update(npc: NpcEntity)
    suspend fun delete(npc: NpcEntity)
}
