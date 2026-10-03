package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.MessageVariantEntity

interface MessageVariantRepository {
    suspend fun getByMessage(messageId: String): List<MessageVariantEntity>
    suspend fun upsert(variant: MessageVariantEntity)
    suspend fun deleteByMessage(messageId: String)
}
