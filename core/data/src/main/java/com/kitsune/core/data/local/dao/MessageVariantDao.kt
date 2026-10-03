package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.MessageVariantEntity

@Dao
interface MessageVariantDao {

    @Query("SELECT * FROM message_variants WHERE messageId = :messageId ORDER BY createdAt ASC")
    suspend fun getByMessage(messageId: String): List<MessageVariantEntity>

    @Upsert
    suspend fun upsert(variant: MessageVariantEntity)

    @Query("DELETE FROM message_variants WHERE messageId = :messageId")
    suspend fun deleteByMessage(messageId: String)
}
