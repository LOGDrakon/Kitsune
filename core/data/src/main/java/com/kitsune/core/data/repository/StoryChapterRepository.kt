package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.StoryChapterEntity
import kotlinx.coroutines.flow.Flow

/** Memory level 2 storage, hierarchical form (FEATURES.md section 4: "chapitres figés"). */
interface StoryChapterRepository {
    fun observeByChat(chatId: String): Flow<List<StoryChapterEntity>>
    suspend fun getByChat(chatId: String): List<StoryChapterEntity>
    suspend fun getMaxChapterIndex(chatId: String): Int
    /** Deletes every chapter reaching into the deleted tail — see [RewindChatUseCase]. */
    suspend fun deleteFrom(chatId: String, fromCreatedAt: Long)
    suspend fun upsert(chapter: StoryChapterEntity)
    suspend fun delete(chapter: StoryChapterEntity)
}
