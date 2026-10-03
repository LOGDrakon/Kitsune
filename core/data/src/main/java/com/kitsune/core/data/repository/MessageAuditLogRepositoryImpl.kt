package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.MessageAuditLogEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageAuditLogRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : MessageAuditLogRepository {

    private val dao get() = databaseProvider.requireOpen().messageAuditLogDao()

    override suspend fun getByChatId(chatId: String): List<MessageAuditLogEntity> = dao.getByChatId(chatId)

    override suspend fun record(entry: MessageAuditLogEntity) = dao.insert(entry)
}
