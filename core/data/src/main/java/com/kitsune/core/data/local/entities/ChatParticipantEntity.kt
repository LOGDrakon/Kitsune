package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A cast member can be a full persona or a lighter-weight universe NPC — resolved against
 * whichever table [ChatParticipantEntity.participantId] actually refers to. */
enum class ParticipantType {
    PERSONA,
    NPC
}

/**
 * Ensemble/universe chats (FEATURES.md section 6) have no single protagonist persona — instead a
 * cast of personas and/or NPCs, all played by the AI in the same shared scene. A chat with
 * `personaId == null` and `universeId != null` is an ensemble chat; its cast lives here rather
 * than as a single FK column, since the cast size is unbounded and mixes two source tables.
 *
 * [participantId] is not FK-enforced (Room can't express a foreign key that conditionally points
 * at `personas` or `npcs` depending on [participantType]) — resolved manually by the repository.
 */
@Entity(
    tableName = "chat_participants",
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
data class ChatParticipantEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val participantType: ParticipantType,
    val participantId: String,
    val createdAt: Long
)
