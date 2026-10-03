package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {

    @Query("SELECT * FROM personas ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE universeId = :universeId ORDER BY updatedAt DESC")
    fun observeByUniverse(universeId: String): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun getById(id: String): PersonaEntity?

    @Query("SELECT * FROM personas WHERE sourceListingId = :listingId LIMIT 1")
    suspend fun getBySourceListingId(listingId: String): PersonaEntity?

    // See ChatDao.upsert — same reasoning: chats/persona_images/entry_scenes CASCADE off
    // personas.id, so a delete+insert on every persona edit (e.g. avatar change) would silently
    // wipe every conversation for that persona.
    @Upsert
    suspend fun upsert(persona: PersonaEntity)

    @Delete
    suspend fun delete(persona: PersonaEntity)
}
