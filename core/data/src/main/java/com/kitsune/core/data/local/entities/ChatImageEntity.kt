package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Every image generated within a chat (whether attributed to a specific persona or not) — a
 * catch-all gallery scoped to the conversation itself, distinct from [PersonaImageEntity]. */
@Entity(
    tableName = "chat_images",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId")]
)
data class ChatImageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val imageStoreId: String,
    val description: String,
    val createdAt: Long
)
