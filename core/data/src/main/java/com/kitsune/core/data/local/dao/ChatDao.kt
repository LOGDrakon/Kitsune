package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats WHERE archived = 0 ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getById(id: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE personaId = :personaId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getMostRecentForPersona(personaId: String): ChatEntity?

    @Query("SELECT * FROM chats WHERE personaId = :personaId AND archived = 0 ORDER BY updatedAt DESC")
    fun observeByPersona(personaId: String): Flow<List<ChatEntity>>

    /** Ensemble/universe chats only have `universeId` set (their `personaId` is null) — this does
     * not also return persona chats that merely belong to the universe. */
    @Query("SELECT * FROM chats WHERE universeId = :universeId AND personaId IS NULL AND archived = 0 ORDER BY updatedAt DESC")
    fun observeByUniverse(universeId: String): Flow<List<ChatEntity>>

    // @Upsert does a real UPDATE on conflict (not delete+insert like OnConflictStrategy.REPLACE),
    // which matters here: messages/lore/memory fragments CASCADE-delete off chats.id, so a
    // delete+insert on every timestamp bump would silently wipe the whole conversation.
    @Upsert
    suspend fun upsert(chat: ChatEntity)

    @Delete
    suspend fun delete(chat: ChatEntity)
}
