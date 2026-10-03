package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.NpcEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NpcRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : NpcRepository {
    private val dao get() = databaseProvider.requireOpen().npcDao()

    override fun getByUniverse(universeId: String): Flow<List<NpcEntity>> = dao.getByUniverse(universeId)
    override fun getByFaction(factionId: String): Flow<List<NpcEntity>> = dao.getByFaction(factionId)
    override fun getByLocation(locationId: String): Flow<List<NpcEntity>> = dao.getByLocation(locationId)
    override suspend fun getById(id: String): NpcEntity? = dao.getById(id)
    override suspend fun insert(npc: NpcEntity) = dao.insert(npc)
    override suspend fun update(npc: NpcEntity) = dao.update(npc)
    override suspend fun delete(npc: NpcEntity) = dao.delete(npc)
}
