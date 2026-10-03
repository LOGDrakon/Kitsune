package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.UniverseImageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UniverseImageRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : UniverseImageRepository {

    private val dao get() = databaseProvider.requireOpen().universeImageDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByUniverse(universeId: String): Flow<List<UniverseImageEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.universeImageDao()?.observeByUniverse(universeId) ?: emptyFlow()
        }

    override suspend fun getByUniverse(universeId: String): List<UniverseImageEntity> = dao.getByUniverse(universeId)

    override suspend fun upsert(universeImage: UniverseImageEntity) = dao.upsert(universeImage)

    override suspend fun delete(universeImage: UniverseImageEntity) = dao.delete(universeImage)
}
