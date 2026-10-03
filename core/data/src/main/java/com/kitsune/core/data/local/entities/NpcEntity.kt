package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "npcs",
    foreignKeys = [
        ForeignKey(
            entity = UniverseEntity::class,
            parentColumns = ["id"],
            childColumns = ["universeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["factionId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("universeId"), Index("factionId"), Index("locationId"), Index("name")]
)
data class NpcEntity(
    @PrimaryKey val id: String,
    val universeId: String,
    val name: String,
    val description: String,
    val personality: String,
    val role: String,
    val factionId: String?,
    val locationId: String?,
    val age: Int?,
    /** Short appearance description (build, clothing style, distinguishing features — never age,
     * see the generation prompt) invented alongside the rest of the sheet. Reused as image-generation
     * context so this character looks the same across separate generations, mirroring
     * [com.kitsune.core.data.local.entities.PersonaEntity.visualSheetJson]'s purpose for personas. */
    val physicalDescription: String? = null,
    /** Id into `EncryptedImageStore` (core:security) — a single regenerable portrait, never a raw
     * filesystem path. Unlike personas/universes there is no dedicated gallery for NPCs. */
    val avatarImageId: String? = null,
    /** How much the story has actually made use of this character so far — seeded from, and kept in
     * step with, [com.kitsune.core.data.local.entities.LoreEntryEntity.version] by
     * [com.kitsune.core.memory.lore.SyncCastFromLoreUseCase] every time this character is re-mentioned.
     * Used to sort NPC pickers so recurring/important characters surface above one-off background
     * ones (requested by the user), rather than the previous plain alphabetical order. */
    val importance: Int = 1,
    val createdAt: Long,
    val updatedAt: Long
)
