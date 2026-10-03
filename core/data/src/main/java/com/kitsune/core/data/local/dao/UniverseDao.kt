package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.UniverseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UniverseDao {

    @Query("SELECT * FROM universes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<UniverseEntity>>

    @Query("SELECT * FROM universes WHERE id = :id")
    suspend fun getById(id: String): UniverseEntity?

    @Query("SELECT * FROM universes WHERE sourceListingId = :listingId LIMIT 1")
    suspend fun getBySourceListingId(listingId: String): UniverseEntity?

    @Upsert
    suspend fun upsert(universe: UniverseEntity)

    @Delete
    suspend fun delete(universe: UniverseEntity)
}
