package com.kitsune.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body for our backend's OpenAI-compatible `/v1/chat/completions` endpoint, itself proxied to
 * OpenRouter (originally Mammouth.ai). */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val temperature: Double = 0.9,
    val stream: Boolean = false,
    /** Decoder controls beyond temperature (2026-08-23) — see
     *  [com.kitsune.core.network.repository.SamplingProfile]. Omitted from the JSON when null, so a
     *  caller that sets none produces the exact body this class produced before they existed. */
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null
)

/** A single message, following the OpenAI role/content format. */
@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String? = null,
    /**
     * Populated by the API on assistant messages when an image-capable model is used (output).
     * Also populated by us on outgoing user messages to attach a reference image for
     * image-to-image visual consistency (input) — see [com.kitsune.core.network.repository.ChatTurn.referenceImages]
     * and IDEAS.md 2026-07-02 ("seed fixe / image-to-image avec référence").
     */
    val images: List<ChatMessageImageDto>? = null
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

@Serializable
data class ChatMessageImageDto(
    @SerialName("image_url") val imageUrl: ImageUrlDto?
)

@Serializable
data class ImageUrlDto(
    /** Either a remote URL or a `data:image/...;base64,...` URI. */
    val url: String?
)
