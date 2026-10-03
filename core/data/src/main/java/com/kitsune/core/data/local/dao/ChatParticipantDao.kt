package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.kitsune.core.data.local.entities.ChatParticipantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatParticipantDao {

    @Query("SELECT * FROM chat_participants WHERE chatId = :chatId")
    suspend fun getByChat(chatId: String): List<ChatParticipantEntity>

    @Query("SELECT * FROM chat_participants WHERE chatId = :chatId")
    fun observeByChat(chatId: String): Flow<List<ChatParticipantEntity>>

    @Insert
    suspend fun insert(participant: ChatParticipantEntity)

    @Delete
    suspend fun delete(participant: ChatParticipantEntity)
}
