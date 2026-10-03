package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeByChat(chatId: String): Flow<List<MessageEntity>>
    suspend fun getRecent(chatId: String, limit: Int): List<MessageEntity>
    suspend fun getUnsummarized(chatId: String, afterCreatedAt: Long): List<MessageEntity>
    suspend fun getLastMessage(chatId: String): MessageEntity?
    suspend fun getById(id: String): MessageEntity?
    /** Every message at or after [fromCreatedAt], ascending — the tail a "rewind to here" deletes. */
    suspend fun getFrom(chatId: String, fromCreatedAt: Long): List<MessageEntity>
    /** Every message strictly after [afterCreatedAt], ascending — for watermark cursors, where the
     *  inclusive [getFrom] would re-process the watermark message on every run. */
    suspend fun getAfter(chatId: String, afterCreatedAt: Long): List<MessageEntity>
    suspend fun upsert(message: MessageEntity)
    suspend fun delete(message: MessageEntity)
}
