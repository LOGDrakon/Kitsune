package com.kitsune.app.inspiration

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.app.R
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.core.network.inspiration.GenerateInspirationUseCase
import com.kitsune.core.network.inspiration.INSPIRATION_QUESTION_COUNT
import com.kitsune.core.network.inspiration.InspirationAnswer
import com.kitsune.core.network.inspiration.PersonaSketch
import com.kitsune.core.network.inspiration.SKETCH_MAX_ROUNDS
import com.kitsune.core.network.inspiration.SketchChoice
import com.kitsune.core.network.inspiration.InspirationTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface InspirationWizardUiState {
    /**
     * The fork in the road, shown first (2026-08-24).
     *
     * There are two genuinely different ways of being stuck: knowing roughly what you want but not
     * the details — which the interview answers — and not knowing at all until you see something,
     * which it cannot. The wizard used to assume everyone was in the first case, and asked four
     * open questions of people who had nothing to answer them with.
     */
    data object ChoosingMode : InspirationWizardUiState

    data object LoadingQuestion : InspirationWizardUiState
    data class Asking(
        val questionIndex: Int,
        val totalQuestions: Int,
        val question: String,
        val answer: String
    ) : InspirationWizardUiState

    /** Exploration mode, first step: the user's own vague words, before anything is proposed. */
    data class Briefing(val brief: String) : InspirationWizardUiState

    data object LoadingSketches : InspirationWizardUiState

    /**
     * One round of rough proposals.
     *
     * [note] is what the user types alongside their pick, and it is the whole reason the loop
     * converges instead of wandering: "her, but older and less sure of herself" is the instruction
     * the next round is built on. [canRefine] goes false at the last allowed round, where the only
     * move left is to take one and build it.
     */
    data class Sketching(
        val round: Int,
        val sketches: List<PersonaSketch>,
        val selected: PersonaSketch?,
        val note: String,
        val canRefine: Boolean
    ) : InspirationWizardUiState

    data object Synthesizing : InspirationWizardUiState
    data class Error(val message: String) : InspirationWizardUiState
}

@HiltViewModel
class InspirationWizardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val generateInspirationUseCase: GenerateInspirationUseCase
) : ViewModel() {

    val target: InspirationTarget = savedStateHandle.get<String>("target")
        ?.let { runCatching { InspirationTarget.valueOf(it) }.getOrNull() }
        ?: InspirationTarget.PERSONA

    private val _state = MutableStateFlow<InspirationWizardUiState>(InspirationWizardUiState.ChoosingMode)
    val state: StateFlow<InspirationWizardUiState> = _state.asStateFlow()

    /** One-shot: the synthesized description, once ready — the screen navigates onward on seeing it. */
    private val _readyEvent = MutableStateFlow<String?>(null)
    val readyEvent: StateFlow<String?> = _readyEvent.asStateFlow()

    private val history = mutableListOf<InspirationAnswer>()

    /** Exploration mode: the user's opening words, and every round they have narrowed since. */
    private var brief: String = ""
    private val sketchHistory = mutableListOf<SketchChoice>()

    fun chooseInterviewMode() = loadNextQuestion()

    fun chooseExplorationMode() {
        _state.value = InspirationWizardUiState.Briefing(brief)
    }

    fun updateBrief(text: String) {
        val current = _state.value as? InspirationWizardUiState.Briefing ?: return
        _state.value = current.copy(brief = text)
    }

    /** Starts the exploration loop from the user's own words. */
    fun submitBrief() {
        val current = _state.value as? InspirationWizardUiState.Briefing ?: return
        brief = current.brief.trim()
        if (brief.isBlank()) return
        loadSketches()
    }

    fun selectSketch(sketch: PersonaSketch) {
        val current = _state.value as? InspirationWizardUiState.Sketching ?: return
        // Tapping the selected one again clears it, so a mis-tap is undoable without leaving the round.
        _state.value = current.copy(selected = if (current.selected == sketch) null else sketch)
    }

    fun updateSketchNote(text: String) {
        val current = _state.value as? InspirationWizardUiState.Sketching ?: return
        _state.value = current.copy(note = text)
    }

    /** "Closer, but…" — records the pick and asks for another round built around it. */
    fun refineFromSelection() {
        val current = _state.value as? InspirationWizardUiState.Sketching ?: return
        val selected = current.selected ?: return
        sketchHistory.add(SketchChoice(sketch = selected, note = current.note.trim()))
        loadSketches()
    }

    /**
     * "This one" — ends the wizard on the chosen sketch.
     *
     * The description is composed **locally**, with no further call: the sketch already is the concept
     * the user chose, and the persona generator that receives it will flesh it out anyway. Spending a
     * call to rewrite three lines they just approved into a paragraph would cost something and risk
     * drifting away from the thing they actually picked.
     */
    fun buildFromSelection() {
        val current = _state.value as? InspirationWizardUiState.Sketching ?: return
        val selected = current.selected ?: return
        _readyEvent.value = composeDescription(selected, current.note.trim())
    }

    private fun composeDescription(sketch: PersonaSketch, lastNote: String): String = buildString {
        if (sketch.name.isNotBlank()) append("${sketch.name}. ")
        append(sketch.concept)
        if (sketch.hook.isNotBlank()) append(" ${sketch.hook}")
        // Every note the user wrote along the way, including the one on their final pick. These are
        // their own words, and they are the part the sketches only ever partially absorbed.
        val notes = (sketchHistory.map { it.note } + lastNote).filter { it.isNotBlank() }
        if (notes.isNotEmpty()) append(" ${notes.joinToString(" ")}")
        if (brief.isNotBlank()) append(" ($brief)")
    }

    private fun loadSketches() {
        viewModelScope.launch {
            _state.value = InspirationWizardUiState.LoadingSketches
            generateInspirationUseCase.sketchProposals(target, brief, sketchHistory.toList())
                .onSuccess { sketches ->
                    _state.value = InspirationWizardUiState.Sketching(
                        round = sketchHistory.size,
                        sketches = sketches,
                        selected = null,
                        note = "",
                        canRefine = sketchHistory.size + 1 < SKETCH_MAX_ROUNDS
                    )
                }
                .onFailure { e ->
                    _state.value = InspirationWizardUiState.Error(
                        NetworkErrorMessages.forUser(e, appContext.getString(R.string.inspiration_wizard_error_fallback))
                    )
                }
        }
    }

    fun updateAnswer(text: String) {
        val current = _state.value as? InspirationWizardUiState.Asking ?: return
        _state.value = current.copy(answer = text)
    }

    /** [skip] records a blank answer ("je n'ai pas d'idée") instead of the typed text — the next
     * question is expected to offer easier, illustrative directions rather than repeat itself. */
    fun submitAnswer(skip: Boolean = false) {
        val current = _state.value as? InspirationWizardUiState.Asking ?: return
        history.add(InspirationAnswer(question = current.question, answer = if (skip) "" else current.answer.trim()))
        if (history.size >= INSPIRATION_QUESTION_COUNT) synthesize() else loadNextQuestion()
    }

    /** Retries whichever step last failed, in whichever mode the user is in. */
    fun retry() {
        if (brief.isNotBlank()) loadSketches()
        else if (history.size >= INSPIRATION_QUESTION_COUNT) synthesize()
        else loadNextQuestion()
    }

    private fun loadNextQuestion() {
        viewModelScope.launch {
            _state.value = InspirationWizardUiState.LoadingQuestion
            generateInspirationUseCase.nextQuestion(target, history)
                .onSuccess { question ->
                    _state.value = InspirationWizardUiState.Asking(
                        questionIndex = history.size,
                        totalQuestions = INSPIRATION_QUESTION_COUNT,
                        question = question,
                        answer = ""
                    )
                }
                .onFailure { e ->
                    _state.value = InspirationWizardUiState.Error(
                        NetworkErrorMessages.forUser(e, appContext.getString(R.string.inspiration_wizard_error_fallback))
                    )
                }
        }
    }

    private fun synthesize() {
        viewModelScope.launch {
            _state.value = InspirationWizardUiState.Synthesizing
            generateInspirationUseCase.synthesizeDescription(target, history)
                .onSuccess { description -> _readyEvent.value = description }
                .onFailure { e ->
                    _state.value = InspirationWizardUiState.Error(
                        NetworkErrorMessages.forUser(e, appContext.getString(R.string.inspiration_wizard_error_fallback))
                    )
                }
        }
    }
}
