package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.MessageAuditLogEntity

interface MessageAuditLogRepository {
    suspend fun getByChatId(chatId: String): List<MessageAuditLogEntity>
    suspend fun record(entry: MessageAuditLogEntity)
}
