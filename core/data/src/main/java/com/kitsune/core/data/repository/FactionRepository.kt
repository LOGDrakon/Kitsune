package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.FactionEntity
import kotlinx.coroutines.flow.Flow

interface FactionRepository {
    fun getByUniverse(universeId: String): Flow<List<FactionEntity>>
    suspend fun getById(id: String): FactionEntity?
    suspend fun insert(faction: FactionEntity)
    suspend fun update(faction: FactionEntity)
    suspend fun delete(faction: FactionEntity)
}
