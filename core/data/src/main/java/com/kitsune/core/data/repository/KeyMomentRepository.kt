package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.KeyMomentEntity
import kotlinx.coroutines.flow.Flow

interface KeyMomentRepository {
    fun observeByChat(chatId: String): Flow<List<KeyMomentEntity>>
    suspend fun getByChat(chatId: String): List<KeyMomentEntity>
    suspend fun getLatestForChat(chatId: String): KeyMomentEntity?
    suspend fun getMaxOrder(chatId: String): Int
    suspend fun upsert(moment: KeyMomentEntity)
    suspend fun delete(moment: KeyMomentEntity)
    suspend fun deleteByChat(chatId: String)
}