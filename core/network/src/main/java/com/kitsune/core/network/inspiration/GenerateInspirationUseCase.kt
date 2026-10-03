package com.kitsune.core.network.inspiration

import android.util.Log
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.json.AiJsonParser
import com.kitsune.core.network.json.AiJsonParser.stringField
import com.kitsune.core.network.repository.GenerationParsingException
import com.kitsune.core.network.repository.SamplingProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.security.locale.AppLanguageManager
import javax.inject.Inject

private const val TAG = "GenerateInspirationUC"

/** What the inspiration wizard is building towards — persona/universe creation ("Manque
 * d'inspiration ?" on the home screen), or a single image's description (same button, reused on
 * the image generation screens). */
enum class InspirationTarget { PERSONA, UNIVERSE, IMAGE }

/** One question/answer pair from the wizard. [answer] is blank when the user tapped "Je n'ai pas
 * d'idée" instead of writing a free answer — the next question is expected to try a different,
 * easier angle (e.g. offering a couple of illustrative directions) rather than repeating itself. */
data class InspirationAnswer(val question: String, val answer: String)

/** Number of questions asked before synthesizing a description — fixed rather than AI-decided, to
 * keep the wizard's cost/length predictable (same philosophy as GenerateQuickPersonaUseCase making
 * exactly one LLM call: bounded, never open-ended). */
const val INSPIRATION_QUESTION_COUNT = 4

/**
 * One rough character idea, deliberately too small to be a persona.
 *
 * The point of a sketch is that it costs nothing to reject. A full generated sheet invites the user
 * to accept it because it looks finished and because producing it cost a credit; three lines invite
 * them to say "closer, but she should be older" — which is the actual conversation the exploration
 * mode exists to have.
 */
data class PersonaSketch(val name: String, val concept: String, val hook: String)

/** One round of the exploration loop: the sketch the user picked, and what they asked to change. */
data class SketchChoice(val sketch: PersonaSketch, val note: String)

/** Sketches offered per round. Enough to see real spread, few enough to read on a phone. */
const val SKETCH_PROPOSAL_COUNT = 4

/**
 * Rounds allowed before the loop insists on a decision.
 *
 * Free to the user (`INSPIRATION`), which is exactly why it needs a bound: an unbounded refinement
 * loop costs the operator on every round, and a user who has narrowed six times is no longer
 * exploring — the sketches have converged and another round returns near-identical ideas.
 */
const val SKETCH_MAX_ROUNDS = 6

private fun questionSystemPrompt(target: InspirationTarget) = """
You are a creative interviewer helping a user who wants ${targetGoal(target)} but doesn't know yet
exactly what they're after. Your job is to ask exactly ONE short, open, inviting question at a time
to help them figure out what they're in the mood for — never the whole list at once.
Rules:
- Ask about a genuinely different angle than any question already asked in this conversation
  (${targetAngles(target)} — pick whichever hasn't been covered yet and feels like a natural next
  step given their previous answers).
- Build on what they've already told you when relevant, to make the question feel personal rather
  than generic.
- If their most recent answer indicates they have no clear idea (marked "(no answer — no clear idea
  yet)"), make your next question easier: offer 2-3 short illustrative example directions inline so
  they can just pick one or riff on it, rather than asking another wide-open question.
- Keep it short and casual, one or two sentences, like a friendly interviewer, not a form.
- Reply with the question text only — no preamble, no quotation marks, no numbering.
"""

private fun synthesisSystemPrompt(target: InspirationTarget) = """
You are a creative assistant. Below is a short Q&A interview with a user about ${targetGoal(target)}.
Some answers may be blank/vague ("no clear idea") — for those, invent something coherent and
appealing yourself rather than leaving a gap. For every answer that does express a real preference,
honor it faithfully.
Write a single rich, vivid creative brief as ONE natural free-text paragraph — written as if the
user had typed this description themselves — ready to hand directly to ${targetConsumer(target)}.
Do not restate the Q&A format, do not use headings or bullet points, do not mention that this came
from an interview. Reply with the paragraph text only.
"""

private fun sketchSystemPrompt(target: InspirationTarget, count: Int) = """
You help a user who wants ${targetGoal(target)} but has only a vague idea, by proposing rough concepts
they can react to. Propose exactly $count of them.
Respond with a single JSON object only, no markdown, of the form:
{"proposals":[{"name":"...","concept":"...","hook":"..."}, ...]}
Rules:
- "name" is a short name or title, at most 40 characters.
- "concept" is ONE sentence saying who or what this is — concrete, not a genre label.
- "hook" is ONE sentence saying why it is interesting to play with: a tension, a situation, a
  contradiction. Never a restatement of the concept.
- The $count proposals must differ from EACH OTHER along different axes — not one idea in four
  costumes. Vary what makes each one interesting: for one it is the occupation, for another the
  relationship to the player, for another the situation, for another a contradiction they carry.
- Deliberately avoid the most predictable reading of the request. If an idea would occur to anyone
  within five seconds, take the less obvious one instead.
- These are rough ideas, not finished sheets: keep every field to one short sentence.
- When the user has already picked earlier favourites, treat those as the direction to move toward,
  and their notes as instructions. Keep exploring around that direction — do not simply return the
  same idea reworded, and do not drift back to concepts they have already passed over.
- Reply with the JSON object only.
"""

private fun targetGoal(target: InspirationTarget) = when (target) {
    InspirationTarget.PERSONA -> "to create a character (persona) for a roleplay chat"
    InspirationTarget.UNIVERSE -> "to create a setting/universe for a roleplay chat"
    InspirationTarget.IMAGE -> "to generate a single image of their character(s)"
}

private fun targetAngles(target: InspirationTarget) = when (target) {
    InspirationTarget.PERSONA, InspirationTarget.UNIVERSE ->
        "tone/atmosphere, the kind of relationship or dynamic they want with the character(s), a " +
            "setting or time period, a distinctive hook or twist, physical/personality vibe"
    InspirationTarget.IMAGE ->
        "the mood/atmosphere of the image, the pose or action being captured, the setting/location, " +
            "the framing or camera angle, the lighting or color palette, what the character is wearing"
}

private fun targetConsumer(target: InspirationTarget) = when (target) {
    InspirationTarget.PERSONA -> "a character generator"
    InspirationTarget.UNIVERSE -> "a setting generator"
    InspirationTarget.IMAGE -> "an image generator, as the image's description field"
}

/**
 * Powers the "Manque d'inspiration ?" button (persona/universe creation entry points, and the
 * image generation screens): a short AI-guided Q&A that helps an undecided user land on a rich
 * [description] string, which is then handed unchanged to the existing generators
 * (`GenerateQuickPersonaUseCase`/`GenerateWorldElementUseCase.generateUniverseBundle`/the image
 * description field) exactly as if the user had typed it themselves — this use case never touches
 * actual character/universe/image generation, only the brainstorming step before it. Free for the
 * user (`CostCalculator.FREE_OPERATION_TYPES`, `operationType = "INSPIRATION"`), unlike the
 * generation that follows.
 */
class GenerateInspirationUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver,
    private val appLanguageManager: AppLanguageManager
) {
    private val languageSuffix: String
        get() = "\nWrite in ${appLanguageManager.getSelectedLanguage().nativeName} by default — " +
            "unless the user's previous answers are clearly written in a different language, in " +
            "which case match that language instead."

    suspend fun nextQuestion(target: InspirationTarget, history: List<InspirationAnswer>): Result<String> {
        Log.d(TAG, "nextQuestion: target=$target, historySize=${history.size}")
        val completion = chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.INSPIRATION),
            systemPrompt = questionSystemPrompt(target).trim() + languageSuffix,
            messages = history.toChatTurns(),
            operationType = "INSPIRATION",
            maxTokens = ChatCompletionRepository.MEMORY_MAX_TOKENS,
            allowContinuation = false
        )
        return completion.map { it.content.trim().trim('"') }
            .onFailure { e -> Log.e(TAG, "nextQuestion: LLM call failed: ${e::class.simpleName}: ${e.message}", e) }
    }

    suspend fun synthesizeDescription(target: InspirationTarget, history: List<InspirationAnswer>): Result<String> {
        Log.d(TAG, "synthesizeDescription: target=$target, historySize=${history.size}")
        val completion = chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.INSPIRATION),
            systemPrompt = synthesisSystemPrompt(target).trim() + languageSuffix,
            messages = history.toChatTurns(),
            operationType = "INSPIRATION",
            maxTokens = ChatCompletionRepository.GENERATION_MAX_TOKENS,
            allowContinuation = false
        )
        return completion.map { it.content.trim() }
            .onFailure { e -> Log.e(TAG, "synthesizeDescription: LLM call failed: ${e::class.simpleName}: ${e.message}", e) }
    }

    /**
     * Proposes several rough concepts at once, for the exploration mode of the wizard (2026-08-24).
     *
     * ## Why one call for all of them
     *
     * The persona generator asks for N proposals with N independent calls, and none of them can see
     * its siblings — which is why they converge on the same modal character however firmly they are
     * told to differ. Here the whole set comes back from a single call, so the model is actually
     * *looking at* the other three when it writes the fourth. That is the strongest available fix for
     * "everything looks alike", and it happens to be the cheapest: one call instead of four.
     *
     * ## Why sketches and not personas
     *
     * A generated sheet is expensive to produce, looks finished, and therefore gets accepted. Three
     * lines get rejected freely, which is what makes a refinement loop work at all.
     *
     * @param brief the user's own vague description, in their words.
     * @param history rounds already played — each the sketch the user liked and what they asked to
     *   change. Sent as real conversation turns so the model treats them as a direction to move
     *   toward rather than as a list of constraints to satisfy literally.
     */
    suspend fun sketchProposals(
        target: InspirationTarget,
        brief: String,
        history: List<SketchChoice> = emptyList()
    ): Result<List<PersonaSketch>> {
        if (brief.isBlank()) return Result.failure(IllegalArgumentException("Describe what you want first"))

        Log.d(TAG, "sketchProposals: target=$target, round=${history.size}, briefLength=${brief.length}")
        val turns = buildList {
            add(ChatTurn(role = ChatMessageDto.ROLE_USER, content = brief))
            history.forEach { choice ->
                add(
                    ChatTurn(
                        role = ChatMessageDto.ROLE_ASSISTANT,
                        content = "${choice.sketch.name} — ${choice.sketch.concept}"
                    )
                )
                add(
                    ChatTurn(
                        role = ChatMessageDto.ROLE_USER,
                        content = "I like this one. " + choice.note.ifBlank {
                            "Keep going in this direction, but show me other takes on it."
                        }
                    )
                )
            }
        }

        val completion = chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.INSPIRATION),
            systemPrompt = sketchSystemPrompt(target, SKETCH_PROPOSAL_COUNT).trim() + languageSuffix,
            messages = turns,
            operationType = "INSPIRATION",
            maxTokens = ChatCompletionRepository.GENERATION_MAX_TOKENS,
            allowContinuation = false,
            // Exploration is the one place where an unexpected idea is the product rather than a
            // defect, so this decodes hotter than the interview mode and carries a real presence
            // penalty to keep the four proposals off each other's vocabulary.
            sampling = SamplingProfile(temperature = 1.15, presencePenalty = 0.6, frequencyPenalty = 0.3)
        )
        val result = completion.getOrElse { e ->
            Log.e(TAG, "sketchProposals: LLM call failed: ${e::class.simpleName}: ${e.message}", e)
            return Result.failure(e)
        }

        return try {
            val json = AiJsonParser.parseObject(result.content, knownKeys = listOf("proposals"))
            val sketches = json["proposals"]?.jsonArray.orEmpty().mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val name = obj.stringField("name").trim()
                val concept = obj.stringField("concept").trim()
                if (name.isBlank() && concept.isBlank()) null
                else PersonaSketch(name = name, concept = concept, hook = obj.stringField("hook").trim())
            }
            if (sketches.isEmpty()) {
                // A syntactically valid object with nothing usable in it is a parsing failure from the
                // caller's point of view, and must not present as an empty, un-actionable screen.
                Log.e(TAG, "sketchProposals: response parsed but held no usable proposal")
                Result.failure(
                    GenerationParsingException(
                        "Réponse de l'IA invalide : aucune proposition.",
                        IllegalStateException("empty proposals array")
                    )
                )
            } else {
                Result.success(sketches)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sketchProposals: response received but failed to parse (contentLength=${result.content.length})", e)
            Result.failure(GenerationParsingException("Réponse de l'IA invalide : ${e.message}", e))
        }
    }

    private fun List<InspirationAnswer>.toChatTurns(): List<ChatTurn> = flatMap { qa ->
        listOf(
            ChatTurn(role = ChatMessageDto.ROLE_ASSISTANT, content = qa.question),
            ChatTurn(
                role = ChatMessageDto.ROLE_USER,
                content = qa.answer.ifBlank { "(no answer — no clear idea yet)" }
            )
        )
    }
}
