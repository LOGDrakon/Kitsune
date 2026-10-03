package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.ChatImageEntity
import kotlinx.coroutines.flow.Flow

interface ChatImageRepository {
    fun observeByChat(chatId: String): Flow<List<ChatImageEntity>>
    suspend fun getByChat(chatId: String): List<ChatImageEntity>
    suspend fun upsert(chatImage: ChatImageEntity)
    suspend fun delete(chatImage: ChatImageEntity)
}
