package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "factions",
    foreignKeys = [
        ForeignKey(
            entity = UniverseEntity::class,
            parentColumns = ["id"],
            childColumns = ["universeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("universeId"), Index("name")]
)
data class FactionEntity(
    @PrimaryKey val id: String,
    val universeId: String,
    val name: String,
    val description: String,
    val type: String,
    val alignment: String?,
    val createdAt: Long,
    val updatedAt: Long
)
