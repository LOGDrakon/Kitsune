package com.kitsune.core.data.repository

import com.kitsune.core.data.local.entities.LoreEntryEntity
import kotlinx.coroutines.flow.Flow

/** Memory level 3 storage (FEATURES.md section 4: "fiches structurées d'entités persistantes"). */
interface LoreEntryRepository {
    fun observeByChat(chatId: String): Flow<List<LoreEntryEntity>>
    suspend fun getByChat(chatId: String): List<LoreEntryEntity>
    fun observeByUniverse(universeId: String): Flow<List<LoreEntryEntity>>
    suspend fun getByUniverse(universeId: String): List<LoreEntryEntity>
    /** Lore scoped to a character rather than to one story or one world. */
    suspend fun getByPersona(personaId: String): List<LoreEntryEntity>
    suspend fun findByChatAndName(chatId: String, name: String): LoreEntryEntity?
    /** Like [findByChatAndName] but also matches recorded aliases — see [LoreEntryEntity.aliases]. */
    suspend fun findByChatAndNameOrAlias(chatId: String, name: String): LoreEntryEntity?
    suspend fun upsert(entry: LoreEntryEntity)
    suspend fun delete(entry: LoreEntryEntity)
}
