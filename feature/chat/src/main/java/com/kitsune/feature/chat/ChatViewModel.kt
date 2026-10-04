package com.kitsune.feature.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatParticipantEntity
import com.kitsune.core.data.local.entities.IntensityMode
import com.kitsune.core.data.local.entities.InvolvementMode
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.storyContentOnly
import com.kitsune.feature.chat.style.NarrativeState
import com.kitsune.feature.chat.style.NarrativeThresholds
import com.kitsune.core.common.memory.MemorySettingsHolder
import com.kitsune.core.data.local.entities.NarrationBalanceMode
import com.kitsune.core.data.local.entities.VoiceMode
import com.kitsune.core.data.local.entities.ReplyLengthMode
import com.kitsune.core.network.repository.SamplingProfile
import com.kitsune.core.network.storycard.GenerateStoryCardUseCase
import com.kitsune.feature.chat.style.StoryPreset
import com.kitsune.core.memory.recap.GenerateWorldBeatUseCase
import com.kitsune.feature.chat.novel.EnsureStoryCoverUseCase
import com.kitsune.feature.chat.style.StoryCardPresets
import com.kitsune.feature.chat.style.StyleSettings
import com.kitsune.feature.chat.style.buildStyleContract
import com.kitsune.feature.chat.style.buildBeatDirective
import com.kitsune.feature.chat.style.buildStylePivot
import com.kitsune.feature.chat.style.decideBeat
import com.kitsune.core.data.local.entities.NarrativeRhythmMode
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.StoryPaceMode
import com.kitsune.core.data.local.entities.ToneCardEntity
import com.kitsune.core.data.local.entities.ToneMode
import com.kitsune.core.data.local.entities.UniverseExperienceMode
import com.kitsune.core.data.repository.ChatParticipantRepository
import com.kitsune.core.data.repository.ChatRepository
import com.kitsune.core.data.repository.EntrySceneRepository
import com.kitsune.core.data.local.entities.MessageVariantEntity
import com.kitsune.core.data.repository.MessageVariantRepository
import com.kitsune.core.data.repository.ToneCardRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import com.kitsune.core.data.repository.MessageRepository
import com.kitsune.core.data.repository.NpcRepository
import com.kitsune.core.data.local.entities.ChatImageEntity
import com.kitsune.core.data.local.entities.NpcEntity
import com.kitsune.core.data.local.entities.PersonaImageEntity
import com.kitsune.core.data.local.entities.UniverseImageEntity
import com.kitsune.core.data.repository.ChatImageRepository
import com.kitsune.core.data.repository.PersonaImageRepository
import com.kitsune.core.data.repository.PersonaRepository
import com.kitsune.core.data.repository.UniverseImageRepository
import com.kitsune.core.data.repository.UniverseRepository
import com.kitsune.core.memory.rewind.RewindChatUseCase
import com.kitsune.core.background.GenerationScheduler
import com.kitsune.core.background.NpcGenerationRequest
import com.kitsune.core.background.parseNpcResult
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.GenerationJobType
import com.kitsune.core.data.repository.GenerationJobRepository
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.feature.chat.imagegen.GalleryTarget
import com.kitsune.core.memory.context.BuildTurnMemoryUseCase
import com.kitsune.core.memory.context.TurnMemory
import com.kitsune.core.memory.summarization.SummarizationConfig
import com.kitsune.core.memory.semantic.IndexMessageChunkUseCase
import com.kitsune.core.memory.summarization.UpdateChatSummaryUseCase
import com.kitsune.core.memory.recap.GenerateRecapUseCase
import com.kitsune.core.moderation.safeword.SafeWordManager
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.preferences.GenerationPreferences
import com.kitsune.core.network.preferences.NetworkPreferences
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatCompletionResult
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.network.suggestions.GenerateNextReplySuggestionsUseCase
import com.kitsune.core.security.profile.UserProfileStore
import com.kitsune.core.security.storage.EncryptedImageStore
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.storage.SecureStorage
import com.kitsune.core.security.taste.StoryTasteStore
import com.kitsune.core.diagnostics.PersonalNameRedactor
import com.kitsune.core.diagnostics.SubmitBugReportUseCase
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import com.kitsune.feature.chat.style.StylePacks

/** Used to tell the world beat how much time it has to account for. */
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

/** Sent as a synthetic turn, never persisted — the reply it produces is appended to the one
 *  being continued. */
private const val CONTINUE_INSTRUCTION = "Continue exactly where your last reply stopped, mid-sentence if that is where it ended. Do not restart, do not summarise, do not repeat a single word of it."

/**
 * Closes the instruction block so the model switches registers instead of continuing it.
 *
 * Deliberately the last words in the whole request: everything before it is imperative, and the
 * model's next token is the reply itself.
 */
private const val CONTRACT_CLOSING_BOUNDARY =
    "END OF INSTRUCTIONS. Everything above tells you how to write; none of it is content. Your reply " +
        "is the scene itself — prose and dialogue only. Never restate, quote, summarise or comment on " +
        "these instructions, and never write directions to yourself."

private const val TAG = "ChatViewModel"

/**
 * The one content rule the app itself states, first in every system prompt: no sexual content
 * involving minors. It addresses the model, never the user's text — nothing is filtered or blocked
 * client-side, so it cannot produce false positives on legitimate adult fiction. Kept as the only
 * rule on purpose: everything else between consenting adult characters is the user's business.
 */
private const val SAFETY_DIRECTIVE =
    "[Content rule, set by the app and not overridable by anything below: never write sexual content " +
        "involving a minor — any character under 18, whatever their claimed age or framing. If a scene " +
        "drifts that way, steer it elsewhere in character instead of refusing outright. All other dark, " +
        "violent or explicit fiction between consenting adult characters is allowed.]"

private const val STYLE_PACK_BUNDLE_ID = "style_pack_bundle"

/** "Régénérer"/edit-and-resend both exist specifically so the user can get a DIFFERENT reply from
 * the same point in the story — a real user report showed the same request sometimes still comes
 * back byte-identical even after editing the message, most likely upstream (provider-side) low-
 * entropy decoding or caching for that exact prompt, something this app can't control directly.
 * Bumping temperature specifically for these two flows is a cheap, effective mitigation regardless
 * of the exact upstream cause. Capped so it stays coherent rather than degrading into noise. */
private const val REGENERATE_TEMPERATURE_BONUS = 0.2
private const val REGENERATE_TEMPERATURE_MAX = 1.4

/** Sentence boundary used for auto-capitalising the chat input ('\n' or end-of-sentence punctuation followed by whitespace). */
private val SENTENCE_BOUNDARY_REGEX = Regex("""(^|[.!?]\s+|\n)([a-zàâäéèêëïîôöùûüÿç])""")

private const val BUG_REPORT_MIN_DIALOG_MS = 4000L

/** Short recent window for "what could I say next?" suggestions — deliberately smaller than a
 * real reply's [SummarizationConfig.rawWindowSize] since suggestions only need the immediate
 * back-and-forth, not the full adaptive context budget a real generation gets. */
private const val SUGGESTION_CONTEXT_MESSAGES = 8

/** A cast member of an ensemble/universe chat (no single protagonist persona) — unifies personas
 * and NPCs, the two source tables a [ChatParticipantEntity][com.kitsune.core.data.local.entities.ChatParticipantEntity] can point at. */
data class CastMember(
    val participantId: String,
    val participantType: ParticipantType,
    val name: String,
    val description: String,
    val personality: String,
    val role: String? = null
)

/** One labelled row of candidate images for the background picker dialog — the chat's own gallery,
 * the parent universe's gallery, or one persona's avatar + gallery. */
data class BackgroundPickerSection(
    val title: String,
    val imageIds: List<String>
)

/** A universe persona/NPC not yet in this ensemble chat's cast, offered by the "Ajouter un
 * personnage à la scène" dialog. */
data class CastCandidateOption(
    val type: ParticipantType,
    val id: String,
    val name: String,
    val subtitle: String
)

/**
 * How a newly-added cast member is folded into the story (requested explicitly by the user, who
 * wanted to avoid a forced "X just arrived in town" narration): [RETROACTIVE] adds them silently
 * (an out-of-character note only, the character was simply already there and the story just
 * hadn't brought them up yet), [ARRIVAL] has the AI narrate an actual in-fiction entrance.
 */
enum class ParticipantJoinFraming { RETROACTIVE, ARRIVAL }

enum class DirectorToolKind {
    SCENE_CHANGE,
    PLOT_TWIST,
    TIME_SKIP,
    RANDOM_INTERLUDE
}


/** Captures a failed LLM operation so the user can retry it. */
sealed interface PendingChatRetry {
    data class Send(val text: String) : PendingChatRetry
    data class Edit(val messageId: String, val text: String) : PendingChatRetry
    data object Regenerate : PendingChatRetry
    data object OpeningScene : PendingChatRetry
    data class Arrival(val names: List<String>) : PendingChatRetry
    data class DirectorBeat(val kind: DirectorToolKind) : PendingChatRetry
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val personaRepository: PersonaRepository,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val chatCompletionRepository: ChatCompletionRepository,
    private val networkPreferences: NetworkPreferences,
    private val generationPreferences: GenerationPreferences,
    private val llmModelResolver: LlmModelResolver,
    private val safeWordManager: SafeWordManager,
    private val updateChatSummaryUseCase: UpdateChatSummaryUseCase,
    private val indexMessageChunkUseCase: IndexMessageChunkUseCase,
    private val userProfileStore: UserProfileStore,
    private val encryptedImageStore: EncryptedImageStore,
    private val submitBugReportUseCase: SubmitBugReportUseCase,
    private val loreEntryRepository: LoreEntryRepository,
    private val entrySceneRepository: EntrySceneRepository,
    private val toneCardRepository: ToneCardRepository,
    private val buildTurnMemoryUseCase: BuildTurnMemoryUseCase,
    private val personaImageRepository: PersonaImageRepository,
    private val chatImageRepository: ChatImageRepository,
    private val universeImageRepository: UniverseImageRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val npcRepository: NpcRepository,
    private val universeRepository: UniverseRepository,
    private val rewindChatUseCase: RewindChatUseCase,
    private val generationScheduler: GenerationScheduler,
    private val generationJobRepository: GenerationJobRepository,
    private val generateRecapUseCase: GenerateRecapUseCase,
    private val secureStorage: SecureStorage,
    private val storyTasteStore: StoryTasteStore,
    private val messageVariantRepository: MessageVariantRepository,
    private val generateStoryCardUseCase: GenerateStoryCardUseCase,
    private val ensureStoryCoverUseCase: EnsureStoryCoverUseCase,
    private val generateWorldBeatUseCase: GenerateWorldBeatUseCase,
    private val appLanguageManager: AppLanguageManager,
    private val generateNextReplySuggestionsUseCase: GenerateNextReplySuggestionsUseCase
) : ViewModel() {

    private var stylePackPrompt: String? = null

    private val chatId: String = checkNotNull(savedStateHandle["chatId"])

    private val _chat = MutableStateFlow<ChatEntity?>(null)
    val chat: StateFlow<ChatEntity?> = _chat.asStateFlow()

    /** True while a bug report is being redacted, encrypted and sent — see [generateBugReport]. */
    private val _bugReportSending = MutableStateFlow(false)
    val bugReportSending: StateFlow<Boolean> = _bugReportSending.asStateFlow()

    /**
     * The base temperature for this conversation, before any bonus.
     *
     * A story-card preset states one when its register depends on it — comedy wants the model
     * reaching for the unexpected word, a controlled psychological piece does not. When the chat has
     * no preset (every conversation created before the card existed), this is the user's own
     * Settings value exactly as before.
     */
    private fun baseTemperature(): Double {
        val preset = _chat.value?.let { StoryCardPresets.byId(it.storyPresetId) }
        return preset?.sampling?.temperature ?: networkPreferences.getDefaultTemperature()
    }

    private fun effectiveTemperature(): Double = baseTemperature()

    /** See [REGENERATE_TEMPERATURE_BONUS] — used by [regenerateLastResponse] and
     *  [updateEditedMessage], both existing specifically to get a different reply than last time. */
    private fun regenerateTemperature(): Double =
        (effectiveTemperature() + REGENERATE_TEMPERATURE_BONUS).coerceAtMost(REGENERATE_TEMPERATURE_MAX)

    /** Routes a failed LLM call to the error banner with a same-model retry option. Provider
     * errors (bad key, no credit left at the provider, rate limit) get an actionable message from
     * [NetworkErrorMessages]. */
    private fun handleChatFailure(e: Throwable, retry: PendingChatRetry) {
        _error.value = NetworkErrorMessages.forUser(e)
        _pendingRetry.value = retry
    }

    /** Sends a bug report built from the user-provided [subject]/[description] (FEATURES.md
     * section 9). [attachConversation] controls whether the **full** transcript is included — the
     * user decides explicitly rather than it being attached automatically. [focusMessageId]/
     * [flaggedContent] instead attach only a targeted "Contenu d'intérêt" excerpt + a small window
     * of context (demande explicite : "isole le contenu inapproprié et son contexte... pas
     * obligatoire d'avoir accès à toute la conversation") — see [reportMessage]/
     * [reportContentPolicyViolation], the two callers that actually set them; the generic
     * "signaler un problème" flow leaves both null. `chatId` is always passed to
     * [submitBugReportUseCase] now (needed to look up context for the targeted excerpt regardless
     * of whether the full transcript is attached) — [attachFullConversation] is what actually
     * gates the full transcript, matching [attachConversation] when no targeted excerpt is set.
     * Keeps [_bugReportSending] visible for at least [BUG_REPORT_MIN_DIALOG_MS] regardless of how
     * fast the network call completes, so the privacy reassurance dialog actually gets read. */
    fun generateBugReport(
        subject: String,
        description: String,
        attachConversation: Boolean,
        focusMessageId: String? = null
    ) {
        if (_bugReportSending.value) return
        viewModelScope.launch {
            _bugReportSending.value = true
            val minDisplay = launch { delay(BUG_REPORT_MIN_DIALOG_MS) }
            submitBugReportUseCase(
                subject = subject,
                description = description,
                chatId = chatId,
                focusMessageId = focusMessageId,
                attachFullConversation = attachConversation && focusMessageId == null
            )
            minDisplay.join()
            _bugReportSending.value = false
        }
    }

    /** "Signaler ce message" (per-message long-press action, `ChatScreen`'s `MessageBubble`) —
     * attaches just that message + a small window of context, not the whole conversation. */
    fun reportMessage(messageId: String, subject: String, description: String) {
        generateBugReport(subject, description, attachConversation = false, focusMessageId = messageId)
    }

    /** Redacts [text] with the user's own first/last name, using the exact same logic
     * ([PersonalNameRedactor]) that [SubmitBugReportUseCase] applies when it actually builds the
     * report — so the preview `ChatScreen` shows before the user confirms a filter-block report is
     * guaranteed to match what gets sent. */
    fun redactedPreview(text: String): String {
        val profile = userProfileStore.get()
        return PersonalNameRedactor.redact(text, profile.firstName, profile.lastName)
    }

    fun dismissError() {
        _error.value = null
        _pendingRetry.value = null
    }

    /** Retries the last failed operation. */
    fun retrySameModel() {
        val retry = _pendingRetry.value ?: return
        _pendingRetry.value = null
        _error.value = null
        when (retry) {
            is PendingChatRetry.Send -> sendMessageWithText(retry.text)
            is PendingChatRetry.Edit -> viewModelScope.launch {
                val original = messageRepository.getById(retry.messageId)
                if (original != null) {
                    _editingMessage.value = original
                    _inputText.value = TextFieldValue(
                        text = retry.text,
                        selection = TextRange(retry.text.length)
                    )
                    updateEditedMessage()
                }
            }
            PendingChatRetry.Regenerate -> regenerateLastResponse()
            PendingChatRetry.OpeningScene -> generateOpeningScene()
            is PendingChatRetry.Arrival -> viewModelScope.launch { generateArrivalBeat(retry.names) }
            is PendingChatRetry.DirectorBeat -> injectDirectorBeat(retry.kind)
        }
    }

    private val _persona = MutableStateFlow<PersonaEntity?>(null)
    val persona: StateFlow<PersonaEntity?> = _persona.asStateFlow()

    /** Non-empty only for ensemble/universe chats (`chat.personaId == null`) — see [buildSystemPrompt]. */
    private val _castMembers = MutableStateFlow<List<CastMember>>(emptyList())
    val castMembers: StateFlow<List<CastMember>> = _castMembers.asStateFlow()

    /** Personas an in-chat image can be filed under (long-press on a message image, "Ajouter à la
     * galerie") — the one protagonist for a regular chat, or every persona in the cast for an
     * ensemble chat (NPCs excluded, they have no gallery of their own). */
    private val _galleryTargets = MutableStateFlow<List<GalleryTarget>>(emptyList())
    val galleryTargets: StateFlow<List<GalleryTarget>> = _galleryTargets.asStateFlow()

    private var _activeScenario: String? = null

    private val _avatarBytes = MutableStateFlow<ByteArray?>(null)
    val avatarBytes: StateFlow<ByteArray?> = _avatarBytes.asStateFlow()

    private val _backgroundBytes = MutableStateFlow<ByteArray?>(null)
    val backgroundBytes: StateFlow<ByteArray?> = _backgroundBytes.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    /** Chat-scoped lore entries shown in the pre-game briefing for ensemble/universe chats. */
    private val _briefingLoreEntries = MutableStateFlow<List<LoreEntryEntity>>(emptyList())
    val briefingLoreEntries: StateFlow<List<LoreEntryEntity>> = _briefingLoreEntries.asStateFlow()

    private val messageFlow = messageRepository.observeByChat(chatId)

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Captures the last failed LLM operation so the user can retry with the same model. */
    private val _pendingRetry = MutableStateFlow<PendingChatRetry?>(null)
    val pendingRetry: StateFlow<PendingChatRetry?> = _pendingRetry.asStateFlow()

    /** "What could I say next?" reply-suggestion chips, shown above the input field on demand
     * only — never fetched automatically after every AI turn (see BUG-070: an unconditional extra
     * LLM call on every single turn already caused a production incident once, for moderation; the
     * same cost/latency/reliability caution applies to any other per-turn AI call). Cleared as soon
     * as the user types or sends anything, so stale suggestions never linger against a scene that
     * has already moved on. */
    private val _nextReplySuggestions = MutableStateFlow<List<String>>(emptyList())
    val nextReplySuggestions: StateFlow<List<String>> = _nextReplySuggestions.asStateFlow()

    private val _isLoadingNextReplySuggestions = MutableStateFlow(false)
    val isLoadingNextReplySuggestions: StateFlow<Boolean> = _isLoadingNextReplySuggestions.asStateFlow()

    fun loadNextReplySuggestions() {
        if (_isLoadingNextReplySuggestions.value) return
        _isLoadingNextReplySuggestions.value = true
        _nextReplySuggestions.value = emptyList()
        viewModelScope.launch {
            val rawWindow = messageRepository.getRecent(chatId, SUGGESTION_CONTEXT_MESSAGES)
            // No style contract here: this generates suggestions for what the *user* might say
            // next, so directives about how the character should write would be actively wrong.
            val history = buildApiHistory(rawWindow)
            generateNextReplySuggestionsUseCase(history)
                .onSuccess { suggestions -> _nextReplySuggestions.value = suggestions }
            _isLoadingNextReplySuggestions.value = false
        }
    }

    fun dismissNextReplySuggestions() {
        _nextReplySuggestions.value = emptyList()
    }

    /** Fills the input with [suggestion] rather than sending it directly — this is creative
     * writing, not a quick-reply chat app, so the user gets a chance to tweak it first. */
    fun useNextReplySuggestion(suggestion: String) {
        _nextReplySuggestions.value = emptyList()
        _inputText.value = TextFieldValue(suggestion, TextRange(suggestion.length))
    }

    private val sendMutex = Mutex()

    private val _inputText = MutableStateFlow(TextFieldValue(""))
    val inputText: StateFlow<TextFieldValue> = _inputText.asStateFlow()

    private val _editingMessage = MutableStateFlow<MessageEntity?>(null)
    val editingMessage: StateFlow<MessageEntity?> = _editingMessage.asStateFlow()

    private fun rawWindowSize(): Int = SummarizationConfig.rawWindowSize()

    init {
        viewModelScope.launch {
            val chat = chatRepository.getById(chatId) ?: return@launch
            _chat.value = chat
            // Loaded for every conversation, persona or ensemble, and before the story card can
            // appear: these are the user's own registers and they apply regardless of who is on the
            // other side.
            _profileToneCards.value = toneCardRepository.getProfileCards()

            // Observer les messages en temps réel. `messageFlow` (Room, ORDER BY createdAt ASC) is kept in
            // ascending order here — ChatScreen is the single place responsible for reversing it to
            // feed a `reverseLayout` LazyColumn (previously both this collector and ChatScreen
            // reversed it, cancelling out and rendering the newest message at the wrong scroll end).
            launch {
                messageFlow.collect { messages ->
                    // Style-pivot markers are model-facing only: they must never appear in the
                    // thread. Filtered here, at the single source the UI reads, rather than at each
                    // rendering site. The generation paths deliberately re-read the unfiltered
                    // messages from the repository (see buildApiHistory).
                    _messages.value = messages.storyContentOnly()
                }
            }

            val personaId = chat.personaId
            if (personaId != null) {
                val persona = personaRepository.getById(personaId) ?: return@launch
                _persona.value = persona
                // Loaded before the story card can appear: it decides what the card offers, and an
                // empty list would silently degrade it to the six generic recipes.
                _toneCards.value = toneCardRepository.getByPersona(personaId)

                val selectedScene = chat.selectedSceneId?.let { entrySceneRepository.getById(it) }
                val firstMessage = selectedScene?.firstMessage?.takeIf { it.isNotBlank() } ?: persona.firstMessage
                _activeScenario = selectedScene?.scenario?.takeIf { it.isNotBlank() }

                // Vérifier s'il y a déjà des messages dans la base avant d'insérer le premier message.
                //
                // Also held back until the story card has been answered (2026-08-23): inserting the
                // canned opening straight away would make `_messages` non-empty, and the card — which
                // deliberately refuses to interrupt a story already in motion — would never appear
                // for a persona chat at all. The card writes its own opening when the player
                // describes their story, so this only runs for the preset and skip paths.
                val existingMessages = messageRepository.getRecent(chatId, 1)
                if (firstMessage.isNotBlank() && existingMessages.isEmpty() && chat.hasSeenStoryCard) {
                    messageRepository.upsert(
                        MessageEntity(
                            id = UUID.randomUUID().toString(),
                            chatId = chatId,
                            role = MessageRole.ASSISTANT,
                            content = firstMessage,
                            imageAttachmentPath = null,
                            tokenCount = null,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    updateChatTimestamp()
                }

                withContext(Dispatchers.IO) {
                    persona.avatarImageId?.let { _avatarBytes.value = encryptedImageStore.load(it) }
                }
                _galleryTargets.value = listOf(GalleryTarget(persona.id, persona.name))
            } else {
                // An ensemble scene has no persona, so its tone cards hang off the universe instead.
                chat.universeId?.let { _toneCards.value = toneCardRepository.getByUniverse(it) }
                // Ensemble/universe chat: no single protagonist, no auto-inserted first message —
                // the user starts the scene. See buildSystemPrompt for how the cast is presented.
                // Reactive rather than a one-shot snapshot: a participant can be added later while
                // this ViewModel is still alive (manually via addCastMember/generateAndAddNpc, or
                // automatically by SyncCastFromLoreUseCase in the background) — collecting keeps
                // both the system-prompt cast list and the gallery targets in sync without needing
                // every call site to remember to refresh them.
                launch {
                    chatParticipantRepository.observeByChat(chatId).collect { participants ->
                        _castMembers.value = resolveCastMembers(participants)
                        _galleryTargets.value = participants
                            .filter { it.participantType == ParticipantType.PERSONA }
                            .mapNotNull { p -> personaRepository.getById(p.participantId)?.let { GalleryTarget(it.id, it.name) } }
                    }
                }
            }

            withContext(Dispatchers.IO) {
                chat.backgroundImageId?.let { _backgroundBytes.value = encryptedImageStore.load(it) }
            }

            launch {
                loreEntryRepository.observeByChat(chatId).collect { entries ->
                    _briefingLoreEntries.value = entries
                }
            }

            launch { observeNpcGenerationJobs() }

            launch {
                maybeShowRecap(chat)
            }

            launch {
                loadStylePackPrompt()
            }

            launch {
                // Marks the visit *before* anything else can move `updatedAt`, so the shelf's
                // "something happened here" badge compares against when the user actually looked,
                // not against the last time the background pipeline wrote a summary.
                chatRepository.getById(chatId)?.let {
                    chatRepository.upsert(it.copy(lastVisitedAt = System.currentTimeMillis()))
                }
                // Safe on every open: it returns immediately unless the story has closed a chapter
                // and still has no cover. That makes it self-healing for the conversations that
                // predate covers entirely.
                ensureStoryCoverUseCase(chatId)
            }
        }
    }

    /**
     * Resolves the one writing style pack that applies to **this** conversation.
     *
     * Two fixes over the previous version, both shipped 2026-08-23:
     *
     * 1. **Per-chat.** The applied pack used to be a single app-wide value, so a Visual Novel story
     *    and a stage-play story could not coexist. `ChatEntity.stylePackId` wins when set; the global
     *    setting remains the fallback, which is what keeps every existing conversation unchanged.
     *
     * 2. **The bundle no longer stacks.** Owning `style_pack_bundle` used to concatenate all four
     *    packs with `---` separators, so the customer who paid the most was asking the model to write
     *    a film, a stage play, a light novel and second-person narration simultaneously — reliably the
     *    worst output of any configuration. The bundle is an entitlement to four registers, not an
     *    instruction to blend them: with nothing chosen it now applies none and lets the default
     *    register stand, until the story card picks one.
     */
    private fun loadStylePackPrompt() {
        val packId = _chat.value?.stylePackId?.takeIf { it.isNotBlank() }
            ?: secureStorage.getString(SecureStorage.KEY_APPLIED_STYLE_PACK)?.takeIf { it.isNotBlank() }
        stylePackPrompt = StylePacks.find(packId)?.prompt
    }

    /** Style packs the user may pick from for this conversation — all of them, they ship with the app. */
    fun availableStylePacks(): List<Pair<String, String>> = StylePacks.all.map { it.id to it.name }

    /**
     * True while this conversation still needs its framing question answered.
     *
     * Gated on the message count as well as the flag so the card can never interrupt a story already
     * under way — including the conversations that predate the card, which all carry
     * `hasSeenStoryCard = false` and would otherwise be ambushed mid-scene.
     */
    val needsStoryCard: StateFlow<Boolean> = combine(_chat, _messages) { chat, messages ->
        chat != null && !chat.hasSeenStoryCard && messages.isEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _toneCards = MutableStateFlow<List<ToneCardEntity>>(emptyList())

    /** The user's own library, offered on every conversation whoever the character is. */
    private val _profileToneCards = MutableStateFlow<List<ToneCardEntity>>(emptyList())

    /**
     * Presets offerable here: this character's own tones, then the user's own library, then the
     * built-in catalogue.
     *
     * The order is the point. A card written for *this* persona knows things nothing else does — that
     * this rival is funniest when the replies are short. The user's own library comes next because it
     * is still about them rather than about nobody: "I want plot twists and new faces" holds whoever
     * they are talking to. The six generic recipes, written for no one in particular, come last.
     *
     * Mature entries stay behind the same tag gate at every scope, so authoring a tone card — at any
     * level, including the ungated profile library — can never become a way around it.
     */
    fun offeredPresets(): List<StoryPreset> {
        val allowMature = StoryCardPresets.allowMature(_persona.value?.maturityTags.orEmpty())
        fun cards(source: List<ToneCardEntity>) = source
            .map { StoryCardPresets.fromToneCard(it) }
            .filter { allowMature || !it.requiresMature }
        return cards(_toneCards.value) + cards(_profileToneCards.value) + StoryCardPresets.available(allowMature)
    }

    /**
     * Writes a preset onto the conversation and closes the card.
     *
     * This is what guarantees the property the whole feature rests on: after this runs,
     * `buildStyleContract` can no longer produce a bare default register, whichever path got here —
     * a chosen preset, a generated card, or "Skip".
     */
    fun applyStoryCard(
        preset: StoryPreset,
        directive: String = "",
        stylePackId: String = "",
        /** False only when the caller writes its own opening — see [generateStoryCard] — so the
         *  persona's canned first message does not land underneath a generated one. */
        insertPersonaOpening: Boolean = true
    ) {
        viewModelScope.launch {
            updateExperienceMode { chat ->
                chat.copy(
                    storyPresetId = preset.storedPresetId,
                    storyPaceMode = preset.storyPaceMode,
                    toneMode = preset.toneMode,
                    involvementMode = preset.involvementMode,
                    narrativeRhythmMode = preset.narrativeRhythmMode,
                    universeMode = preset.universeMode,
                    intensityMode = preset.intensityMode,
                    replyLength = preset.replyLength,
                    narrationBalance = preset.narrationBalance,
                    voiceMode = preset.voiceMode,
                    // Only overwrite an existing instruction when the card actually produced one, so
                    // reopening the card on a tuned conversation does not silently erase it.
                    // A tone card carries its own instruction; an explicit one from the caller (the
                    // AI-generated card) still wins over it.
                    customExperienceDirective = directive.ifBlank { preset.directive }
                        .ifBlank { chat.customExperienceDirective },
                    stylePackId = stylePackId.ifBlank { chat.stylePackId },
                    hasSeenStoryCard = true
                )
            }
            loadStylePackPrompt()
            if (insertPersonaOpening) insertPersonaOpeningIfMissing()
        }
    }

    /**
     * Writes the persona's canned opening now that the framing question has been answered.
     *
     * The insertion moved out of `init` for the card's sake; running it here keeps the observable
     * behaviour identical for anyone who taps a preset or skips — they land on the same opening
     * message they would have seen before, only now with the story already framed.
     */
    private suspend fun insertPersonaOpeningIfMissing() {
        val persona = _persona.value ?: return
        if (messageRepository.getRecent(chatId, 1).isNotEmpty()) return
        val scene = _chat.value?.selectedSceneId?.let { entrySceneRepository.getById(it) }
        val firstMessage = scene?.firstMessage?.takeIf { it.isNotBlank() } ?: persona.firstMessage
        if (firstMessage.isBlank()) return
        messageRepository.upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                content = firstMessage,
                imageAttachmentPath = null,
                tokenCount = null,
                createdAt = System.currentTimeMillis()
            )
        )
        updateChatTimestamp()
    }

    /** "Skip" — still applies a real preset, deduced from the persona's tags. The card exists to
     *  remove unsteered conversations, so skipping must not recreate one. */
    fun skipStoryCard() {
        applyStoryCard(StoryCardPresets.defaultFor(_persona.value?.maturityTags.orEmpty()))
    }

    private val _storyCardGenerating = MutableStateFlow(false)
    val storyCardGenerating: StateFlow<Boolean> = _storyCardGenerating.asStateFlow()

    /**
     * Turns the player's free-text answer into a full card, in one call.
     *
     * On success it also writes the opening scene straight into the conversation, so the story starts
     * already framed *and* already in motion rather than on an empty screen.
     */
    fun generateStoryCard(description: String) {
        if (_storyCardGenerating.value) return
        viewModelScope.launch {
            _storyCardGenerating.value = true
            val persona = _persona.value
            val characterContext = persona?.let {
                "Name: ${it.name}\nDescription: ${it.shortDescription}\nPersonality: ${it.personality}"
            }.orEmpty()
            val offered = offeredPresets()
            generateStoryCardUseCase(offered.map { it.id }, characterContext, description)
                .onSuccess { draft ->
                    val preset = StoryCardPresets.byId(draft.presetId) ?: offered.first()
                    applyStoryCard(preset, draft.directive, insertPersonaOpening = false)
                    if (draft.scenario.isNotBlank()) _activeScenario = draft.scenario
                    if (draft.firstMessage.isNotBlank() && _messages.value.isEmpty()) {
                        messageRepository.upsert(
                            MessageEntity(
                                id = UUID.randomUUID().toString(),
                                chatId = chatId,
                                role = MessageRole.ASSISTANT,
                                content = draft.firstMessage,
                                imageAttachmentPath = null,
                                tokenCount = null,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                .onFailure { _error.value = NetworkErrorMessages.forUser(it) }
            _storyCardGenerating.value = false
        }
    }

    fun setStylePack(packId: String) {
        viewModelScope.launch {
            updateExperienceMode { it.copy(stylePackId = packId) }
            loadStylePackPrompt()
        }
    }

    private suspend fun maybeShowRecap(chat: ChatEntity) {
        val recapEnabled = secureStorage.getInt(SecureStorage.KEY_AUTO_RECAP_ENABLED, 1) == 1
        if (!recapEnabled) return
        if (!generateRecapUseCase.shouldRecap(chat.updatedAt)) return
        val existingMessages = messageRepository.getRecent(chatId, 1)
        if (existingMessages.isEmpty()) return

        runCatching {
            // What happened *without* the player takes priority over a summary of what they already
            // saw (2026-08-25) — but only when the story has something that could genuinely have
            // moved on its own. With no stated drive and no unkept promise there is nothing to
            // report, and inventing one would hand the next turn an event it has never heard of, so
            // we fall back to the plain recap rather than manufacture a beat.
            val threads = generateWorldBeatUseCase.openThreads(chatId)
            val persona = _persona.value
            val text = if (generateWorldBeatUseCase.hasMaterial(persona, threads)) {
                val daysAway = (System.currentTimeMillis() - chat.updatedAt) / MILLIS_PER_DAY
                generateWorldBeatUseCase.generate(chatId, persona, chat.summary, threads, daysAway)
                    // The beat can be refused after generation (it addressed the player), and that
                    // call is already spent — so we do not spend a second one on a recap.
                    ?: return@runCatching
            } else {
                generateRecapUseCase.generate(chatId, chat.summary)
            }

            if (!text.isNullOrBlank()) {
                messageRepository.upsert(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        role = MessageRole.SYSTEM,
                        content = text,
                        imageAttachmentPath = null,
                        tokenCount = null,
                        createdAt = System.currentTimeMillis()
                    )
                )
                updateChatTimestamp()
            }
        }
    }

    /**
     * Watches for completed (or failed) NPC generation jobs tied to this chat. Successful jobs are
     * consumed: the NPC is inserted, added to the cast, the join framing is applied, and the job
     * is deleted so it isn't processed again on recomposition or process restart. Failed jobs
     * surface their error and are also removed.
     */
    private suspend fun observeNpcGenerationJobs() {
        generationJobRepository.observeByChatId(chatId)
            .collect { jobs ->
                val npcJobs = jobs.filter { it.type == GenerationJobType.NPC }
                _isGeneratingCastMember.value = npcJobs.any {
                    it.state == GenerationJobState.PENDING || it.state == GenerationJobState.RUNNING
                }

                npcJobs.filter { it.state == GenerationJobState.SUCCEEDED }.forEach { job ->
                    consumeCompletedNpcJob(job)
                }

                npcJobs.filter { it.state == GenerationJobState.FAILED }.forEach { job ->
                    _error.value = job.errorMessage ?: "La génération du PNJ a échoué."
                    generationJobRepository.delete(job)
                }
            }
    }

    private suspend fun consumeCompletedNpcJob(job: GenerationJobEntity) {
        val result = job.parseNpcResult()
        if (result == null) {
            generationJobRepository.delete(job)
            return
        }

        val universeId = _chat.value?.universeId ?: job.universeId ?: return
        val npcResult = result.npc
        val npc = NpcEntity(
            id = UUID.randomUUID().toString(),
            universeId = universeId,
            name = npcResult.name,
            description = npcResult.description,
            personality = npcResult.personality,
            role = npcResult.role,
            factionId = null,
            locationId = null,
            age = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        npcRepository.insert(npc)
        chatParticipantRepository.addParticipant(chatId, ParticipantType.NPC, npc.id)
        _castMembers.value = resolveCastMembers(chatParticipantRepository.getByChat(chatId))
        _availableCastCandidates.value = _availableCastCandidates.value.filterNot { it.id == npc.id }

        val framing = job.joinFraming?.let { ParticipantJoinFraming.valueOf(it) }
            ?: ParticipantJoinFraming.RETROACTIVE
        applyJoinFraming(framing, listOf(npc.id to ParticipantType.NPC))

        generationJobRepository.delete(job)
    }

    private suspend fun resolveCastMembers(participants: List<ChatParticipantEntity>): List<CastMember> =
        participants.mapNotNull { participant ->
            when (participant.participantType) {
                ParticipantType.PERSONA -> personaRepository.getById(participant.participantId)?.let {
                    CastMember(
                        participantId = it.id,
                        participantType = ParticipantType.PERSONA,
                        name = it.name,
                        description = it.shortDescription,
                        personality = it.personality
                    )
                }
                ParticipantType.NPC -> npcRepository.getById(participant.participantId)?.let {
                    CastMember(
                        participantId = it.id,
                        participantType = ParticipantType.NPC,
                        name = it.name,
                        description = it.description,
                        personality = it.personality,
                        role = it.role
                    )
                }
            }
        }

    

    private val _messageImageBytesById = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val messageImageBytesById: StateFlow<Map<String, ByteArray>> = _messageImageBytesById.asStateFlow()

    /** Decrypts and caches any message image not already loaded — keyed by image id. */
    fun ensureMessageImagesLoaded(messages: List<MessageEntity>) {
        val missingIds = messages.mapNotNull { it.imageAttachmentPath }.filter { it !in _messageImageBytesById.value }
        if (missingIds.isEmpty()) return
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                missingIds.mapNotNull { id -> encryptedImageStore.load(id)?.let { id to it } }
            }
            _messageImageBytesById.value = _messageImageBytesById.value + loaded
        }
    }

    fun setBackground(bytes: ByteArray) {
        viewModelScope.launch {
            val currentChat = _chat.value ?: return@launch
            val newId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
            val oldId = currentChat.backgroundImageId
            val updated = currentChat.copy(backgroundImageId = newId, updatedAt = System.currentTimeMillis())
            chatRepository.upsert(updated)
            _chat.value = updated
            _backgroundBytes.value = bytes
            if (oldId != null) withContext(Dispatchers.IO) { encryptedImageStore.delete(oldId) }
        }
    }

    private val _backgroundPickerSections = MutableStateFlow<List<BackgroundPickerSection>>(emptyList())
    val backgroundPickerSections: StateFlow<List<BackgroundPickerSection>> = _backgroundPickerSections.asStateFlow()

    private val _galleryImageBytesById = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val galleryImageBytesById: StateFlow<Map<String, ByteArray>> = _galleryImageBytesById.asStateFlow()

    /**
     * Loads candidates for the background picker (menu > "Fond d'écran"): this chat's own gallery
     * (every image generated in it, regardless of persona attribution), the parent universe's
     * gallery for an ensemble chat (group scenes with no single persona to attribute to), and
     * either the one persona's avatar + gallery (regular chat) or every persona of the universe's
     * avatar + gallery, sorted by name (ensemble chat). Empty sections are dropped.
     */
    fun loadGalleriesForBackgroundPicker() {
        viewModelScope.launch {
            val universeId = _chat.value?.universeId
            val isEnsemble = universeId != null && _persona.value == null

            val chatSection = BackgroundPickerSection(
                title = "Cette conversation",
                imageIds = chatImageRepository.getByChat(chatId).map { it.imageStoreId }
            )
            val universeSection = universeId?.takeIf { isEnsemble }?.let {
                BackgroundPickerSection(
                    title = "Univers",
                    imageIds = universeImageRepository.getByUniverse(it).map { image -> image.imageStoreId }
                )
            }
            val personas = if (isEnsemble) {
                personaRepository.observeByUniverse(universeId!!).first().sortedBy { it.name }
            } else {
                listOfNotNull(_persona.value)
            }
            val personaSections = personas.map { persona ->
                val images = personaImageRepository.getByPersona(persona.id)
                val imageIds = listOfNotNull(persona.avatarImageId) + images.map { it.imageStoreId }
                BackgroundPickerSection(title = persona.name, imageIds = imageIds)
            }

            val sections = (listOf(chatSection) + listOfNotNull(universeSection) + personaSections)
                .filter { it.imageIds.isNotEmpty() }
            _backgroundPickerSections.value = sections

            val idsToLoad = sections.flatMap { it.imageIds }.filter { it !in _galleryImageBytesById.value }
            if (idsToLoad.isEmpty()) return@launch
            val loaded = withContext(Dispatchers.IO) {
                idsToLoad.mapNotNull { id -> encryptedImageStore.load(id)?.let { id to it } }
            }
            _galleryImageBytesById.value = _galleryImageBytesById.value + loaded
        }
    }

    /** Reuses the bytes of an existing persona avatar/gallery image as the conversation background. */
    fun setBackgroundFromGallery(imageStoreId: String) {
        val bytes = _galleryImageBytesById.value[imageStoreId] ?: return
        setBackground(bytes)
    }

    /**
     * Files an already-sent in-chat image (long-press on a message image, "Ajouter à la galerie")
     * into [targetPersonaId]'s gallery — retroactively, for images generated before this feature
     * existed or simply never saved at generation time. [targetPersonaId] must be one of
     * [galleryTargets]. Re-saves the bytes under a fresh `EncryptedImageStore` id rather than
     * reusing [messageImageStoreId] directly, so deleting the message later (which deletes its own
     * image) can never silently orphan the gallery copy, or vice versa.
     */
    fun saveMessageImageToGallery(messageImageStoreId: String, targetPersonaId: String, description: String) {
        if (_galleryTargets.value.none { it.personaId == targetPersonaId }) return

        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { encryptedImageStore.load(messageImageStoreId) } ?: return@launch
            val newImageId = withContext(Dispatchers.IO) { encryptedImageStore.save(bytes) }
            personaImageRepository.upsert(
                PersonaImageEntity(
                    id = UUID.randomUUID().toString(),
                    personaId = targetPersonaId,
                    imageStoreId = newImageId,
                    description = description,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateInputText(value: TextFieldValue) {
        _inputText.value = autoCapitalizeSentences(value)
        if (_nextReplySuggestions.value.isNotEmpty()) _nextReplySuggestions.value = emptyList()
    }

    /** Auto-capitalises the first letter of each sentence (start of text or after `. `, `? `, `! `, `\n`). */
    private fun autoCapitalizeSentences(value: TextFieldValue): TextFieldValue {
        val newText = SENTENCE_BOUNDARY_REGEX.replace(value.text) { match ->
            match.groupValues[1] + match.groupValues[2].uppercase(Locale.ROOT)
        }
        return value.copy(
            text = newText,
            selection = TextRange(
                value.selection.start.coerceIn(0, newText.length),
                value.selection.end.coerceIn(0, newText.length)
            )
        )
    }

    /** Inserts `*` at the current cursor position (or replaces the selection), then moves the cursor past it. */
    fun insertAsterisk() {
        val current = _inputText.value
        val newText = current.text.replaceRange(current.selection.start, current.selection.end, "*")
        _inputText.value = TextFieldValue(newText, TextRange(current.selection.start + 1))
    }

    fun sendMessage() {
        val trimmed = _inputText.value.text.trim()
        val chat = _chat.value
        val persona = _persona.value
        val isEnsemble = chat?.personaId == null
        if (trimmed.isEmpty() || chat == null || (!isEnsemble && persona == null) || _isSending.value) return

        _inputText.value = TextFieldValue("")
        _nextReplySuggestions.value = emptyList()
        _error.value = null
        _isSending.value = true


        if (safeWordManager.matches(trimmed)) {
            viewModelScope.launch { insertSystemMessage(chatId, "— Safe word activé : la scène a été interrompue. —") }
            _isSending.value = false
            return
        }

        viewModelScope.launch {
            messageRepository.upsert(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    role = MessageRole.USER,
                    content = trimmed,
                    imageAttachmentPath = null,
                    tokenCount = null,
                    createdAt = System.currentTimeMillis()
                )
            )

            val rawWindow = messageRepository.getRecent(chatId, rawWindowSize())
            val memory = buildTurnMemoryUseCase(chatId, trimmed, rawWindow)
            val history = buildApiHistory(rawWindow) + listOfNotNull(styleContractTurn(memory))

            chatCompletionRepository.complete(
                modelId = resolveChatModel(),
                systemPrompt = buildSystemPrompt(persona, _castMembers.value, memory),
                messages = history,
                temperature = effectiveTemperature(),
                maxTokens = chatMaxTokens(),
                sampling = currentSampling()
            ).onSuccess { result ->
                acceptAiResponse(result)
            }.onFailure { e ->
                handleChatFailure(e, PendingChatRetry.Send(trimmed))
            }

            _isSending.value = false

            launch { updateChatSummaryUseCase(chatId) }
            launch { indexMessageChunkUseCase(chatId) }
        }
    }

    fun regenerateLastResponse() {
        val chat = _chat.value ?: return
        val persona = _persona.value
        val isEnsemble = chat.personaId == null
        if (!isEnsemble && persona == null) return
        if (_isSending.value) return

        _isSending.value = true
        _error.value = null


        viewModelScope.launch {
            val recentMessages = messageRepository.getRecent(chatId, 2)
            val lastAssistant = recentMessages.firstOrNull { it.role == MessageRole.ASSISTANT }
            val lastUser = recentMessages.firstOrNull { it.role == MessageRole.USER }

            if (lastAssistant == null || lastUser == null) {
                _isSending.value = false
                return@launch
            }

            // The previous reply is kept, not destroyed (2026-08-25). Deleting it meant a worse second
            // attempt lost the first for good, with nothing to compare — and comparing is the single
            // most-used control in this category.
            snapshotAsVariant(lastAssistant)
            _regeneratingMessageId.value = lastAssistant.id
            messageRepository.delete(lastAssistant)
            executeCompletionFromLastUser(lastUser.content, temperatureOverride = regenerateTemperature()) { PendingChatRetry.Regenerate }
        }
    }


    // --- Reply variants, in-place editing, continuation (2026-08-25) ---

    /** The assistant message whose alternatives the user is currently paging through, if any. */
    private val _regeneratingMessageId = MutableStateFlow<String?>(null)

    /** True while the open edit applies to the message itself rather than resending the story. */
    private val _editInPlace = MutableStateFlow(false)
    val editInPlace: StateFlow<Boolean> = _editInPlace.asStateFlow()

    private val _variantsByMessage = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    /** `messageId -> (currentIndex, total)`, for the ‹ 2/3 › control under a reply. */
    val variantPositions: StateFlow<Map<String, Pair<Int, Int>>> =
        combine(_messages, _variantsByMessage) { messages, variants ->
            messages.mapNotNull { message ->
                val versions = variants[message.id] ?: return@mapNotNull null
                if (versions.size < 2) return@mapNotNull null
                val index = versions.indexOf(message.content).takeIf { it >= 0 } ?: versions.lastIndex
                message.id to (index to versions.size)
            }.toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Records a reply before it is replaced.
     *
     * The first regeneration stores **two** rows — the original and, once it arrives, the new one —
     * so that the count the user sees matches the number of versions that actually exist rather than
     * starting at one.
     */
    private suspend fun snapshotAsVariant(message: MessageEntity) {
        val existing = messageVariantRepository.getByMessage(message.id)
        if (existing.none { it.content == message.content }) {
            messageVariantRepository.upsert(
                MessageVariantEntity(
                    id = UUID.randomUUID().toString(),
                    messageId = message.id,
                    content = message.content,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Loads the alternatives of the visible replies, so the ‹ 2/3 › control knows what to show. */
    fun refreshVariants() {
        viewModelScope.launch {
            val loaded = _messages.value
                .filter { it.role == MessageRole.ASSISTANT }
                .associate { it.id to messageVariantRepository.getByMessage(it.id).map { v -> v.content } }
                .filterValues { it.size >= 2 }
            _variantsByMessage.value = loaded
        }
    }

    /**
     * Switches a reply to another of its versions.
     *
     * A copy from the variant table into `messages.content` — nothing that reads messages needs to
     * know variants exist, which is exactly why they live in their own table.
     */
    fun selectVariant(messageId: String, index: Int) {
        viewModelScope.launch {
            val versions = _variantsByMessage.value[messageId] ?: return@launch
            val content = versions.getOrNull(index) ?: return@launch
            val message = messageRepository.getById(messageId) ?: return@launch
            messageRepository.upsert(message.copy(content = content))
        }
    }

    /**
     * Saves an edit without regenerating anything after it.
     *
     * This is what makes editing any message safe: the story keeps its shape, only the text of one
     * message changes. The memory pipeline picks the new text up on its next pass like any other
     * content.
     */
    fun saveEditedMessageInPlace(newText: String) {
        val target = _editingMessage.value ?: return
        viewModelScope.launch {
            messageRepository.upsert(target.copy(content = newText.trim(), isEdited = true))
            _editingMessage.value = null
            _editInPlace.value = false
            _inputText.value = TextFieldValue("")
        }
    }

    /**
     * Asks the model to carry on from where a reply stopped.
     *
     * A reply cut off at the token ceiling is the one case where regenerating is the wrong answer —
     * the user liked what they got, there was simply not enough of it. The repository has been able
     * to continue since long before this button existed (`allowContinuation`); what was missing was
     * a way to ask for it.
     */
    fun continueLastResponse() {
        if (_isSending.value) return
        viewModelScope.launch {
            val lastAssistant = messageRepository.getRecent(chatId, 1)
                .firstOrNull { it.role == MessageRole.ASSISTANT } ?: return@launch
            _isSending.value = true

            val rawWindow = messageRepository.getRecent(chatId, rawWindowSize())
            val memory = buildTurnMemoryUseCase(chatId, lastAssistant.content, rawWindow)
            val history = buildApiHistory(rawWindow) +
                ChatTurn(role = ChatMessageDto.ROLE_USER, content = CONTINUE_INSTRUCTION) +
                listOfNotNull(styleContractTurn(memory))

            chatCompletionRepository.complete(
                modelId = resolveChatModel(),
                systemPrompt = buildSystemPrompt(_persona.value, _castMembers.value, memory),
                messages = history,
                temperature = effectiveTemperature(),
                maxTokens = chatMaxTokens(),
                sampling = currentSampling()
            ).onSuccess { result ->
                if (result.content.isNotBlank()) {
                    // Appended to the same message rather than added as a new one: it is the same
                    // reply, finished — splitting it would put a false turn boundary in the memory
                    // pipeline and in the novel export.
                    messageRepository.upsert(
                        lastAssistant.copy(content = lastAssistant.content.trimEnd() + " " + result.content.trim())
                    )
                    updateChatTimestamp()
                }
            }.onFailure { e -> _error.value = NetworkErrorMessages.forUser(e) }
            _isSending.value = false
        }
    }

    private suspend fun executeCompletionFromLastUser(
        lastUserContent: String,
        temperatureOverride: Double? = null,
        onFailurePendingRetry: () -> PendingChatRetry
    ) {
        val persona = _persona.value
        val rawWindow = messageRepository.getRecent(chatId, rawWindowSize())
        val memory = buildTurnMemoryUseCase(chatId, lastUserContent, rawWindow)
        val history = buildApiHistory(rawWindow) + listOfNotNull(styleContractTurn(memory))

        chatCompletionRepository.complete(
            modelId = resolveChatModel(),
            systemPrompt = buildSystemPrompt(persona, _castMembers.value, memory),
            messages = history,
            temperature = temperatureOverride ?: effectiveTemperature(),
            maxTokens = chatMaxTokens(),
            sampling = currentSampling()
        ).onSuccess { result ->
            acceptAiResponse(result)
        }.onFailure { e ->
            handleChatFailure(e, onFailurePendingRetry())
        }

        _isSending.value = false
    }

    /**
     * Opens a message for editing.
     *
     * Any message, of either role (2026-08-25). The old restriction to the *last user* message came
     * from the resend path: replaying from the middle of a story deletes everything after it, so
     * allowing it anywhere would have silently destroyed the rest. Editing **without** resending has
     * no such consequence — see [saveEditedMessageInPlace] — so the two cases are now separated
     * rather than both being refused.
     *
     * The tamper-proof local log (`message_audit_log`) is unaffected by design: it records messages
     * as first generated and is never touched by an edit, so the integrity guarantee still holds.
     */
    fun startEditingMessage(message: MessageEntity) {
        viewModelScope.launch {
            val lastUser = messageRepository.getRecent(chatId, rawWindowSize())
                .firstOrNull { it.role == MessageRole.USER }
            // Only the last user message can be edited *and resent*; anything else is edited in place.
            _editInPlace.value = message.role != MessageRole.USER || lastUser?.id != message.id
            _editingMessage.value = message
            _inputText.value = TextFieldValue(
                text = message.content,
                selection = TextRange(message.content.length)
            )
        }
    }

    fun cancelEdit() {
        _editingMessage.value = null
        _inputText.value = TextFieldValue("")
    }

    fun updateEditedMessage() {
        val chat = _chat.value ?: return
        val persona = _persona.value
        val isEnsemble = chat.personaId == null
        if (!isEnsemble && persona == null) return
        val text = _inputText.value.text.trim()
        if (text.isEmpty() || _isSending.value) return
        val original = _editingMessage.value ?: return
        if (original.role != MessageRole.USER) return

        viewModelScope.launch {
            val lastUser = messageRepository.getRecent(chatId, rawWindowSize())
                .firstOrNull { it.role == MessageRole.USER }
            if (lastUser?.id != original.id) {
                _error.value = "Ce message n'est plus le dernier message utilisateur."
                _editingMessage.value = null
                _inputText.value = TextFieldValue("")
                return@launch
            }

            _isSending.value = true
            _error.value = null
            _editingMessage.value = null
            _inputText.value = TextFieldValue("")

            messageRepository.upsert(original.copy(content = text, isEdited = true))

            val newerAssistantMessages = messageRepository.getFrom(chatId, original.createdAt)
                .filter { it.id != original.id && it.role == MessageRole.ASSISTANT }
            newerAssistantMessages.forEach { messageRepository.delete(it) }

            executeCompletionFromLastUser(text, temperatureOverride = regenerateTemperature()) { PendingChatRetry.Edit(original.id, text) }
        }
    }

    /**
     * Injects a directorial beat requested by the user through the "Outils de scène" menu.
     * The instruction is passed as a synthetic user turn (not persisted) so the model answers
     * in-fiction, and the resulting assistant message is inserted into the chat.
     */
    fun injectDirectorBeat(kind: DirectorToolKind) {
        val chat = _chat.value ?: return
        val persona = _persona.value
        val isEnsemble = chat.personaId == null
        if (!isEnsemble && persona == null) return
        if (_isSending.value) return

        _isSending.value = true
        _error.value = null


        viewModelScope.launch {
            val instruction = when (kind) {
                DirectorToolKind.SCENE_CHANGE ->
                    "Directeur de scène : change de décor et de situation maintenant. Fais évoluer le cadre et l'atmosphère de façon crédible, sans attendre que l'utilisateur demande quoi que ce soit."
                DirectorToolKind.PLOT_TWIST ->
                    "Directeur de scène : introduis un retournement narratif inattendu mais cohérent avec l'univers et les personnages. Pas de préambule."
                DirectorToolKind.TIME_SKIP ->
                    "Directeur de scène : effectue un saut temporel (plusieurs heures, jours ou semaines selon ce qui a du sens) puis reprends la scène à ce moment, en résumant brièvement ce qui a changé." +
                        STORY_TIME_SKIP_MARKER_INSTRUCTION
                DirectorToolKind.RANDOM_INTERLUDE ->
                    "Directeur de scène : insère un interlude aléatoire (événement extérieur, visite, interruption, flash d'ambiance...) qui oblige les personnages à réagir."
            }

            val rawWindow = messageRepository.getRecent(chatId, rawWindowSize())
            // Contract goes last, after the director instruction — closest to the generation point.
            val memory = buildTurnMemoryUseCase(chatId, instruction, rawWindow)
            val history = buildApiHistory(rawWindow)
                .plus(ChatTurn(role = ChatMessageDto.ROLE_USER, content = instruction))
                .plus(listOfNotNull(styleContractTurn(memory)))

            chatCompletionRepository.complete(
                modelId = resolveChatModel(),
                systemPrompt = buildSystemPrompt(persona, _castMembers.value, memory),
                messages = history,
                temperature = effectiveTemperature(),
                maxTokens = chatMaxTokens(),
                sampling = currentSampling()
            ).onSuccess { result ->
                val (accepted, newStoryTime) = if (kind == DirectorToolKind.TIME_SKIP) {
                    val (cleaned, extracted) = extractAndStripStoryTimeMarker(result.content)
                    acceptAiResponse(result.copy(content = cleaned)) to extracted
                } else {
                    acceptAiResponse(result) to null
                }
                if (accepted && newStoryTime != null) {
                    chatRepository.getById(chatId)?.let { chat ->
                        val updated = chat.copy(storyTimeAnchor = newStoryTime, updatedAt = System.currentTimeMillis())
                        chatRepository.upsert(updated)
                        _chat.value = updated
                    }
                }
            }.onFailure { e ->
                handleChatFailure(e, PendingChatRetry.DirectorBeat(kind))
            }

            _isSending.value = false
        }
    }

    /**
     * Ensemble chats have no canned `persona.firstMessage` to auto-insert (no single protagonist),
     * so the opening scene is opt-in instead of automatic: the user taps a button once the chat is
     * confirmed empty, and the AI improvises an opening introducing the cast. Only valid before any
     * message exists — calling it again once the scene has started would just be a normal message.
     */
    fun generateOpeningScene() {
        val chat = _chat.value ?: return
        if (chat.personaId != null || _castMembers.value.isEmpty()) return
        if (_isSending.value || _messages.value.isNotEmpty()) return

        _isSending.value = true
        _error.value = null


        viewModelScope.launch {
            val systemPrompt = buildSystemPrompt(null, _castMembers.value, TurnMemory(storyTimeAnchor = chat.storyTimeAnchor)) +
                "\n\n## Instruction\nWrite the opening scene now: set the stage, introduce the characters above naturally through action and dialogue, and end at a natural point for the user to respond. Do not wait for the user to speak first."

            // A system-prompt-only request (no user/assistant turn) is rejected by some
            // OpenAI-compatible providers, or answered with empty content — a synthetic user turn
            // sidesteps that; it's never persisted, only the assistant's reply is saved as a message.
            val kickoffTurn = ChatTurn(role = ChatMessageDto.ROLE_USER, content = "(Begin the scene now.)")

            // The opening scene is the only message a reader judges the whole story on, and until
            // 2026-08-23 it was the one message generated with no style contract at all — so a chat
            // configured for brisk second-person comedy opened in the default novelistic register and
            // only switched afterwards. There is no transcript yet to fight, but the contract is
            // still what carries the modes, the reply shape and the style pack.
            val openingTurns = listOf(kickoffTurn) + listOfNotNull(styleContractTurn(TurnMemory.EMPTY))

            chatCompletionRepository.complete(
                modelId = resolveChatModel(),
                systemPrompt = systemPrompt,
                messages = openingTurns,
                temperature = effectiveTemperature(),
                maxTokens = chatMaxTokens(),
                sampling = currentSampling()
            ).onSuccess { result ->
                if (result.content.isBlank()) {
                    _error.value = "Le modèle n'a rien renvoyé — réessayez."
                } else {
                    messageRepository.upsert(
                        MessageEntity(
                            id = UUID.randomUUID().toString(),
                            chatId = chatId,
                            role = MessageRole.ASSISTANT,
                            content = result.content,
                            imageAttachmentPath = null,
                            tokenCount = result.usage?.completionTokens,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    updateChatTimestamp()
                }
            }.onFailure { e ->
                handleChatFailure(e, PendingChatRetry.OpeningScene)
            }

            _isSending.value = false
        }
    }

    private val _availableCastCandidates = MutableStateFlow<List<CastCandidateOption>>(emptyList())
    val availableCastCandidates: StateFlow<List<CastCandidateOption>> = _availableCastCandidates.asStateFlow()

    private val _isGeneratingCastMember = MutableStateFlow(false)
    val isGeneratingCastMember: StateFlow<Boolean> = _isGeneratingCastMember.asStateFlow()

    /** Loads every persona/NPC of this chat's universe not already in its cast, for the "Ajouter un
     * personnage à la scène" dialog (menu ⋮, ensemble chats only). */
    fun loadAvailableCastCandidates() {
        val universeId = _chat.value?.universeId ?: return
        viewModelScope.launch {
            val currentIds = chatParticipantRepository.getByChat(chatId).map { it.participantId }.toSet()
            val personas = personaRepository.observeByUniverse(universeId).first()
                .filter { it.id !in currentIds }
                .map { CastCandidateOption(ParticipantType.PERSONA, it.id, it.name, it.shortDescription) }
            val npcs = npcRepository.getByUniverse(universeId).first()
                .filter { it.id !in currentIds }
                .map { CastCandidateOption(ParticipantType.NPC, it.id, it.name, it.role) }
            _availableCastCandidates.value = (personas + npcs).sortedBy { it.name }
        }
    }

    /**
     * Adds an existing universe persona/NPC to this already-ongoing ensemble chat's cast — the
     * user-facing counterpart to [SyncCastFromLoreUseCase][com.kitsune.core.memory.lore.SyncCastFromLoreUseCase],
     * which does the same thing automatically once the story starts talking about them.
     */
    fun addCastMember(type: ParticipantType, participantId: String, framing: ParticipantJoinFraming) {
        viewModelScope.launch {
            chatParticipantRepository.addParticipant(chatId, type, participantId)
            _castMembers.value = resolveCastMembers(chatParticipantRepository.getByChat(chatId))
            _availableCastCandidates.value = _availableCastCandidates.value.filterNot { it.id == participantId }
            applyJoinFraming(framing, listOf(participantId to type))
        }
    }

    /**
     * Schedules an asynchronous NPC generation for this ensemble chat. The actual generation runs
     * in `GenerationWorker` (resilient to the user leaving the app); when the job completes,
     * [observeNpcGenerationJobs] inserts the NPC, joins them to the cast and applies [framing].
     */
    fun generateAndAddNpc(description: String, framing: ParticipantJoinFraming) {
        val universeId = _chat.value?.universeId ?: return
        viewModelScope.launch {
            generationScheduler.scheduleNpcGeneration(
                NpcGenerationRequest(
                    universeContext = universeContextForGeneration(universeId),
                    description = description,
                    chatId = chatId,
                    universeId = universeId,
                    joinFraming = framing.name
                )
            )
        }
    }

    private suspend fun universeContextForGeneration(universeId: String): String {
        val universe = universeRepository.getById(universeId) ?: return ""
        return listOfNotNull(
            "Nom : ${universe.name}",
            universe.genre.takeIf { it.isNotBlank() }?.let { "Genre : $it" },
            universe.description.takeIf { it.isNotBlank() }?.let { "Description : $it" }
        ).joinToString("\n")
    }

    /** [ParticipantJoinFraming.RETROACTIVE] is a quiet out-of-character note only (excluded from
     * what the AI itself sees, same as every [MessageRole.SYSTEM] message) — the character was
     * simply already part of the scene. [ParticipantJoinFraming.ARRIVAL] instead has the AI narrate
     * their entrance as a real in-fiction beat. */
    private suspend fun applyJoinFraming(framing: ParticipantJoinFraming, joined: List<Pair<String, ParticipantType>>) {
        val names = joined.mapNotNull { (id, type) ->
            when (type) {
                ParticipantType.PERSONA -> personaRepository.getById(id)?.name
                ParticipantType.NPC -> npcRepository.getById(id)?.name
            }
        }
        if (names.isEmpty()) return

        when (framing) {
            ParticipantJoinFraming.RETROACTIVE -> {
                val label = if (names.size == 1) {
                    "${names.first()} fait maintenant partie de la scène."
                } else {
                    "${names.joinToString(", ")} font maintenant partie de la scène."
                }
                insertSystemMessage(chatId, "— $label —")
            }
            ParticipantJoinFraming.ARRIVAL -> generateArrivalBeat(names)
        }
    }

    private suspend fun generateArrivalBeat(names: List<String>) {
        _isSending.value = true
        val systemPrompt = buildSystemPrompt(
            null,
            _castMembers.value,
            TurnMemory(
                summary = _chat.value?.summary.orEmpty(),
                storyTimeAnchor = _chat.value?.storyTimeAnchor.orEmpty()
            )
        )
        val who = names.joinToString(" and ")
        val instruction = "Write a short narrative beat (2-4 sentences) where $who now enters/joins the current scene naturally — do not restart or reset the scene, just continue it with their arrival. Then stop and wait for the user."

        val arrivalTurns = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = instruction)) +
            listOfNotNull(styleContractTurn(TurnMemory.EMPTY))

        chatCompletionRepository.complete(
            modelId = resolveChatModel(),
            systemPrompt = systemPrompt,
            messages = arrivalTurns,
            temperature = effectiveTemperature(),
            maxTokens = chatMaxTokens(),
            sampling = currentSampling()
        ).onSuccess { result ->
            if (result.content.isNotBlank()) {
                messageRepository.upsert(
                    MessageEntity(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        role = MessageRole.ASSISTANT,
                        content = result.content,
                        imageAttachmentPath = null,
                        tokenCount = result.usage?.completionTokens,
                        createdAt = System.currentTimeMillis()
                    )
                )
                updateChatTimestamp()
            }
        }.onFailure { e ->
            handleChatFailure(e, PendingChatRetry.Arrival(names))
        }
        _isSending.value = false
    }

    fun forkChat(messageId: String, onForked: (String) -> Unit) {
        viewModelScope.launch {
            val newChat = chatRepository.forkChat(chatId, messageId)
            if (newChat != null) {
                onForked(newChat.id)
            }
        }
    }

    /** Deletes a single message — see [RewindChatUseCase] for how this keeps the rolling
     * summary/semantic memory from silently going stale about a deleted event. */
    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch {
            rewindChatUseCase.deleteSingleMessage(chatId, message)
        }
    }

    /** "Revenir à ce point" (requested explicitly by the user): deletes [fromMessage] and every
     * later message in the conversation. See [RewindChatUseCase] for the memory-consistency handling. */
    fun rewindTo(fromMessage: MessageEntity) {
        viewModelScope.launch {
            rewindChatUseCase.rewindTo(chatId, fromMessage)
        }
    }

    private suspend fun resolveChatModel(): String =
        llmModelResolver.resolve(LlmOperation.CHAT)

    /** Internal send method that accepts the text directly, used for retry after model change.
     *  Unlike [sendMessage], does NOT re-insert the user message — it was already persisted by
     *  the original [sendMessage] call before the failure occurred. */
    private fun sendMessageWithText(text: String) {
        val chat = _chat.value ?: return
        val persona = _persona.value
        val isEnsemble = chat.personaId == null
        if (text.isEmpty() || (!isEnsemble && persona == null) || _isSending.value) return

        _error.value = null
        _isSending.value = true


        viewModelScope.launch {
            val rawWindow = messageRepository.getRecent(chatId, rawWindowSize())
            val memory = buildTurnMemoryUseCase(chatId, text, rawWindow)
            val history = buildApiHistory(rawWindow) + listOfNotNull(styleContractTurn(memory))

            chatCompletionRepository.complete(
                modelId = resolveChatModel(),
                systemPrompt = buildSystemPrompt(persona, _castMembers.value, memory),
                messages = history,
                temperature = effectiveTemperature(),
                maxTokens = chatMaxTokens(),
                sampling = currentSampling()
            ).onSuccess { result ->
                acceptAiResponse(result)
            }.onFailure { e ->
                handleChatFailure(e, PendingChatRetry.Send(text))
            }

            _isSending.value = false
            launch { updateChatSummaryUseCase(chatId) }
            launch { indexMessageChunkUseCase(chatId) }
        }
    }

    private suspend fun updateChatTimestamp() {
        val chat = chatRepository.getById(chatId) ?: return
        chatRepository.upsert(chat.copy(updatedAt = System.currentTimeMillis()))
    }

    fun markBriefingSeen() {
        viewModelScope.launch {
            val chat = chatRepository.getById(chatId) ?: return@launch
            chatRepository.upsert(chat.copy(hasSeenBriefing = true, updatedAt = System.currentTimeMillis()))
        }
    }

    /** "Modes d'expérience" setters — each persists the chosen mode onto this chat's [ChatEntity]
     * and refreshes [_chat] so the change takes effect on the very next reply via
     * `ChatStyleContract.buildStyleContract`, sent as the last turn before generation. */
    fun setStoryPaceMode(mode: StoryPaceMode) = updateExperienceMode { it.copy(storyPaceMode = mode) }

    fun setToneMode(mode: ToneMode) = updateExperienceMode { it.copy(toneMode = mode) }

    fun setInvolvementMode(mode: InvolvementMode) = updateExperienceMode { it.copy(involvementMode = mode) }

    fun setNarrativeRhythmMode(mode: NarrativeRhythmMode) = updateExperienceMode { it.copy(narrativeRhythmMode = mode) }

    fun setUniverseExperienceMode(mode: UniverseExperienceMode) = updateExperienceMode { it.copy(universeMode = mode) }

    fun setIntensityMode(mode: IntensityMode) = updateExperienceMode { it.copy(intensityMode = mode) }

    // Reply shape (2026-08-23). Tuning any of these by hand leaves `storyPresetId` alone on purpose:
    // it records which recipe the story started from, not what it currently is, and the sampling
    // profile it carries stays valid — a shorter reply length does not make a comedy decode coldly.
    fun setReplyLength(mode: ReplyLengthMode) = updateExperienceMode { it.copy(replyLength = mode) }

    fun setNarrationBalance(mode: NarrationBalanceMode) = updateExperienceMode { it.copy(narrationBalance = mode) }

    fun setVoiceMode(mode: VoiceMode) = updateExperienceMode { it.copy(voiceMode = mode) }

    /** Manual override for the auto-tracked story clock (see [ExtractLoreEntriesUseCase] and the
     *  "Time Skip" director tool) — lets the user correct a drift or set an initial anchor by hand. */
    fun setStoryTimeAnchor(text: String) = updateExperienceMode { it.copy(storyTimeAnchor = text) }

    /** Free-form "describe exactly what you want" instruction for this conversation. */
    fun setCustomExperienceDirective(text: String) =
        updateExperienceMode { it.copy(customExperienceDirective = text) }

    private fun updateExperienceMode(update: (ChatEntity) -> ChatEntity) {
        viewModelScope.launch {
            val current = _chat.value ?: chatRepository.getById(chatId) ?: return@launch
            val updated = update(current).copy(updatedAt = System.currentTimeMillis())
            chatRepository.upsert(updated)
            _chat.value = updated
        }
    }

    /**
     * Settings as they stood when the experience-mode dialog was opened, so [commitStyleChanges]
     * can diff against them on close.
     *
     * The diff is taken once, on close, rather than per setter: the dialog calls one setter per
     * category touched, and the free-text directive calls its setter **on every keystroke**. A
     * marker emitted from inside the setters would mean four markers for four toggles, and one per
     * character typed.
     */
    private var styleSettingsOnDialogOpen: StyleSettings? = null

    fun onExperienceModeDialogOpened() {
        styleSettingsOnDialogOpen = _chat.value?.let { StyleSettings.from(it, generationPreferences.isEnhancedCraftEnabled()) }
    }

    fun onExperienceModeDialogClosed() {
        val before = styleSettingsOnDialogOpen ?: return
        styleSettingsOnDialogOpen = null
        commitStyleChanges(before)
    }

    /**
     * Drops a style-pivot marker into the transcript when the settings actually changed, at the
     * point in the conversation where the change happened — see `ChatStyleContract.buildStylePivot`
     * for why the transcript position matters as much as the wording.
     */
    private fun commitStyleChanges(before: StyleSettings) {
        viewModelScope.launch {
            val current = _chat.value ?: chatRepository.getById(chatId) ?: return@launch
            val pivot = buildStylePivot(before, StyleSettings.from(current, generationPreferences.isEnhancedCraftEnabled())) ?: return@launch
            insertStyleDirective(chatId, pivot)
        }
    }

    /** Invisible to the user, unlike [insertSystemMessage] — it exists purely to be read by the
     * model as an inline `system` turn in [buildApiHistory]. */
    private suspend fun insertStyleDirective(chatId: String, content: String) {
        messageRepository.upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.STYLE_DIRECTIVE,
                content = content,
                imageAttachmentPath = null,
                tokenCount = null,
                createdAt = System.currentTimeMillis()
            )
        )
    }

private suspend fun insertSystemMessage(chatId: String, content: String) {
        messageRepository.upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.SYSTEM,
                content = content,
                imageAttachmentPath = null,
                tokenCount = null,
                createdAt = System.currentTimeMillis()
            )
        )
        updateChatTimestamp()
    }

    /**
     * Persists an AI reply as an assistant message (returns true), unless it is not a usable story
     * beat at all (returns false, with a retry offered). Shared by every call site that handles a
     * `chatCompletionRepository.complete()` success (send, regenerate, director beat, retry).
     *
     * There is no keyword moderation of replies or user messages: without a model to tell narrated
     * dark fiction from real harm, a keyword list mostly produced false positives on legitimate adult
     * fiction. What remains is the age floor on characters (persona creation), [SAFETY_DIRECTIVE] at
     * the top of the system prompt, and the provider's own policies.
     */
    private suspend fun acceptAiResponse(result: ChatCompletionResult): Boolean {
        // Before anything else: is this a story beat at all? A user's evening produced the literal
        // word "null", several stage directions and a block of token soup, all written permanently
        // into their story next to real scenes. Nothing unusable reaches the transcript now — the
        // player gets a retry instead, which is recoverable, unlike a ruined story.
        when (val verdict = assessReply(result.content, lastInstructionsSent)) {
            is ReplyVerdict.Discard -> {
                Log.w(TAG, "discarded an unusable reply: ${verdict.reason}")
                _error.value = "L'IA a renvoyé une réponse inutilisable. Rien n'a été ajouté à ton histoire — relance la génération."
                return false
            }
            ReplyVerdict.Usable -> Unit
        }
        messageRepository.upsert(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = MessageRole.ASSISTANT,
                content = result.content,
                imageAttachmentPath = null,
                tokenCount = result.usage?.completionTokens,
                createdAt = System.currentTimeMillis()
            )
        )
        // BUG-012 (BUGS.md): every caller of this shared helper needs this — 3 of its 4 call
        // sites (sendMessage, injectDirectorBeat, sendMessageWithText) forgot to bump the chat's
        // updatedAt themselves, so ChatListViewModel's Room Flow (which
        // only re-emits on writes to the `chats` table, never on writes to `messages`) never saw
        // a new "last message" after an ordinary send. Doing it here once removes the chance of
        // any future caller forgetting it again.
        updateChatTimestamp()
        return true
    }

    /**
     * Raw window → API turns, for every generation path.
     *
     * [MessageRole.SYSTEM] entries (auto-recaps, NPC-join framing) are narration for the user and
     * are dropped. [MessageRole.STYLE_DIRECTIVE] markers are the exact opposite: invisible to the
     * user, but they must reach the model as an inline `system` turn *at the position where the
     * settings changed*, which is the whole point of the mechanism.
     *
     * Factored out because the four generation paths each had their own copy of this mapping —
     * adding the style turn to three of four would have been a silent, hard-to-spot bug.
     */
    private fun buildApiHistory(rawWindow: List<MessageEntity>): List<ChatTurn> =
        rawWindow.asReversed().mapNotNull { message ->
            when (message.role) {
                MessageRole.SYSTEM -> null
                MessageRole.STYLE_DIRECTIVE -> ChatTurn(role = ChatMessageDto.ROLE_SYSTEM, content = message.content)
                MessageRole.USER -> ChatTurn(role = ChatMessageDto.ROLE_USER, content = message.content)
                MessageRole.ASSISTANT -> ChatTurn(role = ChatMessageDto.ROLE_ASSISTANT, content = message.content)
            }
        }

    /**
     * The active style contract, appended as the **last** turn before generation. Null when there
     * is nothing to enforce. See `ChatStyleContract.buildStyleContract` for why position matters
     * more here than wording.
     */
    /**
     * The instructions sent on the most recent turn.
     *
     * Kept so [assessReply] can recognise a reply that simply repeats them, without maintaining a
     * second copy of wording that changes often. See `ReplyIntegrity` for the incident.
     */
    private var lastInstructionsSent: String? = null

    private fun styleContractTurn(memory: TurnMemory): ChatTurn? {
        val chat = _chat.value ?: return null
        val contract = buildStyleContract(currentStyleSettings(chat))
        val beat = buildBeatDirective(decideBeat(narrativeState(memory)))
        val content = listOfNotNull(contract, beat).joinToString(NarrativeThresholds.SECTION_BREAK)
        if (content.isBlank()) {
            lastInstructionsSent = null
            return null
        }
        lastInstructionsSent = content
        // The closing boundary (2026-09-01). Everything above is imperative French, and it is the
        // last thing the model reads before generating — a model that loses the boundary keeps
        // writing instructions instead of switching to prose, which is exactly what a user's
        // transcript showed. Restating the register costs one line and is the only in-prompt defence
        // available; `assessReply` catches the cases where it does not hold.
        return ChatTurn(
            role = ChatMessageDto.ROLE_SYSTEM,
            content = content + NarrativeThresholds.SECTION_BREAK + CONTRACT_CLOSING_BOUNDARY
        )
    }

    /** The three sources the contract needs that do not live on [ChatEntity]: this chat's style pack,
     *  the app-wide custom style, and the taste profile's hard-avoid list. */
    private fun currentStyleSettings(chat: ChatEntity): StyleSettings = StyleSettings.from(
        chat = chat,
        enhancedCraft = generationPreferences.isEnhancedCraftEnabled(),
        stylePackPrompt = stylePackPrompt.orEmpty(),
        globalCustomStyle = secureStorage.getString(SecureStorage.KEY_CUSTOM_STYLE_PROMPT).orEmpty(),
        neverWrite = storyTasteStore.get().neverWrite
    )

    /**
     * The decoder settings for this conversation.
     *
     * Temperature is resolved by [effectiveTemperature] rather than passed through here, so that the
     * Pro and regenerate bonuses keep applying on top of whatever base the preset asks for — a preset
     * that wants a cooler decode should still get a hotter one when the user asks for a different
     * take. What travels in the profile is only what nothing else decides.
     */
    private fun currentSampling(): SamplingProfile {
        if (!MemorySettingsHolder.samplingEnabled) return SamplingProfile.INHERIT
        val presetId = _chat.value?.storyPresetId ?: return SamplingProfile.INHERIT
        val preset = StoryCardPresets.byId(presetId) ?: return SamplingProfile.INHERIT
        return preset.sampling.copy(temperature = null)
    }

    /**
     * Traduit l'état du chat en signaux de rythme pour [decideBeat].
     *
     * La cadence est dérivée du **compteur de tours** plutôt que mémorisée en base : `totalTurns %
     * intervalle` produit la même régularité qu'un champ persistant, sans colonne supplémentaire ni
     * risque de désynchronisation. Le décalage [NarrativeThresholds.QUIET_PHASE] évite que complication et accalmie
     * tombent au même tour, ce qui reviendrait à n'en jouer qu'une.
     *
     * Les fils, eux, viennent de vraies données : leur âge est calculé par `BuildTurnMemoryUseCase`.
     */
    private fun narrativeState(memory: TurnMemory): NarrativeState {
        val totalTurns = _messages.value.count { it.role != MessageRole.SYSTEM }
        return NarrativeState(
            turnsSinceComplication = totalTurns % NarrativeThresholds.COMPLICATION_INTERVAL,
            turnsSinceQuiet = (totalTurns + NarrativeThresholds.QUIET_PHASE) % NarrativeThresholds.MIN_TURNS_BETWEEN_QUIET,
            oldestOpenThreadAgeTurns = memory.openThreads.maxOfOrNull { it.ageTurns },
            openThreadCount = memory.openThreads.size,
            recentTensionSignals = memory.openThreads.count { it.ageTurns <= NarrativeThresholds.RECENT_THREAD_TURNS },
            totalTurns = totalTurns
        )
    }

    /** The reply budget: the user's ceiling (Settings → Mémoire et longueur), tightened when the chat
     * asks for a particular length. */
    private fun chatMaxTokens(): Int {
        val ceiling = generationPreferences.maxReplyTokens()
        // A word count in the prompt asks; a budget enforces. Until now the ceiling was a flat 4096
        // (6144 Pro) — so high that "keep it to 120 words" was the only thing standing between the
        // user and a page of prose, and nothing at all backed it up. The targets are generous
        // multiples of the stated word count (roughly 3 tokens per word, plus room to finish a
        // sentence) so the budget never truncates a reply that respected the instruction; it only
        // stops one that ignored it from running away.
        val target = when (_chat.value?.replyLength) {
            ReplyLengthMode.BRIEF -> 600
            ReplyLengthMode.MEDIUM -> 1200
            ReplyLengthMode.LONG -> 2000
            ReplyLengthMode.UNBOUNDED, ReplyLengthMode.DEFAULT, null -> ceiling
        }
        return minOf(target, ceiling)
    }

    private fun buildSystemPrompt(
        persona: PersonaEntity?,
        castMembers: List<CastMember>,
        memory: TurnMemory = TurnMemory.EMPTY
    ): String = buildString {
        val chatSummary = memory.summary
        val loreEntries = memory.loreEntries
        val relevantMemories = memory.relevantMemories
        val storyTimeAnchor = memory.storyTimeAnchor
        appendLine(SAFETY_DIRECTIVE)
        appendLine()
        if (persona != null) {
            appendLine("You are roleplaying as ${persona.name}. Stay in character at all times and never break the fourth wall.")
        } else {
            appendLine("You are the narrator and every character of an ensemble roleplay scene.")
        }
        appendLine()
        // What stays here is only what no register may override — the rules that hold whether the
        // story is a stage play, a light novel or second-person narration.
        //
        // The prose rules that used to follow ("write like a skilled novelist", "vivid but economical
        // prose", "*italicized action text*") moved into `ChatStyleContract.BASE_CRAFT` on
        // 2026-08-23. They were the structural reason every conversation sounded alike: stated here
        // as unconditional fact, they described one specific register, and anything trying to write
        // differently — an experience mode, a purchased style pack — had to spend its budget arguing
        // with instructions the app itself had already given. In the contract a pack can replace them
        // outright. Cost is unchanged: same text, same every-turn frequency, different position.
        //
        // The optional craft directives and the experience-mode directives left for the same destination
        // earlier, for the related reason that at index 0 they lost against 40-60 transcript messages
        // demonstrating the previous style. Deliberately not duplicated in both places.
        appendLine("## Core dramatic intent")
        appendLine("- Treat every reply as a living scene in an ongoing fiction, not as an assistant response.")
        appendLine("- No meta-commentary, no summarizing what just happened, no disclaimers, no out-of-character commentary.")
        appendLine("- Prioritize momentum, subtext, tension, emotion, and character voice.")
        appendLine("- Make the scene feel immediate, specific, and alive: each reply should reveal something new about the character, the relationship, or the situation.")
        appendLine()
        appendLine("## Scene progression")
        appendLine("- Move the scene forward on every turn.")
        appendLine("- React to what the user did, then add a meaningful new beat: a question, a revealing gesture, a shift in tone, a new observation, or a small complication.")
        appendLine("- Do not merely mirror the user's wording back to them.")
        appendLine("- Do not resolve the scene too quickly unless the user explicitly closes it.")
        appendLine()
        appendLine("## User character boundary")
        appendLine("- Never speak for, narrate the actions of, or decide the internal thoughts, feelings, or decisions of the user's character.")
        appendLine("- The user's character belongs entirely to the user.")
        appendLine("- End your turn at the point where the user's character would need to respond, act, or choose.")

        // The style pack and the global custom style used to be rendered here. They now travel in the
        // style contract, at the rank 3 the contract had been *claiming* for them all along while
        // they were in fact injected hundreds of tokens further up — the weakest position in the
        // context, and the one this codebase had already proven loses to transcript inertia.

        if (persona != null) {
            appendLine()
            appendLine("## Secondary Characters & NPCs")
            appendLine("You may portray other characters (NPCs, secondary characters, narrators) when the story calls for it. When doing so:")
            appendLine("- Clearly distinguish each character's voice, personality, and mannerisms")
            appendLine("- Use formatting to indicate who is speaking: **CharacterName:** \"dialogue\" or *CharacterName* does something")
            appendLine("- NPCs should have their own motivations and react naturally to the situation")
            appendLine("- Don't let NPCs overshadow ${persona.name} — they support the story, not replace the main character")
            appendLine("- If introducing a new NPC, give them a brief description so the user knows who they are")
        } else {
            // Ensemble/universe chat: no single fixed protagonist. The AI plays every character
            // below together in one shared scene and is explicitly told to make them interact.
            appendLine("There is no single fixed protagonist — you play ALL of the characters below together, making them interact naturally with each other and with the user, not just respond to the user one at a time.")
            appendLine()
            appendLine("## Cast of characters")
            castMembers.forEach { member ->
                appendLine("### ${member.name}${member.role?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}")
                appendLine("Description: ${member.description}")
                if (member.personality.isNotBlank()) appendLine("Personality: ${member.personality}")
                appendLine()
            }
            appendLine("Guidelines:")
            appendLine("- Clearly distinguish each character's voice, personality, and mannerisms")
            appendLine("- Use formatting to indicate who is speaking: **CharacterName:** \"dialogue\" or *CharacterName* does something")
            appendLine("- Make characters interact with each other, not only with the user — they can start conversations, disagree, react to one another")
            appendLine("- You may introduce brief, minor unnamed background characters if the scene calls for it, but keep the focus on the cast above")
        }
        appendLine()
        appendLine("Reply in ${appLanguageManager.getSelectedLanguage().nativeName} by default (the app's configured language), even if this sheet is written in a different language — but if the user writes in a different language, switch to match them instead.")

        if (persona != null) {
            appendLine()
            appendLine("## Character Sheet")
            appendLine("Description: ${persona.shortDescription}")
            appendLine("Personality: ${persona.personality}")
            val scenario = _activeScenario ?: persona.scenario
            appendLine("Scenario: $scenario")

            // Intériorité (2026-08-22). Section séparée et étiquetée plutôt que fondue dans
            // "Personality" : un modèle suit bien mieux « ne trahira jamais un allié » posé comme
            // une contrainte nommée que la même idée noyée dans un paragraphe de prose. Chaque
            // ligne n'est écrite que si elle est renseignée — un persona d'avant cette version, ou
            // un champ laissé vide par l'utilisateur, n'ajoute rien au prompt.
            val interiority = listOfNotNull(
                persona.desire.takeIf { it.isNotBlank() }?.let { "Wants: $it" },
                persona.fear.takeIf { it.isNotBlank() }?.let { "Fears: $it" },
                persona.flaw.takeIf { it.isNotBlank() }?.let { "Self-sabotages by: $it" },
                persona.moralLine.takeIf { it.isNotBlank() }?.let { "Will never: $it" },
                persona.secret.takeIf { it.isNotBlank() }?.let { "Hides: $it" }
            )
            if (interiority.isNotEmpty()) {
                appendLine()
                appendLine("## Character interiority")
                appendLine("- Let these drive what the character does, not what they announce. Never state them outright.")
                appendLine("- \"Will never\" is a hard line: under pressure the character refuses, deflects or pays a price — it does not bend for convenience.")
                appendLine("- \"Hides\" stays hidden until the story genuinely forces it out.")
                interiority.forEach { appendLine("- $it") }
            }

            if (persona.exampleDialogues.isNotBlank()) {
                appendLine()
                appendLine("## Example dialogues")
                appendLine(persona.exampleDialogues)
            }
        }

        val userProfile = userProfileStore.get()
        if (!userProfile.isBlank) {
            appendLine()
            appendLine("## User Profile")
            appendLine("You are talking to a real user with the following profile (address them accordingly):")
            val fullName = listOf(userProfile.firstName, userProfile.lastName).filter { it.isNotBlank() }.joinToString(" ")
            if (fullName.isNotBlank()) appendLine("Name: $fullName")
            if (userProfile.pronoun.isNotBlank()) appendLine("Pronoun: ${userProfile.pronoun}")
            if (userProfile.age.isNotBlank()) appendLine("Age: ${userProfile.age}")
            if (userProfile.physicalDescription.isNotBlank()) appendLine("Physical description: ${userProfile.physicalDescription}")
            if (userProfile.sexualOrientation.isNotBlank()) appendLine("Sexual orientation: ${userProfile.sexualOrientation}")
        }

        if (loreEntries.isNotEmpty()) {
            appendLine()
            appendLine("## Known Entities (characters, places, factions, events, items)")
            appendLine("Persistent entity sheets for this story, ordered by how relevant they are to the current moment. Treat them as authoritative continuity: never contradict a fact stated here, and never reintroduce an entity listed here as if the user were meeting it for the first time.")
            val detailedCount = SummarizationConfig.detailedLoreEntries()
            loreEntries.forEachIndexed { index, entry ->
                appendLine("- **${entry.name}** (${entry.entryType}): ${entry.summary}")
                // The full `content` paragraph only for the best-ranked few — it is several times
                // longer than the summary line, and until now never reached the model at all even
                // though the extraction prompt explicitly asks for it.
                if (index < detailedCount && entry.content.isNotBlank() && entry.content != entry.summary) {
                    appendLine("  ${entry.content}")
                }
                // Posture relationnelle : où en est ce personnage vis-à-vis du joueur. Accolée à sa
                // fiche plutôt que regroupée ailleurs, pour que le modèle la lise au moment où il
                // lit le personnage.
                if (entry.stance.isNotBlank()) {
                    appendLine("  Toward the player: ${entry.stance}")
                }
            }
        }

        if (storyTimeAnchor.isNotBlank()) {
            appendLine()
            appendLine("## Current Story Time")
            appendLine("The current point in the story's internal timeline — use this as the authoritative anchor for elapsed time, dates, season, and time-of-day continuity. Never silently contradict, reset, or ignore it:")
            appendLine(storyTimeAnchor)
        }

        // Fils en suspens (2026-08-22). Le plus ancien d'abord : c'est celui que le modèle a perdu
        // de vue et que le lecteur, lui, n'a pas oublié. L'âge est marqué en toutes lettres parce
        // qu'un modèle ne perçoit aucune durée dans un transcript — sans ça, un fil de trente tours
        // et un fil du tour précédent lui paraissent aussi frais l'un que l'autre.
        if (memory.openThreads.isNotEmpty()) {
            appendLine()
            appendLine("## Unresolved threads")
            appendLine("Promises this story has made and not yet kept. Weave them back in when the scene allows; do not resolve them all at once, and do not invent a resolution that costs nothing.")
            memory.openThreads.forEach { thread ->
                val overdue = if (thread.ageTurns >= NarrativeThresholds.STALE_THREAD_TURNS) ", overdue" else ""
                appendLine("- **${thread.entry.name}** — planted ${thread.ageTurns} turns ago$overdue: ${thread.entry.summary}")
            }
        }

        if (memory.chronology.isNotEmpty()) {
            appendLine()
            appendLine("## Story Chronology")
            appendLine("Ordered ledger of what has already happened in this story, oldest first. This is the authoritative order of events: never reorder them, never write as if a listed event has not happened yet, and never invent an event that sits between two entries. The bracketed label is the story's own in-fiction reckoning of when the event happened, not a real-world date; when an entry has no label, its position in the list is still authoritative.")
            memory.chronology.forEach { appendLine(it) }
        }

        if (chatSummary.isNotBlank() || memory.chapters.isNotEmpty()) {
            appendLine()
            appendLine("## Story Summary")
            appendLine("The story so far, chapter by chapter. Everything here is already folded in and is not repeated in the messages below.")
            memory.chapters.forEach { chapter ->
                appendLine()
                appendLine("### Chapter ${chapter.chapterIndex} — ${chapter.title}")
                appendLine(chapter.summary)
            }
            if (chatSummary.isNotBlank()) {
                appendLine()
                if (memory.chapters.isNotEmpty()) appendLine("### Current chapter (in progress)")
                appendLine(chatSummary)
            }
        }

        if (relevantMemories.isNotEmpty()) {
            appendLine()
            appendLine("## Relevant Memories")
            appendLine("Potentially relevant memories from earlier in the story (only use what's actually relevant):")
            relevantMemories.forEach { memory -> appendLine("- $memory") }
        }
    }
}
