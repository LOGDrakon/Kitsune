package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.UniverseImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UniverseImageDao {

    @Query("SELECT * FROM universe_images WHERE universeId = :universeId ORDER BY createdAt DESC")
    fun observeByUniverse(universeId: String): Flow<List<UniverseImageEntity>>

    @Query("SELECT * FROM universe_images WHERE universeId = :universeId ORDER BY createdAt DESC")
    suspend fun getByUniverse(universeId: String): List<UniverseImageEntity>

    @Upsert
    suspend fun upsert(universeImage: UniverseImageEntity)

    @Delete
    suspend fun delete(universeImage: UniverseImageEntity)
}
