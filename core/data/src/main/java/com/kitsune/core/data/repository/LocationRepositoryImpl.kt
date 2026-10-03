package com.kitsune.core.data.repository

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.entities.LocationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val databaseProvider: KitsuneDatabaseProvider
) : LocationRepository {
    private val dao get() = databaseProvider.requireOpen().locationDao()

    override fun getByUniverse(universeId: String): Flow<List<LocationEntity>> = dao.getByUniverse(universeId)
    override suspend fun getById(id: String): LocationEntity? = dao.getById(id)
    override suspend fun insert(location: LocationEntity) = dao.insert(location)
    override suspend fun update(location: LocationEntity) = dao.update(location)
    override suspend fun delete(location: LocationEntity) = dao.delete(location)
}
