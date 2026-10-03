package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.PersonaEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonaRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : PersonaRepository {

    private val dao get() = databaseProvider.requireOpen().personaDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAll(): Flow<List<PersonaEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.personaDao()?.observeAll() ?: emptyFlow()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByUniverse(universeId: String): Flow<List<PersonaEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.personaDao()?.observeByUniverse(universeId) ?: emptyFlow()
        }

    override suspend fun getById(id: String): PersonaEntity? = dao.getById(id)

    override suspend fun getBySourceListingId(listingId: String): PersonaEntity? = dao.getBySourceListingId(listingId)

    override suspend fun upsert(persona: PersonaEntity) = dao.upsert(persona)

    override suspend fun delete(persona: PersonaEntity) = dao.delete(persona)
}
