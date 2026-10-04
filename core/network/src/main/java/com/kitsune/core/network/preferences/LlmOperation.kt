package com.kitsune.core.network.preferences

/**
 * Identifies a class of LLM call in Kitsune so the user can pick a different default model per
 * class. The default chat/image models act as fallbacks when a specific operation has not been
 * overridden.
 */
enum class LlmOperation(val storageKey: String) {
    /** In-character chat replies (send, regenerate, opening scene, arrival beat). */
    CHAT(storageKey = "op_chat_model_id"),

    /** Rolling-summary compaction (memory level 2). */
    SUMMARY(storageKey = "op_summary_model_id"),

    /** Structured lore fiches extraction (memory level 3). */
    LORE(storageKey = "op_lore_model_id"),

    /** Persona quick generation, universe/location/faction/NPC quick generation. */
    QUICK_GENERATION(storageKey = "op_quick_generation_model_id"),

    /** Structured visual sheet generation for image consistency. */
    VISUAL_SHEET(storageKey = "op_visual_sheet_model_id"),

    /** Scene description and prompt softening for image generation. */
    IMAGE_DESCRIPTION(storageKey = "op_image_description_model_id"),

    /** Inline image generation — kept in sync with [NetworkPreferences.getDefaultImageModelId]. */
    IMAGE_GENERATION(storageKey = "default_image_model_id"),

    /** Neural text embeddings for semantic memory retrieval (memory level 4 RAG). */
    EMBEDDING(storageKey = "op_embedding_model_id"),

    /** Translating a persona's sheet (description/personality/scenario/example dialogues) and
     * opening message into the app's current language — see TranslatePersonaUseCase. */
    TRANSLATION(storageKey = "op_translation_model_id"),

    /** "Je ne sais pas quoi créer" AI-guided Q&A wizard (persona/universe creation) — see
     * GenerateInspirationUseCase. */
    INSPIRATION(storageKey = "op_inspiration_model_id"),

    /** On-demand "what could I say next?" reply-suggestion chips mid-conversation — see
     * GenerateNextReplySuggestionsUseCase. */
    NEXT_REPLY_SUGGESTIONS(storageKey = "op_next_reply_suggestions_model_id")
}
