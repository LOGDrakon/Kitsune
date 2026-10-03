package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.FactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FactionRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : FactionRepository {
    private val dao get() = databaseProvider.requireOpen().factionDao()

    override fun getByUniverse(universeId: String): Flow<List<FactionEntity>> = dao.getByUniverse(universeId)
    override suspend fun getById(id: String): FactionEntity? = dao.getById(id)
    override suspend fun insert(faction: FactionEntity) = dao.insert(faction)
    override suspend fun update(faction: FactionEntity) = dao.update(faction)
    override suspend fun delete(faction: FactionEntity) = dao.delete(faction)
}
