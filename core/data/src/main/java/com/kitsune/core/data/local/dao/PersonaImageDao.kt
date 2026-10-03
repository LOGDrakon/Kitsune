package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.PersonaImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaImageDao {

    @Query("SELECT * FROM persona_images WHERE personaId = :personaId ORDER BY createdAt DESC")
    fun observeByPersona(personaId: String): Flow<List<PersonaImageEntity>>

    @Query("SELECT * FROM persona_images WHERE personaId = :personaId ORDER BY createdAt DESC")
    suspend fun getByPersona(personaId: String): List<PersonaImageEntity>

    @Upsert
    suspend fun upsert(personaImage: PersonaImageEntity)

    @Delete
    suspend fun delete(personaImage: PersonaImageEntity)
}
