package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.GenerationJobType
import kotlinx.coroutines.flow.Flow

@Dao
interface GenerationJobDao {

    @Query("SELECT * FROM generation_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE type = :type ORDER BY createdAt DESC")
    fun observeByType(type: GenerationJobType): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE state = :state ORDER BY createdAt DESC")
    fun observeByState(state: GenerationJobState): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE chatId = :chatId ORDER BY createdAt DESC")
    fun observeByChatId(chatId: String): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE id = :id")
    fun observeById(id: String): Flow<GenerationJobEntity?>

    @Query("SELECT * FROM generation_jobs WHERE chatId = :chatId AND state = :state ORDER BY createdAt DESC")
    fun observeByChatIdAndState(chatId: String, state: GenerationJobState): Flow<List<GenerationJobEntity>>

    @Query("SELECT * FROM generation_jobs WHERE id = :id")
    suspend fun getById(id: String): GenerationJobEntity?

    @Upsert
    suspend fun upsert(job: GenerationJobEntity)

    @Query("DELETE FROM generation_jobs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(job: GenerationJobEntity)

    /**
     * Marks every still-unfinished job as [GenerationJobState.FAILED]. Called once at app startup:
     * on a cold start no worker can legitimately be running yet, so any `PENDING`/`RUNNING` row is a
     * leftover from a killed process (or a crashed worker) and must be surfaced as failed rather than
     * left spinning forever in the UI. Only rows whose state is in [unfinishedStates] are touched.
     */
    @Query(
        "UPDATE generation_jobs SET state = :failedState, errorMessage = :message, completedAt = :now " +
            "WHERE state IN (:unfinishedStates)"
    )
    suspend fun failStaleJobs(
        unfinishedStates: List<GenerationJobState>,
        failedState: GenerationJobState,
        message: String,
        now: Long
    ): Int
}
