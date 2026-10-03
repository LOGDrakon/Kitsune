package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.PersonaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Only usable once the vault has been unlocked and [com.kitsune.core.data.local.database.KitsuneDatabaseProvider]
 * has an open database — see that class for why this can't just be a Hilt-provided DAO singleton.
 */
interface PersonaRepository {
    fun observeAll(): Flow<List<PersonaEntity>>
    fun observeByUniverse(universeId: String): Flow<List<PersonaEntity>>
    suspend fun getById(id: String): PersonaEntity?
    suspend fun getBySourceListingId(listingId: String): PersonaEntity?
    suspend fun upsert(persona: PersonaEntity)
    suspend fun delete(persona: PersonaEntity)
}
