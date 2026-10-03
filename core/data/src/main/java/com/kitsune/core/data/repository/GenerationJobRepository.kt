package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.GenerationJobType
import kotlinx.coroutines.flow.Flow

interface GenerationJobRepository {
    fun observeAll(): Flow<List<GenerationJobEntity>>
    fun observeByType(type: GenerationJobType): Flow<List<GenerationJobEntity>>
    fun observeByState(state: GenerationJobState): Flow<List<GenerationJobEntity>>
    fun observeByChatId(chatId: String): Flow<List<GenerationJobEntity>>
    fun observeById(id: String): Flow<GenerationJobEntity?>
    suspend fun getById(id: String): GenerationJobEntity?
    suspend fun upsert(job: GenerationJobEntity)
    suspend fun deleteById(id: String)
    suspend fun delete(job: GenerationJobEntity)

    /**
     * Marks any leftover `PENDING`/`RUNNING` jobs as `FAILED`. Intended to be called once at app
     * startup to clear jobs orphaned by a killed process or a crashed worker (see BUGS.md BUG-037).
     * Returns the number of rows updated.
     */
    suspend fun failStalePendingJobs(): Int
}
