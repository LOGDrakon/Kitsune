package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Images generated in an ensemble/universe chat that aren't attributed to one specific persona
 * (e.g. group scenes) — a gallery scoped to the whole universe rather than to any one persona. */
@Entity(
    tableName = "universe_images",
    foreignKeys = [
        ForeignKey(
            entity = UniverseEntity::class,
            parentColumns = ["id"],
            childColumns = ["universeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("universeId")]
)
data class UniverseImageEntity(
    @PrimaryKey val id: String,
    val universeId: String,
    val imageStoreId: String,
    val description: String,
    val createdAt: Long
)
