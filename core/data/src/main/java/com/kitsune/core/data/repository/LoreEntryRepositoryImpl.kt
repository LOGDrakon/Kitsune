package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.LoreEntryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoreEntryRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : LoreEntryRepository {

    private val dao get() = databaseProvider.requireOpen().loreEntryDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByChat(chatId: String): Flow<List<LoreEntryEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.loreEntryDao()?.observeByChat(chatId) ?: emptyFlow()
        }

    override suspend fun getByChat(chatId: String): List<LoreEntryEntity> = dao.getByChat(chatId)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByUniverse(universeId: String): Flow<List<LoreEntryEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.loreEntryDao()?.observeByUniverse(universeId) ?: emptyFlow()
        }

    override suspend fun getByUniverse(universeId: String): List<LoreEntryEntity> = dao.getByUniverse(universeId)

    override suspend fun getByPersona(personaId: String): List<LoreEntryEntity> = dao.getByPersona(personaId)

    override suspend fun findByChatAndName(chatId: String, name: String): LoreEntryEntity? =
        dao.findByChatAndName(chatId, name)

    override suspend fun findByChatAndNameOrAlias(chatId: String, name: String): LoreEntryEntity? =
        dao.findByChatAndNameOrAlias(chatId, name)

    override suspend fun upsert(entry: LoreEntryEntity) = dao.upsert(entry)

    override suspend fun delete(entry: LoreEntryEntity) = dao.delete(entry)
}
