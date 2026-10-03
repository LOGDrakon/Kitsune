package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.PersonaImageEntity
import kotlinx.coroutines.flow.Flow

interface PersonaImageRepository {
    fun observeByPersona(personaId: String): Flow<List<PersonaImageEntity>>
    suspend fun getByPersona(personaId: String): List<PersonaImageEntity>
    suspend fun upsert(personaImage: PersonaImageEntity)
    suspend fun delete(personaImage: PersonaImageEntity)
}
