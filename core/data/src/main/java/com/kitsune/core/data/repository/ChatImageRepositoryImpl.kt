package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.ChatImageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatImageRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : ChatImageRepository {

    private val dao get() = databaseProvider.requireOpen().chatImageDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByChat(chatId: String): Flow<List<ChatImageEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.chatImageDao()?.observeByChat(chatId) ?: emptyFlow()
        }

    override suspend fun getByChat(chatId: String): List<ChatImageEntity> = dao.getByChat(chatId)

    override suspend fun upsert(chatImage: ChatImageEntity) = dao.upsert(chatImage)

    override suspend fun delete(chatImage: ChatImageEntity) = dao.delete(chatImage)
}
