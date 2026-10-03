package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "entry_scenes",
    foreignKeys = [
        ForeignKey(
            entity = PersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personaId")]
)
data class EntrySceneEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val title: String,
    val scenario: String,
    val firstMessage: String,
    val createdAt: Long
)
