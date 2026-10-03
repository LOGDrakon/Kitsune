package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.StoryChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryChapterDao {

    @Query("SELECT * FROM story_chapters WHERE chatId = :chatId ORDER BY chapterIndex ASC")
    fun observeByChat(chatId: String): Flow<List<StoryChapterEntity>>

    @Query("SELECT * FROM story_chapters WHERE chatId = :chatId ORDER BY chapterIndex ASC")
    suspend fun getByChat(chatId: String): List<StoryChapterEntity>

    @Query("SELECT MAX(chapterIndex) FROM story_chapters WHERE chatId = :chatId")
    suspend fun getMaxChapterIndex(chatId: String): Int?

    /** Chapters invalidated by a rewind: any chapter whose range reaches into the deleted tail. */
    @Query("DELETE FROM story_chapters WHERE chatId = :chatId AND throughCreatedAt >= :fromCreatedAt")
    suspend fun deleteFrom(chatId: String, fromCreatedAt: Long)

    @Upsert
    suspend fun upsert(chapter: StoryChapterEntity)

    @Delete
    suspend fun delete(chapter: StoryChapterEntity)

    @Query("DELETE FROM story_chapters WHERE chatId = :chatId")
    suspend fun deleteByChat(chatId: String)
}
