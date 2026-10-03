package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.KeyMomentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyMomentDao {

    @Query("SELECT * FROM key_moments WHERE chatId = :chatId ORDER BY momentOrder ASC")
    fun observeByChat(chatId: String): Flow<List<KeyMomentEntity>>

    @Query("SELECT * FROM key_moments WHERE chatId = :chatId ORDER BY momentOrder ASC")
    suspend fun getByChat(chatId: String): List<KeyMomentEntity>

    @Query("SELECT * FROM key_moments WHERE chatId = :chatId ORDER BY momentOrder DESC LIMIT 1")
    suspend fun getLatestForChat(chatId: String): KeyMomentEntity?

    @Query("SELECT COUNT(*) FROM key_moments WHERE chatId = :chatId")
    suspend fun countForChat(chatId: String): Int

    @Query("SELECT MAX(momentOrder) FROM key_moments WHERE chatId = :chatId")
    suspend fun getMaxOrder(chatId: String): Int?

    @Upsert
    suspend fun upsert(moment: KeyMomentEntity)

    @Delete
    suspend fun delete(moment: KeyMomentEntity)

    @Query("DELETE FROM key_moments WHERE chatId = :chatId")
    suspend fun deleteByChat(chatId: String)
}