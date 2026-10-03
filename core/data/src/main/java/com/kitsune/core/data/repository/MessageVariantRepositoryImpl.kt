package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.MessageVariantEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageVariantRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : MessageVariantRepository {

    private val dao get() = databaseProvider.requireOpen().messageVariantDao()

    override suspend fun getByMessage(messageId: String) = dao.getByMessage(messageId)

    override suspend fun upsert(variant: MessageVariantEntity) = dao.upsert(variant)

    override suspend fun deleteByMessage(messageId: String) = dao.deleteByMessage(messageId)
}
