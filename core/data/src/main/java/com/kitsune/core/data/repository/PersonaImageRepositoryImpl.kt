package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.PersonaImageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonaImageRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : PersonaImageRepository {

    private val dao get() = databaseProvider.requireOpen().personaImageDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByPersona(personaId: String): Flow<List<PersonaImageEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.personaImageDao()?.observeByPersona(personaId) ?: emptyFlow()
        }

    override suspend fun getByPersona(personaId: String): List<PersonaImageEntity> = dao.getByPersona(personaId)

    override suspend fun upsert(personaImage: PersonaImageEntity) = dao.upsert(personaImage)

    override suspend fun delete(personaImage: PersonaImageEntity) = dao.delete(personaImage)
}
