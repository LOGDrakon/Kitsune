package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.ToneCardEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToneCardRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : ToneCardRepository {

    private val dao get() = databaseProvider.requireOpen().toneCardDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByPersona(personaId: String): Flow<List<ToneCardEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.toneCardDao()?.observeByPersona(personaId) ?: emptyFlow()
        }

    override suspend fun getByPersona(personaId: String): List<ToneCardEntity> = dao.getByPersona(personaId)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByUniverse(universeId: String): Flow<List<ToneCardEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.toneCardDao()?.observeByUniverse(universeId) ?: emptyFlow()
        }

    override suspend fun getByUniverse(universeId: String): List<ToneCardEntity> = dao.getByUniverse(universeId)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProfileCards(): Flow<List<ToneCardEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.toneCardDao()?.observeProfileCards() ?: emptyFlow()
        }

    override suspend fun getProfileCards(): List<ToneCardEntity> = dao.getProfileCards()

    override suspend fun getById(id: String): ToneCardEntity? = dao.getById(id)

    override suspend fun upsert(toneCard: ToneCardEntity) = dao.upsert(toneCard)

    override suspend fun delete(toneCard: ToneCardEntity) = dao.delete(toneCard)
}
