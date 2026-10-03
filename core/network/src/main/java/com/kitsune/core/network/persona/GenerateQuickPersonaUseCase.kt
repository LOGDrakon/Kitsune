package com.kitsune.core.network.persona

import android.util.Log
import com.kitsune.core.common.style.MAX_NAME_LENGTH
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.json.AiJsonParser
import com.kitsune.core.network.json.AiJsonParser.stringField
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.SamplingProfile
import com.kitsune.core.network.repository.ChatTurn
import com.kitsune.core.network.repository.GenerationParsingException
import com.kitsune.core.network.visualsheet.PersonaVisualSheet
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.profile.UserProfileStore
import javax.inject.Inject

private const val TAG = "GenerateQuickPersonaUC"

/**
 * Named directions handed out one per proposal, so a batch explores instead of converging.
 *
 * Each names a *different part of the character to make the interesting one*. That matters more than
 * it sounds: told only to be creative, a model reliably makes the personality the distinctive part
 * and leaves everything else generic, which is why five proposals used to read as five moods of the
 * same person. Told to make the occupation strange, or the relationship to the player strange, it has
 * to move a different lever.
 *
 * Deliberately about structure, not content: none of these names a genre, an era or an archetype, so
 * they compose with whatever the user actually asked for instead of overriding it.
 */
private val PROPOSAL_ANGLES = listOf(
    "make the occupation or role the unusual part, and keep the personality grounded.",
    "make the relationship to the player the unusual part — how they already know each other, or why they cannot simply be friendly.",
    "make the setting or circumstances the unusual part, and let an ordinary person react to them.",
    "make the personality the unusual part — a contradiction they carry, a way of speaking nobody else has.",
    "make what they want the unusual part: give them a goal that has nothing to do with the player, and let the player complicate it.",
    "take the most obvious reading of the request and deliberately invert one of its assumptions."
)

/** Generation used the repository default (0.9) until 2026-08-24. See `samplingFor`. */
private const val GENERATION_BASE_TEMPERATURE = 1.0
private const val GENERATION_TEMPERATURE_STEP = 0.06
private const val GENERATION_MAX_TEMPERATURE = 1.2

private val KNOWN_KEYS = listOf(
    "name", "description", "personality", "scenario", "firstMessage", "exampleDialogues",
    "physicalTraits", "artStyle", "colorPalette", "defaultOutfit", "tags",
    // Intériorité dramatique (2026-08-22) — produite dans le MÊME appel que le reste de la fiche,
    // exactement comme la fiche visuelle et les tags : aucun crédit supplémentaire.
    "desire", "fear", "flaw", "moralLine", "secret"
)

private const val MAX_GENERATED_TAGS = 6

private val SYSTEM_PROMPT = """
You are a creative assistant that designs immersive roleplay characters for adult fiction.
From a short free-form description, invent a complete, coherent, and memorable character sheet, plus a
fixed visual reference sheet reused verbatim across every future image generation of this character.
Your goal is not merely to fill fields, but to create a character with identity, tension, flavor, and
emotional presence. Favor distinctive details, strong relational hooks, and a clear narrative presence
over generic completeness.
Respond with a single JSON object only, no markdown, using exactly these keys:
"name", "description", "personality", "scenario", "firstMessage", "exampleDialogues",
"physicalTraits", "artStyle", "colorPalette", "defaultOutfit", "tags",
"desire", "fear", "flaw", "moralLine", "secret".
Rules:
- Every field must be filled.
- "name" must be short — a real name or short title, at most $MAX_NAME_LENGTH characters, no epithets or subtitles.
- Write rich, specific content: several sentences for "description", "personality" and "scenario".
- The five interiority fields are what make the character act like a person rather than recite traits.
  Each is ONE short, concrete sentence — never a list, never abstract virtues:
  "desire": what they actively want right now and would take risks for. Something pursuable in scenes,
    not a vague aspiration ("prove her brother didn't desert" — not "happiness").
  "fear": what they avoid, and what they do when cornered by it.
  "flaw": how they sabotage themselves — a behaviour that creates trouble, not a cute quirk.
  "moralLine": the one thing they will NOT do, whatever the pressure. This is what makes a refusal feel
    earned instead of arbitrary, so make it specific and costly to hold.
  "secret": something concrete they hide, that could plausibly surface later and change the scene.
- These five must be in tension with each other and with "personality": a desire that the fear opposes,
  a flaw that endangers the moral line. A character whose traits all point the same way is inert.
- Actively avoid the most predictable, overused options: stock tropes ("mysterious past", "confident
  smirk", "guarded but secretly soft"), the single most common descriptor for any given trait (e.g.
  "piercing blue eyes", "raven hair", "tall dark and handsome"), and generic archetypes. When several
  ideas come to mind, deliberately pick the less obvious, more specific one — an unusual combination of
  traits, an unexpected profession or background detail, a distinctive verbal tic or habit.
- "firstMessage" opens the story already in motion, not a greeting or a self-introduction: ground it in
  a concrete physical scene (where the character is, what they're doing, a sensory detail of the
  moment), have the character take an active first move that directly involves the player — approach
  them, address them, hand them something, interrupt what they're doing — rather than passively waiting
  or describing themselves, and end on an open beat (a question, an unfinished action, a charged pause)
  that invites the player to respond, never a closed statement that could just as well end the scene.
- "exampleDialogues" must demonstrate the character's voice, emotional range, and interaction style.
- Never write a short, generic, or lazy answer: treat this request with exactly the same care and length
  whether it's the only character asked for or one of several alternatives requested together.
- Make the character feel distinct, story-ready, and easy to roleplay with.
- Never mention or imply an age for the character; age is set separately by the user.
- Tailor the character's appeal, relationship dynamics, and romantic/sexual framing to the player's
  sexual orientation if provided.
- "physicalTraits" must be concrete and specific: build, height, hair color/style, eye color, skin tone,
  distinguishing features — consistent with "description"/"personality" above, and specific rather than
  the first cliché that comes to mind (see the anti-cliché rule above).
- "artStyle" must be a reusable rendering style that is visually coherent and aesthetically strong (e.g.
  "semi-realistic digital painting, soft cinematic lighting").
- "colorPalette" must be a small set of 3 to 5 colors that reinforce the character's identity.
- "defaultOutfit" must be a specific, repeatable outfit that fits the character and their world.
- "tags" is a comma-separated list of $MAX_GENERATED_TAGS or fewer short, lowercase, relevant tags
  describing the character/story (genre, dynamic, tone — e.g. "romance, slow-burn, found-family,
  workplace, forbidden-love"). Single words or short hyphenated phrases only, no sentences.
Prioritize coherence across all fields: the visual identity, personality, scenario, and dialogue should
feel like the same person.
"""

/** Powers the "quick persona" creation flow (FEATURES.md section 6): a short description in, a full sheet out. */
class GenerateQuickPersonaUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver,
    private val userProfileStore: UserProfileStore,
    private val appLanguageManager: AppLanguageManager
) {
    private val orientationHint: String
        get() = userProfileStore.get().sexualOrientation.takeIf { it.isNotBlank() }?.let { "Player's sexual orientation: $it." } ?: ""

    private val languageHint: String
        get() = "Write every field in ${appLanguageManager.getSelectedLanguage().nativeName} by default — unless " +
            "the user's description below is clearly written in a different language, in which case match that " +
            "language instead."

    /**
     * [proposalIndex]/[proposalCount] identify this call among several sibling proposals requested
     * together (see `GenerationWorker.doGeneratePersona`): asked the exact same prompt several times
     * in a row with no distinguishing signal, models tend to phone in a much thinner answer on the
     * 2nd/3rd try. Naming the alternative explicitly pushes back against that.
     *
     * Deliberately makes exactly one LLM call, never more: a previous version silently retried with
     * a "reinforced" prompt whenever the first draft looked thin (some fields empty) as "cheap
     * insurance" — except it wasn't cheap. The backend bills per successful call (charge-on-success,
     * no concept of "this one was just a retry"), so a thin-but-successful first draft followed by a
     * successful reinforced retry billed the user twice for one persona, invisibly (BUGS.md). If the
     * model phones it in, the user gets the thin draft back and can regenerate deliberately (a
     * normal, transparently-billed action) rather than being charged a second time without knowing.
     */
    suspend operator fun invoke(
        description: String,
        proposalIndex: Int = 0,
        proposalCount: Int = 1,
        alreadyProposed: List<String> = emptyList()
    ): Result<PersonaDraft> {
        if (description.isBlank()) return Result.failure(IllegalArgumentException("Describe the persona first"))

        Log.d(TAG, "invoke: proposal ${proposalIndex + 1}/$proposalCount, descriptionLength=${description.length}, siblings=${alreadyProposed.size}")
        val userMessage = buildUserMessage(description, proposalIndex, proposalCount, alreadyProposed)
        val result = generateOnce(userMessage, proposalIndex)
        if (result.getOrNull()?.looksThin == true) {
            Log.w(TAG, "invoke: proposal ${proposalIndex + 1}/$proposalCount looks thin (some fields empty) — returning as-is, no automatic paid retry")
        }
        return result
    }

    private suspend fun generateOnce(userMessage: String, proposalIndex: Int): Result<PersonaDraft> {
        val completion = chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.QUICK_GENERATION),
            systemPrompt = buildSystemPrompt(),
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = userMessage)),
            operationType = "PERSONA_GEN",
            maxTokens = ChatCompletionRepository.GENERATION_MAX_TOKENS,
            allowContinuation = false,
            sampling = samplingFor(proposalIndex)
        )
        val result = completion.getOrElse { e ->
            // Failed before/during the LLM call itself (network error, insufficient credits, content
            // policy) — propagated as-is so the caller (GenerationWorker) can classify it correctly;
            // none of these bill a credit (see GenerationFailureCategory's doc comment).
            Log.e(TAG, "generateOnce: LLM call failed: ${e::class.simpleName}: ${e.message}", e)
            return Result.failure(e)
        }

        return try {
            val json = AiJsonParser.parseObject(result.content, knownKeys = KNOWN_KEYS)
            val draft = PersonaDraft(
                name = json.stringField("name").take(MAX_NAME_LENGTH).trim(),
                description = json.stringField("description"),
                personality = json.stringField("personality"),
                // stringField renvoie "" pour une clé absente : un modèle qui omet ces champs produit
                // une fiche sans intériorité, jamais une exception.
                desire = json.stringField("desire"),
                fear = json.stringField("fear"),
                flaw = json.stringField("flaw"),
                moralLine = json.stringField("moralLine"),
                secret = json.stringField("secret"),
                scenario = json.stringField("scenario"),
                firstMessage = json.stringField("firstMessage"),
                exampleDialogues = json.stringField("exampleDialogues"),
                visualSheet = PersonaVisualSheet(
                    physicalTraits = json.stringField("physicalTraits"),
                    artStyle = json.stringField("artStyle"),
                    colorPalette = json.stringField("colorPalette"),
                    defaultOutfit = json.stringField("defaultOutfit")
                ),
                tags = json.stringField("tags")
                    .split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .take(MAX_GENERATED_TAGS)
            )
            Log.d(TAG, "generateOnce: parsed draft successfully")
            Result.success(draft)
        } catch (e: Exception) {
            // The LLM DID respond successfully here — this failure is purely on the app's parsing
            // side, after the backend already billed the credit for this call. Never log the raw
            // generated persona content itself — only its length (compliance audit 2026-08-04).
            Log.e(TAG, "generateOnce: response received but failed to parse (contentLength=${result.content.length})", e)
            Result.failure(GenerationParsingException("Réponse de l'IA invalide : ${e.message}", e))
        }
    }

    private val PersonaDraft.looksThin: Boolean
        get() = description.isBlank() || personality.isBlank() || scenario.isBlank() || firstMessage.isBlank()

    /**
     * Decoder settings for one proposal.
     *
     * Generation ran on the repository default (0.9, no penalties) until 2026-08-24 — a sensible
     * temperature for *one* answer and the wrong one for several, since the whole point of asking for
     * five is that they differ. The presence penalty pushes each draft off vocabulary it has already
     * reached for, and the temperature climbs slightly with the index so the later proposals — the
     * ones a model most tends to phone in — get the most latitude.
     *
     * Capped well below where a JSON contract starts breaking: this call must still return a
     * parseable object, so the ceiling is lower than a prose reply could afford.
     */
    internal fun samplingFor(proposalIndex: Int): SamplingProfile = SamplingProfile(
        temperature = (GENERATION_BASE_TEMPERATURE + proposalIndex * GENERATION_TEMPERATURE_STEP)
            .coerceAtMost(GENERATION_MAX_TEMPERATURE),
        // Lowered with the chat presets on 2026-09-01, same reasoning: this band was guessed, not
        // measured, and the measurement came back badly.
        presencePenalty = 0.35,
        frequencyPenalty = 0.2
    )

    /**
     * Builds the differentiating instruction for one proposal among several.
     *
     * ## What was wrong before
     *
     * Every call was told to "invent a genuinely different character concept from the others" — an
     * instruction no call could follow, because each is an independent request that never sees its
     * siblings. Asked to differ from something it cannot observe, a model can only be *generically*
     * different, which lands it on the same modal answer every time. That is precisely the "they all
     * look alike" complaint.
     *
     * ## What replaces it
     *
     * Two things a call can actually act on:
     *
     * 1. **[alreadyProposed]** — one-line concepts of the drafts produced before this one. The worker
     *    generates sequentially, so this costs nothing but a few tokens and turns an unfollowable
     *    instruction into a concrete list of territory to avoid.
     * 2. **A prescribed angle** — a named direction per index, so even the first proposal (which has
     *    no siblings yet) is pushed somewhere specific rather than toward the average. "Make their
     *    occupation the strange part" produces a different character than "be interesting" does.
     */
    internal fun buildUserMessage(
        description: String,
        proposalIndex: Int,
        proposalCount: Int,
        alreadyProposed: List<String>
    ): String {
        if (proposalCount <= 1) return description
        val ordinal = proposalIndex + 1
        val angle = PROPOSAL_ANGLES[proposalIndex % PROPOSAL_ANGLES.size]
        return buildString {
            append(description)
            append("\n\n(This is alternative $ordinal of $proposalCount requested together. ")
            append("Angle for this one: $angle ")
            append("Change the archetype, the physical presentation, the personality axis and the ")
            append("opening situation — not just the name on the same underlying idea. Put the same ")
            append("care into it as if it were the only one asked for.")
            if (alreadyProposed.isNotEmpty()) {
                append("\n\nAlready proposed for this same request — do not repeat these, and do not ")
                append("produce a variation on them:\n")
                alreadyProposed.forEach { append("- $it\n") }
            }
            append(")")
        }
    }

    private fun buildSystemPrompt(): String {
        val base = "${SYSTEM_PROMPT.trim()}\n$languageHint"
        val hint = orientationHint
        return if (hint.isNotBlank()) "$base\n$hint" else base
    }
}
