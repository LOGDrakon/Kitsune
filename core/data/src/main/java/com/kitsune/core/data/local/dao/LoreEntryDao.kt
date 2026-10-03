package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.LoreEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LoreEntryDao {

    @Query("SELECT * FROM lore_entries WHERE universeId = :universeId ORDER BY name ASC")
    fun observeByUniverse(universeId: String): Flow<List<LoreEntryEntity>>

    @Query("SELECT * FROM lore_entries WHERE universeId = :universeId ORDER BY name ASC")
    suspend fun getByUniverse(universeId: String): List<LoreEntryEntity>

    /** Lore the character carries into every story they appear in — see `LoreEntryEntity.personaId`. */
    @Query("SELECT * FROM lore_entries WHERE personaId = :personaId ORDER BY createdAt ASC")
    suspend fun getByPersona(personaId: String): List<LoreEntryEntity>

    /** Level-3 lore roster for a single story (FEATURES.md section 4), most recently updated first. */
    @Query("SELECT * FROM lore_entries WHERE chatId = :chatId ORDER BY updatedAt DESC")
    fun observeByChat(chatId: String): Flow<List<LoreEntryEntity>>

    @Query("SELECT * FROM lore_entries WHERE chatId = :chatId ORDER BY updatedAt DESC")
    suspend fun getByChat(chatId: String): List<LoreEntryEntity>

    @Query("SELECT * FROM lore_entries WHERE id = :id")
    suspend fun getById(id: String): LoreEntryEntity?

    /** Case-insensitive match used to update an existing entry instead of creating a duplicate. */
    /** Matches the canonical name or any recorded alias, so "la mercenaire" resolves to Aria's
     *  sheet instead of creating a second one. Aliases are stored comma-joined (`Converters.toTags`),
     *  hence the delimiter-padded LIKE. Unindexed, but a chat's roster is tens of rows. */
    @Query(
        """
        SELECT * FROM lore_entries
        WHERE chatId = :chatId
          AND (LOWER(name) = LOWER(:name)
               OR ',' || LOWER(aliases) || ',' LIKE '%,' || LOWER(:name) || ',%')
        LIMIT 1
        """
    )
    suspend fun findByChatAndNameOrAlias(chatId: String, name: String): LoreEntryEntity?

    @Query("SELECT * FROM lore_entries WHERE chatId = :chatId AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun findByChatAndName(chatId: String, name: String): LoreEntryEntity?

    @Upsert
    suspend fun upsert(entry: LoreEntryEntity)

    @Delete
    suspend fun delete(entry: LoreEntryEntity)
}
