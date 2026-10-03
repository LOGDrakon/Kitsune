package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.StoryChapterEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryChapterRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : StoryChapterRepository {

    private val dao get() = databaseProvider.requireOpen().storyChapterDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByChat(chatId: String): Flow<List<StoryChapterEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.storyChapterDao()?.observeByChat(chatId) ?: emptyFlow()
        }

    override suspend fun getByChat(chatId: String): List<StoryChapterEntity> = dao.getByChat(chatId)

    override suspend fun getMaxChapterIndex(chatId: String): Int = dao.getMaxChapterIndex(chatId) ?: 0

    override suspend fun deleteFrom(chatId: String, fromCreatedAt: Long) = dao.deleteFrom(chatId, fromCreatedAt)

    override suspend fun upsert(chapter: StoryChapterEntity) = dao.upsert(chapter)

    override suspend fun delete(chapter: StoryChapterEntity) = dao.delete(chapter)
}
