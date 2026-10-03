package com.kitsune.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.kitsune.core.data.local.entities.ToneCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToneCardDao {

    @Query("SELECT * FROM tone_cards WHERE personaId = :personaId ORDER BY createdAt ASC")
    fun observeByPersona(personaId: String): Flow<List<ToneCardEntity>>

    @Query("SELECT * FROM tone_cards WHERE personaId = :personaId ORDER BY createdAt ASC")
    suspend fun getByPersona(personaId: String): List<ToneCardEntity>

    @Query("SELECT * FROM tone_cards WHERE universeId = :universeId ORDER BY createdAt ASC")
    fun observeByUniverse(universeId: String): Flow<List<ToneCardEntity>>

    @Query("SELECT * FROM tone_cards WHERE universeId = :universeId ORDER BY createdAt ASC")
    suspend fun getByUniverse(universeId: String): List<ToneCardEntity>

    /**
     * The user's own library: tones that belong to no persona and no universe (2026-08-24).
     *
     * Both foreign keys null is a third, deliberate scope rather than an unset row — "this is how I
     * like stories told, whoever I am talking to". It is what stops a player from re-authoring the
     * same register on every character they own.
     */
    @Query("SELECT * FROM tone_cards WHERE personaId IS NULL AND universeId IS NULL ORDER BY createdAt ASC")
    fun observeProfileCards(): Flow<List<ToneCardEntity>>

    @Query("SELECT * FROM tone_cards WHERE personaId IS NULL AND universeId IS NULL ORDER BY createdAt ASC")
    suspend fun getProfileCards(): List<ToneCardEntity>

    @Query("SELECT * FROM tone_cards WHERE id = :id")
    suspend fun getById(id: String): ToneCardEntity?

    @Upsert
    suspend fun upsert(toneCard: ToneCardEntity)

    @Delete
    suspend fun delete(toneCard: ToneCardEntity)
}
