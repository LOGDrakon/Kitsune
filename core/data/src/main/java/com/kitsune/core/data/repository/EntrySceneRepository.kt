package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.EntrySceneEntity
import kotlinx.coroutines.flow.Flow

interface EntrySceneRepository {
    fun observeByPersona(personaId: String): Flow<List<EntrySceneEntity>>
    suspend fun getByPersona(personaId: String): List<EntrySceneEntity>
    suspend fun getById(id: String): EntrySceneEntity?
    suspend fun upsert(entryScene: EntrySceneEntity)
    suspend fun delete(entryScene: EntrySceneEntity)
}
