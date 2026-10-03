package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "universes",
    indices = [Index("name"), Index("sourceListingId")]
)
data class UniverseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val genre: String,
    val visualStyle: String?,
    /** Free-form descriptive tags (e.g. "dark-fantasy", "political-intrigue") — user-editable,
     * optionally AI-suggested at creation time (see `GenerateWorldElementUseCase`). Also sent to
     * the marketplace listing's own `tags` field when publishing. */
    val tags: List<String> = emptyList(),
    /** Id into `EncryptedImageStore` (core:security) — cover image used as the marketplace avatar
     * and, in future, for ensemble/group scenes. Never a raw filesystem path. */
    val avatarImageId: String? = null,
    /** Marketplace listing id this universe was downloaded from, if any — lets the marketplace
     * detect it's already in the user's collection and avoid creating a duplicate on re-download. */
    val sourceListingId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
