package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "persona_images",
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
data class PersonaImageEntity(
    @PrimaryKey val id: String,
    val personaId: String,
    val imageStoreId: String,
    val description: String,
    val createdAt: Long
)
