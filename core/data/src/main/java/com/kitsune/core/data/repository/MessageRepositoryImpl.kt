package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.MessageAuditLogEntity
import com.kitsune.core.data.local.entities.MessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider,
    private val auditLogRepository: MessageAuditLogRepository
) : MessageRepository {

    private val dao get() = databaseProvider.requireOpen().messageDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeByChat(chatId: String): Flow<List<MessageEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.messageDao()?.observeByChat(chatId) ?: emptyFlow()
        }

    override suspend fun getRecent(chatId: String, limit: Int): List<MessageEntity> = dao.getRecent(chatId, limit)

    override suspend fun getUnsummarized(chatId: String, afterCreatedAt: Long): List<MessageEntity> =
        dao.getUnsummarized(chatId, afterCreatedAt)

    override suspend fun getLastMessage(chatId: String): MessageEntity? = dao.getLastMessage(chatId)

    override suspend fun getById(id: String): MessageEntity? = dao.getById(id)

    override suspend fun getFrom(chatId: String, fromCreatedAt: Long): List<MessageEntity> = dao.getFrom(chatId, fromCreatedAt)

    override suspend fun getAfter(chatId: String, afterCreatedAt: Long): List<MessageEntity> = dao.getAfter(chatId, afterCreatedAt)

    override suspend fun upsert(message: MessageEntity) {
        // Only log genuine new inserts, never edit overwrites — the point of the audit trail is
        // to preserve the ORIGINAL content, which was already captured the first time this id was
        // seen. Checking existence first (rather than e.g. an `isEdited` flag) also naturally
        // covers every current and future call site of upsert() without needing to touch them.
        val isNewMessage = dao.getById(message.id) == null
        dao.upsert(message)
        if (isNewMessage) {
            auditLogRepository.record(
                MessageAuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = message.chatId,
                    messageId = message.id,
                    role = message.role,
                    content = message.content,
                    createdAt = message.createdAt
                )
            )
        }
    }

    override suspend fun delete(message: MessageEntity) = dao.delete(message)
}
