package com.kitsune.feature.persona.tonelibrary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.model.CreateListingRequest
import com.kitsune.core.backend.model.MarketplacePresetPackData
import com.kitsune.core.backend.model.MarketplaceStoryPresetData
import com.kitsune.core.data.local.entities.IntensityMode
import com.kitsune.core.data.local.entities.ToneCardEntity
import com.kitsune.core.data.repository.ToneCardRepository
import com.kitsune.feature.persona.detail.ToneCardDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * The user's own library of story tones (2026-08-24).
 *
 * Same entity and same editor as a persona's tone cards, at a third scope: both foreign keys null.
 * The distinction is who the register belongs to — a persona card says "this is how *she* is best
 * played", a profile card says "this is how *I* like stories told" — and the second was worth adding
 * because otherwise the answer to "I always want plot twists and new faces" is to re-author it on
 * every character the player owns.
 *
 * Lives in `feature:persona` rather than `feature:settings` because that is where `ToneCardDialog`
 * already is, and `feature → feature` is forbidden. Settings reaches it through a navigation callback
 * wired in `app`, the same way it already reaches the store and the proposals screen.
 */
@HiltViewModel
class ToneLibraryViewModel @Inject constructor(
    private val toneCardRepository: ToneCardRepository,
    private val backendClient: KitsuneBackendClient
) : ViewModel() {

    /** Sharing needs the marketplace; the share action is hidden while it is switched off. */
    val marketplaceEnabled: StateFlow<Boolean> = backendClient.enabled

    /**
     * Publishes [cards] as a preset pack (listing type `PRESET_PACK`). Calls back with `true` when the
     * pack went live, `false` when it waits in the review queue, or an exception message.
     */
    fun publishPack(title: String, description: String, cards: List<ToneCardEntity>, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val request = CreateListingRequest(
                type = "PRESET_PACK",
                title = title.trim(),
                description = description.trim(),
                maturityRating = if (cards.any { it.intensityMode == IntensityMode.INTENSE_MATURE }) "NSFW" else "SFW",
                presetData = MarketplacePresetPackData(presets = cards.map { it.toPresetData() })
            )
            onResult(backendClient.createListing(request).map { !it.inReview })
        }
    }

    val toneCards: StateFlow<List<ToneCardEntity>> = toneCardRepository.observeProfileCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Creates or replaces a profile tone.
     *
     * Both scope columns are left null on purpose — that *is* what makes this a profile card, and it
     * is why editing one here can never accidentally reattach it to whichever persona was last open.
     */
    fun save(existingId: String?, draft: ToneCardDraft) {
        viewModelScope.launch {
            toneCardRepository.upsert(
                ToneCardEntity(
                    id = existingId ?: UUID.randomUUID().toString(),
                    personaId = null,
                    universeId = null,
                    name = draft.name,
                    description = draft.description,
                    directive = draft.directive,
                    storyPaceMode = draft.storyPaceMode,
                    toneMode = draft.toneMode,
                    involvementMode = draft.involvementMode,
                    narrativeRhythmMode = draft.narrativeRhythmMode,
                    universeMode = draft.universeMode,
                    intensityMode = draft.intensityMode,
                    replyLength = draft.replyLength,
                    narrationBalance = draft.narrationBalance,
                    voiceMode = draft.voiceMode,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun delete(card: ToneCardEntity) {
        viewModelScope.launch { toneCardRepository.delete(card) }
    }
}

/** A tone card as a shareable story preset. The base preset is not sent: the receiving install's
 *  catalogue may differ, and the stored mode values are what the card actually means. */
internal fun ToneCardEntity.toPresetData() = MarketplaceStoryPresetData(
    name = name,
    description = description,
    storyPace = storyPaceMode.name,
    tone = toneMode.name,
    involvement = involvementMode.name,
    rhythm = narrativeRhythmMode.name,
    universe = universeMode.name,
    intensity = intensityMode.name,
    replyLength = replyLength.name,
    narrationBalance = narrationBalance.name,
    voice = voiceMode.name,
    directive = directive
)
