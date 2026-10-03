package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.UniverseImageEntity
import kotlinx.coroutines.flow.Flow

interface UniverseImageRepository {
    fun observeByUniverse(universeId: String): Flow<List<UniverseImageEntity>>
    suspend fun getByUniverse(universeId: String): List<UniverseImageEntity>
    suspend fun upsert(universeImage: UniverseImageEntity)
    suspend fun delete(universeImage: UniverseImageEntity)
}
