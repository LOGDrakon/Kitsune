package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.EntrySceneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntrySceneDao {

    @Query("SELECT * FROM entry_scenes WHERE personaId = :personaId ORDER BY createdAt ASC")
    fun observeByPersona(personaId: String): Flow<List<EntrySceneEntity>>

    @Query("SELECT * FROM entry_scenes WHERE personaId = :personaId ORDER BY createdAt ASC")
    suspend fun getByPersona(personaId: String): List<EntrySceneEntity>

    @Query("SELECT * FROM entry_scenes WHERE id = :id")
    suspend fun getById(id: String): EntrySceneEntity?

    @Upsert
    suspend fun upsert(entryScene: EntrySceneEntity)

    @Delete
    suspend fun delete(entryScene: EntrySceneEntity)
}
