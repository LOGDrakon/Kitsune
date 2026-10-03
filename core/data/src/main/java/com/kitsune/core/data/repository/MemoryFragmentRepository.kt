package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource

/** Memory level 4 storage (FEATURES.md section 4: "RAG local, embeddings on-device, recherche sémantique"). */
interface MemoryFragmentRepository {
    suspend fun getByChat(chatId: String): List<MemoryFragmentEntity>
    suspend fun findBySourceKey(chatId: String, sourceType: MemoryFragmentSource, sourceKey: String): MemoryFragmentEntity?
    suspend fun upsert(fragment: MemoryFragmentEntity)
    suspend fun delete(fragment: MemoryFragmentEntity)
    suspend fun deleteAllForChat(chatId: String)
}
