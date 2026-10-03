package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.UniverseEntity
import kotlinx.coroutines.flow.Flow

interface UniverseRepository {
    fun getAll(): Flow<List<UniverseEntity>>
    suspend fun getById(id: String): UniverseEntity?
    suspend fun getBySourceListingId(listingId: String): UniverseEntity?
    suspend fun insert(universe: UniverseEntity)
    suspend fun update(universe: UniverseEntity)
    suspend fun delete(universe: UniverseEntity)
}
