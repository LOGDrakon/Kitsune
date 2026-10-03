package com.kitsune.core.network.repository

/**
 * [referenceImages] are attached as input images on this turn's outgoing message (data URIs) for
 * image-to-image visual consistency — e.g. a persona's existing avatar, so the model can use it as
 * a reference when generating a new image of the same character (FEATURES.md section 5). Empty for
 * every ordinary text turn; only [com.kitsune.core.network.repository.GenerateImageUseCase] populates it.
 */
data class ChatTurn(val role: String, val content: String, val referenceImages: List<ByteArray> = emptyList())

data class TokenUsage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int)

data class ChatCompletionResult(
    val content: String,
    val usage: TokenUsage?,
    /** May differ from the requested model id if an automatic fallback kicked in. */
    val modelUsed: String,
    /** Decoded inline images (`choices[].message.images[]`), only populated with an image-capable model. */
    val images: List<ByteArray> = emptyList()
)
