package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.ChatImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatImageDao {

    @Query("SELECT * FROM chat_images WHERE chatId = :chatId ORDER BY createdAt DESC")
    fun observeByChat(chatId: String): Flow<List<ChatImageEntity>>

    @Query("SELECT * FROM chat_images WHERE chatId = :chatId ORDER BY createdAt DESC")
    suspend fun getByChat(chatId: String): List<ChatImageEntity>

    @Upsert
    suspend fun upsert(chatImage: ChatImageEntity)

    @Delete
    suspend fun delete(chatImage: ChatImageEntity)
}
