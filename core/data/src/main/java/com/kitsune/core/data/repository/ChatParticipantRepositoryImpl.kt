package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.ChatParticipantEntity
import com.kitsune.core.data.local.entities.ParticipantType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatParticipantRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : ChatParticipantRepository {

    private val dao get() = databaseProvider.requireOpen().chatParticipantDao()

    override suspend fun getByChat(chatId: String): List<ChatParticipantEntity> = dao.getByChat(chatId)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByChat(chatId: String): Flow<List<ChatParticipantEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.chatParticipantDao()?.observeByChat(chatId) ?: emptyFlow()
        }

    override suspend fun addParticipant(chatId: String, type: ParticipantType, participantId: String) {
        dao.insert(
            ChatParticipantEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                participantType = type,
                participantId = participantId,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun removeParticipant(participant: ChatParticipantEntity) = dao.delete(participant)
}
