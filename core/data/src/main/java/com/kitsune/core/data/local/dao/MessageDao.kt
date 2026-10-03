package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun observeByChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(chatId: String, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND createdAt > :afterCreatedAt ORDER BY createdAt ASC")
    suspend fun getUnsummarized(chatId: String, afterCreatedAt: Long): List<MessageEntity>

    /** Only ever used to render the "last message" preview in the conversation lists, so
     * STYLE_DIRECTIVE markers are excluded at the query: they are model-facing instructions the
     * user never sees, and changing a mode without sending anything would otherwise leave the
     * conversation list showing a raw prompt directive as the latest activity. */
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND role != 'STYLE_DIRECTIVE' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastMessage(chatId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND createdAt <= :upToCreatedAt ORDER BY createdAt ASC")
    suspend fun getMessagesUpTo(chatId: String, upToCreatedAt: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE chatId = :chatId AND createdAt >= :fromCreatedAt ORDER BY createdAt ASC")
    suspend fun getFrom(chatId: String, fromCreatedAt: Long): List<MessageEntity>

    /** Strictly after [afterCreatedAt], unlike [getFrom]'s inclusive `>=`. For watermark-style
     *  cursors, where re-including the watermark message itself in the next batch is a bug — see
     *  BUG-103 and `IndexMessageChunkUseCase`. Same SQL as [getUnsummarized], named for the cursor
     *  semantics rather than for one caller. */
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND createdAt > :afterCreatedAt ORDER BY createdAt ASC")
    suspend fun getAfter(chatId: String, afterCreatedAt: Long): List<MessageEntity>

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllForChat(chatId: String)
}
