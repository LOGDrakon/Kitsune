package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.ModerationLogEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModerationLogRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : ModerationLogRepository {

    private val dao get() = databaseProvider.requireOpen().moderationLogDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAll(): Flow<List<ModerationLogEntity>> =
        databaseProvider.databaseState.flatMapLatest { db ->
            db?.moderationLogDao()?.observeAll() ?: emptyFlow()
        }

    override suspend fun record(entry: ModerationLogEntity) = dao.insert(entry)
}
