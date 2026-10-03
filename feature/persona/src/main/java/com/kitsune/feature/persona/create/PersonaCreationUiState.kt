package com.kitsune.feature.persona.create

import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.network.visualsheet.PersonaVisualSheet
import com.kitsune.core.network.persona.PersonaDraft

sealed interface PersonaCreationUiState {
    /** Shown instead of the normal flow when this would be the user's very first persona and
     * their profile (`UserProfileStore`) is still blank — see `PersonaCreationViewModel.init`. */
    data object RequiresProfileSetup : PersonaCreationUiState

    data object DescribeInput : PersonaCreationUiState
    data class Generating(val count: Int) : PersonaCreationUiState
    data class Scheduled(val jobId: String, val description: String) : PersonaCreationUiState
    data class GenerationError(val message: String) : PersonaCreationUiState

    /** Browsing several complete proposals ("comme des fiches") before picking one (or several,
     * "comme avec les univers" — demande explicite) to review — only reached when more than one
     * was requested; a single proposal skips straight to [Review], same as before this feature
     * existed. [isPreparingSelection] covers the brief visual-sheet generation for whichever
     * proposal gets picked (generated lazily, only for the one chosen). [savedIndices] tracks
     * which proposals have already been saved as real personas, both to show the user which cards
     * are already done and to guard against saving the same card twice. */
    data class Browsing(
        val proposals: List<PersonaDraft>,
        val currentIndex: Int,
        val isPreparingSelection: Boolean = false,
        val savedIndices: Set<Int> = emptySet()
    ) : PersonaCreationUiState

    data class Review(
        val draft: PersonaDraft,
        /** Editable — seeded from AI generation, reused verbatim in every future image for this persona (FEATURES.md section 5). */
        val visualSheet: PersonaVisualSheet,
        val age: String = "",
        val maturityTags: Set<MaturityTag> = setOf(MaturityTag.SFW),
        /** Editable — seeded from the AI's suggested tags, same free-form list as `PersonaEntity.tags`. */
        val tags: List<String> = draft.tags,
        val isSaving: Boolean = false,
        val error: String? = null,
        /** Set only when reached from [Browsing] with more than one proposal — unlike the
         * single-proposal path (where saving exits the whole creation flow via `onSaved`), a
         * successful save here returns to [Browsing] instead (with this proposal's index added to
         * its `savedIndices`), so the user can review and save more than one persona from the same
         * batch, same as universe creation. */
        val sourceBrowsing: Browsing? = null
    ) : PersonaCreationUiState
}
