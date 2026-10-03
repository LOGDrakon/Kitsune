package com.kitsune.core.data.card

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A character card, the de-facto interchange format of the AI roleplay world (2026-08-25).
 *
 * ## Why Kitsune reads someone else's format
 *
 * Chub, SillyTavern, Agnai and RisuAI all read the same file: a PNG carrying a `tEXt` chunk whose
 * value is base64-encoded JSON. Hundreds of thousands of cards exist. A new Kitsune user currently
 * faces an empty library and has to generate their first character; being able to open
 * a card they already have turns that into a non-question — and it costs nothing, happens offline,
 * and never touches a server, which is exactly the promise the rest of the app makes.
 *
 * ## Why the mapping is unusually good
 *
 * Most apps that import cards throw away the two most interesting fields because they have nowhere to
 * put them. Kitsune does not: `alternate_greetings` become entry scenes and `character_book` becomes
 * lore entries, both of which already exist here with their own screens.
 *
 * ## Tolerance is the design
 *
 * Every field defaults. Cards in the wild are written by hand, by a dozen different tools, across two
 * spec versions, and a card that is 90% readable must import 90% of the way rather than fail — losing
 * the lore is recoverable by hand, losing the character is not.
 */
@Serializable
data class CharacterCard(
    val name: String = "",
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    @SerialName("first_mes") val firstMessage: String = "",
    @SerialName("mes_example") val exampleDialogues: String = "",
    /** Author's system prompt. Kitsune lands this in a tone card rather than the system prompt: it
     *  describes how this character should be written, which is what a tone card is. */
    @SerialName("system_prompt") val systemPrompt: String = "",
    @SerialName("post_history_instructions") val postHistoryInstructions: String = "",
    /** Alternative openings — Kitsune's entry scenes, field for field. */
    @SerialName("alternate_greetings") val alternateGreetings: List<String> = emptyList(),
    @SerialName("character_book") val characterBook: CharacterBook? = null,
    val tags: List<String> = emptyList(),
    val creator: String = "",
    @SerialName("character_version") val characterVersion: String = ""
)

/** The card's embedded lorebook — Kitsune's lore entries. */
@Serializable
data class CharacterBook(
    val name: String = "",
    val entries: List<CharacterBookEntry> = emptyList()
)

/**
 * One lorebook entry.
 *
 * `keys` drives keyword activation in SillyTavern-style frontends. Kitsune retrieves lore
 * semantically and by name/alias instead, so the keys become the entry's **aliases** — which is the
 * closest honest equivalent and keeps them useful rather than discarding them.
 */
@Serializable
data class CharacterBookEntry(
    val keys: List<String> = emptyList(),
    val content: String = "",
    val name: String = "",
    val comment: String = "",
    val enabled: Boolean = true
)

/**
 * The envelope. V2 is `{"spec":"chara_card_v2","data":{...}}`; some very old cards are the bare
 * character object with no envelope at all, which [CharacterCardParser] handles separately.
 */
@Serializable
data class CharacterCardEnvelope(
    val spec: String = "",
    @SerialName("spec_version") val specVersion: String = "",
    val data: CharacterCard = CharacterCard()
)
