package com.kitsune.core.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ChatMode {
    CHAT,
    ROMAN
}

@Entity(
    tableName = "chats",
    foreignKeys = [
        ForeignKey(
            entity = UniverseEntity::class,
            parentColumns = ["id"],
            childColumns = ["universeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            // A chat only exists to talk to its persona; deleting the persona deletes the
            // conversation (and its messages cascade further via MessageEntity's own FK).
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("universeId"), Index("personaId")]
)
data class ChatEntity(
    @PrimaryKey val id: String,
    val universeId: String?,
    val personaId: String?,
    val title: String,
    val mode: ChatMode,
    val archived: Boolean = false,
    /** Rolling summary of the **current** chapter only. Everything older lives in frozen
     *  `story_chapters` rows; this is reset to empty each time a chapter is closed. */
    val summary: String = "",
    val summarizedThroughCreatedAt: Long = 0L,
    /** How far the *derived* steps (lore extraction, cast sync, semantic indexing, key moments)
     *  have actually succeeded — deliberately separate from [summarizedThroughCreatedAt].
     *
     *  BUG-015: the two used to be the same cursor, so it advanced the moment the summary was
     *  written and any derived step that failed afterwards lost its batch permanently, with no way
     *  to notice or retry. Splitting them lets a failed batch be replayed on the next trigger while
     *  the summary itself still moves forward. [derivedRetryCount] bounds that replay so a batch
     *  that fails for a structural reason cannot retry forever. */
    val derivedThroughCreatedAt: Long = 0L,
    val derivedRetryCount: Int = 0,
    /** Free-text current point in the story's internal timeline (date, season, time of day,
     *  elapsed time since a landmark event) — kept as free prose, not a typed date, because
     *  many in-fiction settings have no real calendar. Updated either by
     *  ExtractLoreEntriesUseCase's batch extraction, or directly by the manual "Time Skip"
     *  director tool. Empty until the story establishes any time signal. */
    val storyTimeAnchor: String = "",
    val backgroundImageId: String? = null,
    val selectedSceneId: String? = null,
    val branchedFromMessageId: String? = null,
    val hasSeenBriefing: Boolean = false,
    val lastChunkIndexedAt: Long = 0L,
    /** "Modes d'expérience": per-chat narrative directives rendered by `ChatStyleContract` and sent
     * as the **last turn** before generation, not in the system prompt (they were moved there
     * because at index 0 they lost against 40-60 transcript messages showing the previous style).
     * Each defaults to DEFAULT (no directive injected). */
    val storyPaceMode: StoryPaceMode = StoryPaceMode.DEFAULT,
    val toneMode: ToneMode = ToneMode.DEFAULT,
    val involvementMode: InvolvementMode = InvolvementMode.DEFAULT,
    val narrativeRhythmMode: NarrativeRhythmMode = NarrativeRhythmMode.DEFAULT,
    val universeMode: UniverseExperienceMode = UniverseExperienceMode.DEFAULT,
    val intensityMode: IntensityMode = IntensityMode.DEFAULT,
    /** Free-form "describe exactly what you want" instruction for this conversation, typed in the
     * same dialog as the modes above. The six enums only cover a fixed space of intentions; this
     * covers everything else ("stay cold until he forgives her", "first person, past tense",
     * "never describe clothing"). Outranks every preset — see `ChatStyleContract`. Empty by default. */
    val customExperienceDirective: String = "",
    /** Reply shape (2026-08-23). The three settings a reader notices before any of the six modes
     *  above: how long a reply runs, how much of it is spoken, and in which person/tense it is
     *  written. [replyLength] additionally drives the real `max_tokens`, not just the prose. */
    val replyLength: ReplyLengthMode = ReplyLengthMode.DEFAULT,
    val narrationBalance: NarrationBalanceMode = NarrationBalanceMode.DEFAULT,
    val voiceMode: VoiceMode = VoiceMode.DEFAULT,
    /**
     * The writing style pack applied to **this** conversation (2026-08-23).
     *
     * Style packs used to be a single app-wide setting (`SecureStorage.KEY_APPLIED_STYLE_PACK`), so
     * every story shared one register and the bundle owner got all four packs concatenated at once.
     * Per-chat is what the product always implied: a Visual Novel story and a stage-play story side
     * by side. Empty means "fall back to the globally applied pack", which keeps every existing
     * conversation behaving exactly as before this field existed.
     */
    val stylePackId: String = "",
    /**
     * The story-card preset this conversation was started from, e.g. `slow_romance`.
     *
     * The nine mode fields above are the source of truth for what the model is told — this only
     * records *which recipe* produced them, which buys two things the modes cannot: the card can show
     * the active preset when reopened, and the preset's [SamplingProfile] can be recovered to decode
     * with. Empty for conversations created before the card existed, which then decode exactly as
     * they always have.
     */
    val storyPresetId: String = "",
    /**
     * Whether the story card (framing screen) has already been shown for this conversation.
     *
     * Not a UI-only flag: it is what guarantees a chat is never left with an empty style contract.
     * Chats created before this field existed default to `false` and will be offered the card once.
     */
    val hasSeenStoryCard: Boolean = false,
    /**
     * Cover art for this story, in [com.kitsune.core.security.storage.EncryptedImageStore] (2026-08-25).
     *
     * The cover already existed — `GenerateNovelCoverUseCase` draws one from the story's title,
     * rolling summary and each cast member's visual sheet — but it was produced **inside the PDF
     * export and thrown away afterwards**, so the app generated a book cover for every exported story
     * and never showed one anywhere else. Persisting it is what lets the home screen be a shelf of
     * stories rather than a list of chats.
     *
     * Null until the story has enough material to be worth drawing: it is generated when the first
     * chapter closes, not at creation, and `NOVEL_COVER` is already a free operation type.
     */
    val coverImageId: String? = null,
    /**
     * When the user last opened this story, as opposed to [updatedAt] which also moves when the
     * background pipeline writes.
     *
     * Needed because "something happened while you were away" has to be answerable per story, and
     * `updatedAt` cannot answer it: a summarization pass touches it without anyone having read
     * anything. `0L` for stories that predate the field, which simply means "never visited since we
     * started counting" — the shelf treats that as no badge rather than as a stale one.
     */
    val lastVisitedAt: Long = 0L,
    /**
     * This story's own model and memory/length settings (2026-10-04). Null means "use the global
     * setting" (Settings → Modèles, Settings → Mémoire et longueur), so a short story can run on a
     * small local model while a saga uses a large-context one.
     */
    val chatModelRef: String? = null,
    val memoryRawWindow: Int? = null,
    val memoryLoreEntries: Int? = null,
    val maxReplyTokens: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)
