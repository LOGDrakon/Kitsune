package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.ModerationLogEntity
import kotlinx.coroutines.flow.Flow

interface ModerationLogRepository {
    fun observeAll(): Flow<List<ModerationLogEntity>>
    suspend fun record(entry: ModerationLogEntity)
}
