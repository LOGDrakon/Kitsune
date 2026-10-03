package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.ToneCardEntity
import kotlinx.coroutines.flow.Flow

interface ToneCardRepository {
    fun observeByPersona(personaId: String): Flow<List<ToneCardEntity>>
    suspend fun getByPersona(personaId: String): List<ToneCardEntity>
    fun observeByUniverse(universeId: String): Flow<List<ToneCardEntity>>
    suspend fun getByUniverse(universeId: String): List<ToneCardEntity>
    /** The user's own library — tones attached to no persona and no universe. */
    fun observeProfileCards(): Flow<List<ToneCardEntity>>
    suspend fun getProfileCards(): List<ToneCardEntity>
    suspend fun getById(id: String): ToneCardEntity?
    suspend fun upsert(toneCard: ToneCardEntity)
    suspend fun delete(toneCard: ToneCardEntity)
}
