package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.NoteEntity
import kotlinx.coroutines.flow.Flow

/** Storage for the decoy notes app (see `DecoyNotesDatabase`). */
interface NoteRepository {
    fun observeAll(): Flow<List<NoteEntity>>
    fun observeSearch(query: String): Flow<List<NoteEntity>>
    suspend fun getById(id: String): NoteEntity?
    suspend fun upsert(note: NoteEntity)
    suspend fun delete(note: NoteEntity)
}
