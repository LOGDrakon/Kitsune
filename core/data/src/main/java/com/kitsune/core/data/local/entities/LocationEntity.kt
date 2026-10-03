package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "locations",
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
data class LocationEntity(
    @PrimaryKey val id: String,
    val universeId: String,
    val name: String,
    val description: String,
    val type: String,
    val parentLocationId: String?,
    /** Id into `EncryptedImageStore` (core:security) — a single regenerable illustration, never a
     * raw filesystem path. */
    val avatarImageId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
