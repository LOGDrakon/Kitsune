package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.ChatParticipantEntity
import com.kitsune.core.data.local.entities.ParticipantType
import kotlinx.coroutines.flow.Flow

interface ChatParticipantRepository {
    suspend fun getByChat(chatId: String): List<ChatParticipantEntity>
    fun observeByChat(chatId: String): Flow<List<ChatParticipantEntity>>
    suspend fun addParticipant(chatId: String, type: ParticipantType, participantId: String)
    suspend fun removeParticipant(participant: ChatParticipantEntity)
}
