package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.UniverseEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UniverseRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : UniverseRepository {
    private val dao get() = databaseProvider.requireOpen().universeDao()

    override fun getAll(): Flow<List<UniverseEntity>> = dao.observeAll()
    override suspend fun getById(id: String): UniverseEntity? = dao.getById(id)
    override suspend fun getBySourceListingId(listingId: String): UniverseEntity? = dao.getBySourceListingId(listingId)
    override suspend fun insert(universe: UniverseEntity) = dao.upsert(universe)
    override suspend fun update(universe: UniverseEntity) = dao.upsert(universe)
    override suspend fun delete(universe: UniverseEntity) = dao.delete(universe)
}
