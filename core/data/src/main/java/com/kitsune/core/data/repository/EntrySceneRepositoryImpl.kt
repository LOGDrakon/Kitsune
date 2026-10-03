package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.EntrySceneEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntrySceneRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : EntrySceneRepository {

    private val dao get() = databaseProvider.requireOpen().entrySceneDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByPersona(personaId: String): Flow<List<EntrySceneEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.entrySceneDao()?.observeByPersona(personaId) ?: emptyFlow()
        }

    override suspend fun getByPersona(personaId: String): List<EntrySceneEntity> = dao.getByPersona(personaId)

    override suspend fun getById(id: String): EntrySceneEntity? = dao.getById(id)

    override suspend fun upsert(entryScene: EntrySceneEntity) = dao.upsert(entryScene)

    override suspend fun delete(entryScene: EntrySceneEntity) = dao.delete(entryScene)
}
