package com.kitsune.core.data.local.converters

import androidx.room.TypeConverter
import com.kitsune.core.data.local.entities.ChatMode
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.GenerationJobType
import com.kitsune.core.data.local.entities.IntensityMode
import com.kitsune.core.data.local.entities.InvolvementMode
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.NarrationBalanceMode
import com.kitsune.core.data.local.entities.ReplyLengthMode
import com.kitsune.core.data.local.entities.VoiceMode
import com.kitsune.core.data.local.entities.ModerationFlag
import com.kitsune.core.data.local.entities.NarrativeRhythmMode
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.core.data.local.entities.StoryPaceMode
import com.kitsune.core.data.local.entities.ToneMode
import com.kitsune.core.data.local.entities.UniverseExperienceMode

class Converters {

    private inline fun <reified T : Enum<T>> safeValueOf(value: String): T =
        try {
            enumValueOf<T>(value)
        } catch (e: IllegalArgumentException) {
            enumValues<T>().first()
        }

    @TypeConverter
    fun fromMaturityTags(tags: List<MaturityTag>): String = tags.joinToString(",") { it.name }

    @TypeConverter
    fun toMaturityTags(value: String): List<MaturityTag> =
        if (value.isBlank()) emptyList() else value.split(",").map { safeValueOf<MaturityTag>(it) }

    /** Free-form persona/universe tags (see `PersonaEntity.tags`/`UniverseEntity.tags`), comma-joined
     * like [fromMaturityTags] — tags are sanitized on write so a stray comma in user input can't
     * corrupt the delimiter. */
    @TypeConverter
    fun fromTags(tags: List<String>): String = tags.joinToString(",") { it.replace(",", "").trim() }

    @TypeConverter
    fun toTags(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.trim() }.filter { it.isNotBlank() }

    @TypeConverter
    fun fromChatMode(mode: ChatMode): String = mode.name

    @TypeConverter
    fun toChatMode(value: String): ChatMode = safeValueOf(value)

    @TypeConverter
    fun fromMessageRole(role: MessageRole): String = role.name

    @TypeConverter
    fun toMessageRole(value: String): MessageRole = safeValueOf(value)

    @TypeConverter
    fun fromModerationFlag(flag: ModerationFlag): String = flag.name

    @TypeConverter
    fun toModerationFlag(value: String): ModerationFlag = safeValueOf(value)

    @TypeConverter
    fun fromLoreEntryType(type: LoreEntryType): String = type.name

    @TypeConverter
    fun toLoreEntryType(value: String): LoreEntryType = safeValueOf(value)

    @TypeConverter
    fun fromMemoryFragmentSource(source: MemoryFragmentSource): String = source.name

    @TypeConverter
    fun toMemoryFragmentSource(value: String): MemoryFragmentSource = safeValueOf(value)

    /** Memory level 4 embedding vector (see [com.kitsune.core.data.local.entities.MemoryFragmentEntity]), comma-separated. */
    @TypeConverter
    fun fromEmbedding(embedding: List<Float>): String = embedding.joinToString(",")

    @TypeConverter
    fun toEmbedding(value: String): List<Float> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.toFloatOrNull() ?: 0f }

    @TypeConverter
    fun fromParticipantType(type: ParticipantType): String = type.name

    @TypeConverter
    fun toParticipantType(value: String): ParticipantType = safeValueOf(value)

    @TypeConverter
    fun fromGenerationJobType(type: GenerationJobType): String = type.name

    @TypeConverter
    fun toGenerationJobType(value: String): GenerationJobType = safeValueOf(value)

    @TypeConverter
    fun fromGenerationJobState(state: GenerationJobState): String = state.name

    @TypeConverter
    fun toGenerationJobState(value: String): GenerationJobState = safeValueOf(value)

    @TypeConverter
    fun fromStoryPaceMode(mode: StoryPaceMode): String = mode.name

    @TypeConverter
    fun toStoryPaceMode(value: String): StoryPaceMode = safeValueOf(value)

    @TypeConverter
    fun fromToneMode(mode: ToneMode): String = mode.name

    @TypeConverter
    fun toToneMode(value: String): ToneMode = safeValueOf(value)

    @TypeConverter
    fun fromInvolvementMode(mode: InvolvementMode): String = mode.name

    @TypeConverter
    fun toInvolvementMode(value: String): InvolvementMode = safeValueOf(value)

    @TypeConverter
    fun fromNarrativeRhythmMode(mode: NarrativeRhythmMode): String = mode.name

    @TypeConverter
    fun toNarrativeRhythmMode(value: String): NarrativeRhythmMode = safeValueOf(value)

    @TypeConverter
    fun fromUniverseExperienceMode(mode: UniverseExperienceMode): String = mode.name

    @TypeConverter
    fun toUniverseExperienceMode(value: String): UniverseExperienceMode = safeValueOf(value)

    @TypeConverter
    fun fromIntensityMode(mode: IntensityMode): String = mode.name

    @TypeConverter
    fun toIntensityMode(value: String): IntensityMode = safeValueOf(value)

    @TypeConverter
    fun fromReplyLengthMode(mode: ReplyLengthMode): String = mode.name

    @TypeConverter
    fun toReplyLengthMode(value: String): ReplyLengthMode = safeValueOf(value)

    @TypeConverter
    fun fromNarrationBalanceMode(mode: NarrationBalanceMode): String = mode.name

    @TypeConverter
    fun toNarrationBalanceMode(value: String): NarrationBalanceMode = safeValueOf(value)

    @TypeConverter
    fun fromVoiceMode(mode: VoiceMode): String = mode.name

    @TypeConverter
    fun toVoiceMode(value: String): VoiceMode = safeValueOf(value)
}
