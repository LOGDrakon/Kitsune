package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named way of telling stories with **this** character or universe (2026-08-24).
 *
 * ## Why it exists
 *
 * The story card offers six built-in recipes, and they are deliberately generic — they have to work
 * for any persona. But a given character usually deserves more than one *specific* register: the same
 * rival can carry a slow-burn office romance and a biting comedy, and the settings that make each one
 * land are not something a player should re-derive from nine chip rows every time they start a new
 * conversation with her.
 *
 * A tone card freezes one of those registers under a name, on the character sheet itself. It is the
 * style counterpart of [EntrySceneEntity], which does the same thing for the *situation* a story
 * starts in — same scope, same lifecycle, same place in the UI.
 *
 * ## Why the modes are stored rather than referenced
 *
 * The card records the nine mode values outright instead of pointing at a built-in preset id, even
 * though [basePresetId] remembers where it came from. These cards travel: they are exported with a
 * persona and published with it on the marketplace, so they land in installs whose built-in
 * catalogue may have moved on. A card that resolved through an id would silently change meaning — or
 * stop resolving — on the other side. Stored values mean a shared card reads exactly as its author
 * left it.
 *
 * ## Three scopes, not two
 *
 * [personaId] set — a register written for that character. [universeId] set — one for that world.
 * **Both null** — the user's own library (2026-08-24): "this is how I like stories told, whoever I am
 * talking to", offered on every new conversation. That third case is what stops a player from
 * re-authoring the same preferences on every character they own, and it is a deliberate state rather
 * than an unset row. Never both set at once; the schema does not enforce it, callers must.
 *
 * Only persona-scoped cards travel with a published persona. A profile card describes its author's
 * taste, not the character, so it stays on the device that wrote it.
 */
@Entity(
    tableName = "tone_cards",
    foreignKeys = [
        ForeignKey(
            entity = PersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UniverseEntity::class,
            parentColumns = ["id"],
            childColumns = ["universeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personaId"), Index("universeId")]
)
data class ToneCardEntity(
    @PrimaryKey val id: String,
    val personaId: String? = null,
    val universeId: String? = null,
    /** Shown on the story card, e.g. "Rivalité de bureau" — this is what the player picks. */
    val name: String,
    /** One line telling the player what this register feels like. Optional. */
    val description: String = "",
    /**
     * The built-in recipe this card started from.
     *
     * Kept for two reasons that survive the values above being authoritative: the story card can show
     * which family it belongs to, and the decoder profile is read from it — a comedy should still
     * decode hotter than an obsessive register, and asking a player to set numeric penalties would be
     * a worse question than any it answers.
     */
    val basePresetId: String = "",
    val storyPaceMode: StoryPaceMode = StoryPaceMode.DEFAULT,
    val toneMode: ToneMode = ToneMode.DEFAULT,
    val involvementMode: InvolvementMode = InvolvementMode.DEFAULT,
    val narrativeRhythmMode: NarrativeRhythmMode = NarrativeRhythmMode.DEFAULT,
    val universeMode: UniverseExperienceMode = UniverseExperienceMode.DEFAULT,
    val intensityMode: IntensityMode = IntensityMode.DEFAULT,
    val replyLength: ReplyLengthMode = ReplyLengthMode.DEFAULT,
    val narrationBalance: NarrationBalanceMode = NarrationBalanceMode.DEFAULT,
    val voiceMode: VoiceMode = VoiceMode.DEFAULT,
    /**
     * The free-text instruction this register carries, applied to any conversation started from it.
     *
     * Lands in `ChatEntity.customExperienceDirective`, so it outranks every preset — and, like every
     * user text that reaches the style contract, it is re-sent on every turn and sits in the
     * moderation window. See the vocabulary warning in `ChatStyleContract`.
     */
    val directive: String = "",
    val createdAt: Long
)
