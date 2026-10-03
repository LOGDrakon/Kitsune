package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.KeyMomentEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyMomentRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : KeyMomentRepository {

    private val dao get() = databaseProvider.requireOpen().keyMomentDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByChat(chatId: String): Flow<List<KeyMomentEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.keyMomentDao()?.observeByChat(chatId) ?: emptyFlow()
        }

    override suspend fun getByChat(chatId: String): List<KeyMomentEntity> = dao.getByChat(chatId)

    override suspend fun getLatestForChat(chatId: String): KeyMomentEntity? = dao.getLatestForChat(chatId)

    override suspend fun getMaxOrder(chatId: String): Int = dao.getMaxOrder(chatId) ?: 0

    override suspend fun upsert(moment: KeyMomentEntity) = dao.upsert(moment)

    override suspend fun delete(moment: KeyMomentEntity) = dao.delete(moment)

    override suspend fun deleteByChat(chatId: String) = dao.deleteByChat(chatId)
}