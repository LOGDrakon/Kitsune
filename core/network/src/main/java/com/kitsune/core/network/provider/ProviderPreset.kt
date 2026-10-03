package com.kitsune.core.network.provider

/**
 * The AI providers Kitsune knows how to talk to out of the box.
 *
 * Every one of them speaks the OpenAI-compatible wire format (`/chat/completions`, `/models`,
 * `/embeddings`), which is why a single client ([LlmHttpClient]) serves them all; a preset only
 * fills in the base URL and a few defaults so the user does not have to look them up. [CUSTOM]
 * covers anything else that speaks the same format — a self-hosted server, a new gateway — with a
 * user-typed base URL.
 *
 * The suggested model ids are starting points shown pre-selected after a provider is added, never
 * hard requirements: the model picker lists what the provider itself reports, and the user can pick
 * anything from it.
 */
enum class ProviderPreset(
    val displayName: String,
    val defaultBaseUrl: String,
    /** Where the user creates an API key — opened from the provider setup screen. */
    val keyUrl: String?,
    val suggestedChatModel: String? = null,
    val suggestedImageModel: String? = null,
    val suggestedEmbeddingModel: String? = null,
    /** False for local servers (Ollama, LM Studio) that accept any key or none. */
    val requiresKey: Boolean = true
) {
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        keyUrl = "https://openrouter.ai/settings/keys",
        suggestedChatModel = "deepseek/deepseek-v4-flash",
        suggestedImageModel = "google/gemini-2.5-flash-image",
        suggestedEmbeddingModel = "openai/text-embedding-3-small"
    ),
    MAMMOUTH(
        displayName = "Mammouth",
        defaultBaseUrl = "https://api.mammouth.ai/v1",
        keyUrl = "https://mammouth.ai/app/account/settings/api"
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        keyUrl = "https://platform.openai.com/api-keys",
        suggestedEmbeddingModel = "text-embedding-3-small"
    ),
    MISTRAL(
        displayName = "Mistral",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        keyUrl = "https://console.mistral.ai/api-keys",
        suggestedEmbeddingModel = "mistral-embed"
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        keyUrl = "https://platform.deepseek.com/api_keys"
    ),
    GROQ(
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        keyUrl = "https://console.groq.com/keys"
    ),
    TOGETHER(
        displayName = "Together AI",
        defaultBaseUrl = "https://api.together.xyz/v1",
        keyUrl = "https://api.together.ai/settings/api-keys"
    ),
    NANOGPT(
        displayName = "NanoGPT",
        defaultBaseUrl = "https://nano-gpt.com/api/v1",
        keyUrl = "https://nano-gpt.com/api"
    ),
    OLLAMA(
        displayName = "Ollama (local)",
        defaultBaseUrl = "http://10.0.2.2:11434/v1",
        keyUrl = null,
        requiresKey = false
    ),
    LM_STUDIO(
        displayName = "LM Studio (local)",
        defaultBaseUrl = "http://10.0.2.2:1234/v1",
        keyUrl = null,
        requiresKey = false
    ),
    CUSTOM(
        displayName = "Autre (compatible OpenAI)",
        defaultBaseUrl = "",
        keyUrl = null,
        requiresKey = false
    );

    val isOpenRouter: Boolean get() = this == OPENROUTER
}
