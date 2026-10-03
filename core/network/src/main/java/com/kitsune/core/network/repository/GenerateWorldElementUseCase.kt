package com.kitsune.core.network.repository

import android.util.Log
import com.kitsune.core.common.style.MAX_NAME_LENGTH
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.json.AiJsonParser
import com.kitsune.core.network.json.AiJsonParser.stringField
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.profile.UserProfileStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

private const val TAG = "GenerateWorldElementUC"

data class UniverseDraft(
    val name: String,
    val description: String,
    val genre: String,
    val visualStyle: String,
    /** A handful of AI-suggested descriptive tags — editable by the user afterwards, same
     * free-form tags as [com.kitsune.core.data.local.entities.UniverseEntity.tags]. */
    val tags: List<String> = emptyList()
)
data class FactionDraft(val name: String, val description: String, val type: String, val alignment: String)
data class LocationDraft(val name: String, val description: String, val type: String)
data class NpcDraft(
    val name: String,
    val description: String,
    val personality: String,
    val role: String,
    /** Short appearance description (build, clothing style, distinguishing features — never age)
     * reused as image-generation context so this NPC looks consistent across separate generations. */
    val physicalDescription: String
)

/** A single complete, ready-to-play universe proposal (FEATURES.md section 6 — "génération multi-
 * propositions") : the universe itself plus a small starter cast, all generated together in one AI
 * call so the whole bundle stays thematically consistent. */
data class UniverseBundleDraft(
    val universe: UniverseDraft,
    val factions: List<FactionDraft>,
    val locations: List<LocationDraft>,
    val npcs: List<NpcDraft>
)

/** Description is optional everywhere here — a blank one falls back to "invent something yourself". */
private const val SURPRISE_ME_PROMPT = "Surprise me: invent something original and coherent yourself, no specific request given."

private const val MAX_GENERATED_TAGS = 6

private fun parseTags(raw: String): List<String> = raw
    .split(",")
    .map { it.trim().lowercase() }
    .filter { it.isNotBlank() }
    .take(MAX_GENERATED_TAGS)

private val UNIVERSE_KEYS = listOf("name", "description", "genre", "visualStyle", "tags")
private val FACTION_KEYS = listOf("name", "description", "type", "alignment")
private val LOCATION_KEYS = listOf("name", "description", "type")
private val NPC_KEYS = listOf("name", "description", "personality", "role", "physicalDescription")

private const val UNIVERSE_SYSTEM_PROMPT = """
You are a creative assistant that designs vivid settings and universes for adult roleplay fiction.
From a short free-form description, invent a setting that feels immediately usable for roleplay:
evocative, coherent, and rich in story potential.
Respond with a single JSON object only, no markdown, using exactly these keys:
"name", "description", "genre", "visualStyle", "tags".
Rules:
- Every field must be filled.
- "name" must be short, at most 40 characters, no subtitle.
- "description" should establish the tone, conflict, and roleplay potential in 1-3 strong sentences.
- "genre" should be concise and specific.
- "visualStyle" should give a clear visual identity that helps image generation and scene framing.
- "tags" is a comma-separated list of 6 or fewer short, lowercase, relevant tags describing the
  universe (genre, tone, themes — e.g. "dark-fantasy, political-intrigue, found-family").
- Favor strong atmosphere, memorable hooks, and usable narrative tension over generic worldbuilding.
"""

private val UNIVERSE_BUNDLE_KEYS = listOf("name", "description", "genre", "visualStyle", "tags", "factions", "locations", "npcs")

private const val UNIVERSE_BUNDLE_SYSTEM_PROMPT = """
You design universes for roleplay fiction. From a description, invent a coherent universe with 2
factions, 2 locations, and 3 NPCs that all feel like they belong to the same world. The result should
be compact but vivid: enough detail to inspire stories, while remaining easy to reuse in gameplay and
memory systems.
Reply with ONLY a JSON object, no markdown:
{"name":"...","description":"(2-3 sentences)","genre":"...","visualStyle":"...",
"tags":"(comma-separated, 6 or fewer short lowercase tags, e.g. dark-fantasy, political-intrigue)",
"factions":[{"name":"...","description":"(1-2 sentences)","type":"(one word)","alignment":"(short)"},{"name":"...","description":"...","type":"...","alignment":"..."}],
"locations":[{"name":"...","description":"(1-2 sentences)","type":"(one word)"},{"name":"...","description":"...","type":"..."}],
"npcs":[{"name":"...","description":"(1-2 sentences)","personality":"(short)","role":"(one word)","physicalDescription":"(short appearance, no age)"},{"name":"...","description":"...","personality":"...","role":"...","physicalDescription":"..."},{"name":"...","description":"...","personality":"...","role":"...","physicalDescription":"..."}]}
Rules:
- All "name" fields must be short, at most 40 characters, no subtitles.
- Keep descriptions concise but not bland: 1-2 sentences each, with concrete and story-relevant detail.
- Make every element distinct, flavorful, and mutually consistent with the others.
- Give each faction/location/NPC tension, function, and roleplay usefulness, not just a label.
- Never mention ages, including in physicalDescription.
"""

private fun factionSystemPrompt(universeContext: String) = """
You are a creative assistant that designs factions/organizations for adult roleplay fiction, set in this fictional universe:
$universeContext
From a short free-form description of the desired faction, invent a complete entry that feels
specific, playable, and consistent with that universe.
Respond with a single JSON object only, no markdown, using exactly these keys:
"name", "description", "type", "alignment".
"type" is a short label (e.g. guild, kingdom, cult, corporation); "alignment" is a short descriptor of its values/morality.
Rules:
- Every field must be filled.
- Keep the result concise but vivid.
- Make the faction useful for roleplay: give it a clear role in the universe, a relationship to the
  central conflict, and a distinctive visual or behavioral signature.
- Favor distinctive details over generic fantasy/scifi placeholders.
- Never mention ages.
"""

private fun locationSystemPrompt(universeContext: String) = """
You are a creative assistant that designs locations for adult roleplay fiction, set in this fictional universe:
$universeContext
From a short free-form description of the desired location, invent a complete entry that feels
specific, playable, and consistent with that universe.
Respond with a single JSON object only, no markdown, using exactly these keys:
"name", "description", "type".
"type" is a short label (e.g. city, forest, space station, tavern).
Rules:
- Every field must be filled.
- Keep the result concise but vivid.
- Make the location useful for roleplay: give it a clear role in the universe, a relationship to the
  central conflict, and a distinctive visual or behavioral signature.
- Favor distinctive details over generic fantasy/scifi placeholders.
- Never mention ages.
"""

private fun npcSystemPrompt(universeContext: String) = """
You are a creative assistant that designs secondary/non-player characters (NPCs) for adult roleplay fiction, set in this fictional universe:
$universeContext
From a short free-form description of the desired NPC, invent a complete entry that feels specific,
playable, and consistent with that universe.
Respond with a single JSON object only, no markdown, using exactly these keys:
"name", "description", "personality", "role", "physicalDescription".
"role" is their function in the story (e.g. innkeeper, rival, mentor, informant). "physicalDescription" is a short
appearance description (build, hair, clothing style, distinguishing features) used to keep this character's look
consistent across separately generated images.
Rules:
- Every field must be filled.
- Keep the result concise but vivid.
- Make the NPC useful for roleplay: give them a clear role in the universe, a relationship to the
  central conflict, and a distinctive visual or behavioral signature.
- Favor distinctive details over generic fantasy/scifi placeholders.
- Never mention or imply a specific age for the character anywhere, including in "physicalDescription".
"""

/**
 * Powers the "quick generate" buttons in universe creation (universe itself, factions, locations,
 * NPCs) — mirrors `feature:persona`'s `GenerateQuickPersonaUseCase` (same prompt/JSON-parsing
 * shape), except the description is optional here rather than required. Lives in `core:network`
 * (moved from `feature:universe`) so both `feature:universe` and `feature:chat` (dynamic mid-chat
 * NPC creation) can use it without a forbidden feature→feature dependency.
 */
class GenerateWorldElementUseCase @Inject constructor(
    private val chatCompletionRepository: ChatCompletionRepository,
    private val llmModelResolver: LlmModelResolver,
    private val userProfileStore: UserProfileStore,
    private val appLanguageManager: AppLanguageManager
) {
    private val orientationSuffix: String
        get() = userProfileStore.get().sexualOrientation.takeIf { it.isNotBlank() }?.let { "\nTailor characters, relationships and romantic/sexual dynamics to the player's sexual orientation: $it." } ?: ""

    private val languageSuffix: String
        get() = "\nWrite every field in ${appLanguageManager.getSelectedLanguage().nativeName} by default — " +
            "unless the description/context above is clearly written in a different language, in which case " +
            "match that language instead."

    suspend fun generateUniverse(description: String): Result<UniverseDraft> =
        generate(description, UNIVERSE_SYSTEM_PROMPT, UNIVERSE_KEYS) { json ->
            UniverseDraft(
                name = json.stringField("name").take(MAX_NAME_LENGTH).trim(),
                description = json.stringField("description"),
                genre = json.stringField("genre"),
                visualStyle = json.stringField("visualStyle"),
                tags = parseTags(json.stringField("tags"))
            )
        }

    /**
     * One complete, ready-to-play universe proposal (universe + 2 factions + 2 locations + 3 NPCs)
     * from a single AI call, so a whole batch of [count] independent proposals (see call sites in
     * `feature:universe`) costs exactly [count] calls rather than `count * (1 + 2 + 2 + 3)`.
     */
    suspend fun generateUniverseBundle(description: String): Result<UniverseBundleDraft> =
        generate(description, UNIVERSE_BUNDLE_SYSTEM_PROMPT, UNIVERSE_BUNDLE_KEYS,
            operationType = "UNIVERSE_GEN", maxTokens = ChatCompletionRepository.BUNDLE_MAX_TOKENS) { json ->
            UniverseBundleDraft(
                universe = UniverseDraft(
                    name = json.stringField("name").take(MAX_NAME_LENGTH).trim(),
                    description = json.stringField("description"),
                    genre = json.stringField("genre"),
                    visualStyle = json.stringField("visualStyle"),
                    tags = parseTags(json.stringField("tags"))
                ),
                factions = (json["factions"] as? JsonArray).orEmpty().mapNotNull { element ->
                    runCatching {
                        val obj = element.jsonObject
                        FactionDraft(
                            name = obj.stringField("name"),
                            description = obj.stringField("description"),
                            type = obj.stringField("type"),
                            alignment = obj.stringField("alignment")
                        )
                    }.getOrNull()
                },
                locations = (json["locations"] as? JsonArray).orEmpty().mapNotNull { element ->
                    runCatching {
                        val obj = element.jsonObject
                        LocationDraft(name = obj.stringField("name"), description = obj.stringField("description"), type = obj.stringField("type"))
                    }.getOrNull()
                },
                npcs = (json["npcs"] as? JsonArray).orEmpty().mapNotNull { element ->
                    runCatching {
                        val obj = element.jsonObject
                        NpcDraft(
                            name = obj.stringField("name"),
                            description = obj.stringField("description"),
                            personality = obj.stringField("personality"),
                            role = obj.stringField("role"),
                            physicalDescription = obj.stringField("physicalDescription")
                        )
                    }.getOrNull()
                }
            )
        }

    suspend fun generateFaction(universeContext: String, description: String): Result<FactionDraft> =
        generate(description, factionSystemPrompt(universeContext), FACTION_KEYS) { json ->
            FactionDraft(
                name = json.stringField("name"),
                description = json.stringField("description"),
                type = json.stringField("type"),
                alignment = json.stringField("alignment")
            )
        }

    suspend fun generateLocation(universeContext: String, description: String): Result<LocationDraft> =
        generate(description, locationSystemPrompt(universeContext), LOCATION_KEYS) { json ->
            LocationDraft(
                name = json.stringField("name"),
                description = json.stringField("description"),
                type = json.stringField("type")
            )
        }

    suspend fun generateNpc(universeContext: String, description: String): Result<NpcDraft> =
        generate(description, npcSystemPrompt(universeContext), NPC_KEYS) { json ->
            NpcDraft(
                name = json.stringField("name"),
                description = json.stringField("description"),
                personality = json.stringField("personality"),
                role = json.stringField("role"),
                physicalDescription = json.stringField("physicalDescription")
            )
        }

    private suspend fun <T> generate(
        description: String,
        systemPrompt: String,
        knownKeys: List<String>,
        operationType: String = "UNIVERSE_GEN",
        maxTokens: Int = ChatCompletionRepository.GENERATION_MAX_TOKENS,
        parse: (JsonObject) -> T
    ): Result<T> {
        val userPrompt = description.ifBlank { SURPRISE_ME_PROMPT }
        Log.d(TAG, "generate: operationType=$operationType, descriptionLength=${description.length}")
        val completion = chatCompletionRepository.complete(
            modelId = llmModelResolver.resolve(LlmOperation.QUICK_GENERATION),
            systemPrompt = systemPrompt.trim() + languageSuffix + orientationSuffix,
            messages = listOf(ChatTurn(role = ChatMessageDto.ROLE_USER, content = userPrompt)),
            operationType = operationType,
            maxTokens = maxTokens,
            allowContinuation = false
        )
        val result = completion.getOrElse { e ->
            // Failed before/during the LLM call itself — never bills a credit, see
            // GenerationFailureCategory's doc comment. Propagated as-is for GenerationWorker to classify.
            Log.e(TAG, "generate: LLM call failed (operationType=$operationType): ${e::class.simpleName}: ${e.message}", e)
            return Result.failure(e)
        }
        return try {
            val parsed = parse(AiJsonParser.parseObject(result.content, knownKeys))
            Log.d(TAG, "generate: parsed successfully (operationType=$operationType)")
            Result.success(parsed)
        } catch (e: Exception) {
            // The LLM DID respond successfully — this failure is purely on the app's parsing side,
            // after the provider already charged for this call. Never log the raw generated
            // world/universe content itself — only its length (compliance audit 2026-08-04).
            Log.e(TAG, "generate: response received but failed to parse (operationType=$operationType, contentLength=${result.content.length})", e)
            Result.failure(GenerationParsingException("Réponse de l'IA invalide : ${e.message}", e))
        }
    }
}
