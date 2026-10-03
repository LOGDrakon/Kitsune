package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryFragmentRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : MemoryFragmentRepository {

    private val dao get() = databaseProvider.requireOpen().memoryFragmentDao()

    override suspend fun getByChat(chatId: String): List<MemoryFragmentEntity> = dao.getByChat(chatId)

    override suspend fun findBySourceKey(chatId: String, sourceType: MemoryFragmentSource, sourceKey: String): MemoryFragmentEntity? =
        dao.findBySourceKey(chatId, sourceType, sourceKey)

    override suspend fun upsert(fragment: MemoryFragmentEntity) = dao.upsert(fragment)

    override suspend fun delete(fragment: MemoryFragmentEntity) = dao.delete(fragment)

    override suspend fun deleteAllForChat(chatId: String) = dao.deleteAllForChat(chatId)
}
