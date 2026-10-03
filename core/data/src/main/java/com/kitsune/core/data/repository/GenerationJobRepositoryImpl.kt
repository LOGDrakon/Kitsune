package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.GenerationJobDatabase
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.GenerationJobType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerationJobRepositoryImpl @Inject constructor(
    private val database: GenerationJobDatabase
) : GenerationJobRepository {

    private val dao = database.generationJobDao()

    override fun observeAll(): Flow<List<GenerationJobEntity>> = dao.observeAll()

    override fun observeByType(type: GenerationJobType): Flow<List<GenerationJobEntity>> =
        dao.observeByType(type)

    override fun observeByState(state: GenerationJobState): Flow<List<GenerationJobEntity>> =
        dao.observeByState(state)

    override fun observeByChatId(chatId: String): Flow<List<GenerationJobEntity>> =
        dao.observeByChatId(chatId)

    override fun observeById(id: String): Flow<GenerationJobEntity?> = dao.observeById(id)

    override suspend fun getById(id: String): GenerationJobEntity? = dao.getById(id)

    override suspend fun upsert(job: GenerationJobEntity) = dao.upsert(job)

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    override suspend fun delete(job: GenerationJobEntity) = dao.delete(job)

    override suspend fun failStalePendingJobs(): Int = dao.failStaleJobs(
        // Only PENDING: a job that reached RUNNING has an active worker in this same process that
        // will finalize it (marked SUCCEEDED/FAILED itself). PENDING at cold start means the worker
        // never started or died before running — safe to fail.
        unfinishedStates = listOf(GenerationJobState.PENDING),
        failedState = GenerationJobState.FAILED,
        message = "Génération interrompue (l'application a été fermée avant la fin).",
        now = System.currentTimeMillis()
    )
}
