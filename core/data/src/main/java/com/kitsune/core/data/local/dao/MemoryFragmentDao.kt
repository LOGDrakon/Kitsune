package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource

@Dao
interface MemoryFragmentDao {

    /** Small enough per chat (personal, on-device roleplay stories) to brute-force cosine-similarity in memory — no vector index needed. */
    @Query("SELECT * FROM memory_fragments WHERE chatId = :chatId")
    suspend fun getByChat(chatId: String): List<MemoryFragmentEntity>

    @Query("SELECT * FROM memory_fragments WHERE chatId = :chatId AND sourceType = :sourceType AND sourceKey = :sourceKey LIMIT 1")
    suspend fun findBySourceKey(chatId: String, sourceType: MemoryFragmentSource, sourceKey: String): MemoryFragmentEntity?

    @Upsert
    suspend fun upsert(fragment: MemoryFragmentEntity)

    @Delete
    suspend fun delete(fragment: MemoryFragmentEntity)

    @Query("DELETE FROM memory_fragments WHERE chatId = :chatId")
    suspend fun deleteAllForChat(chatId: String)
}
