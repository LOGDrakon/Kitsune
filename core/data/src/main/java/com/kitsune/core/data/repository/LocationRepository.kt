package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.LocationEntity
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getByUniverse(universeId: String): Flow<List<LocationEntity>>
    suspend fun getById(id: String): LocationEntity?
    suspend fun insert(location: LocationEntity)
    suspend fun update(location: LocationEntity)
    suspend fun delete(location: LocationEntity)
}
