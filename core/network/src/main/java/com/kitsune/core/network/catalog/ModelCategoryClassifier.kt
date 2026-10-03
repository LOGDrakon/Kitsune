package com.kitsune.core.network.catalog

/**
 * Sorts a provider's models into what they are *for*: `chat`, `image`, `embedding`, `audio`,
 * `video` or `other`.
 *
 * Ported from the hosted backend's `ModelCategory`. The provider's own
 * `architecture.output_modalities` (published by OpenRouter) is a stated fact and is checked first;
 * the id heuristics cover providers that publish nothing but ids, and the cases modalities don't
 * distinguish (rerankers and moderation classifiers look like plain text→text).
 *
 * Defaults to `chat`: wrongly excluding a working chat model breaks a feature, while briefly listing
 * one that turns out not to chat only clutters the picker.
 */
object ModelCategoryClassifier {

    fun classify(modelId: String, outputModalities: List<String> = emptyList()): String {
        val id = modelId.lowercase()
        val outputs = outputModalities.map { it.lowercase() }
        fun has(vararg markers: String) = markers.any { id.contains(it) }

        return when {
            "embeddings" in outputs || "embedding" in outputs || has("embed") -> "embedding"
            has("rerank") -> "other"
            "video" in outputs || has("veo-", "video") -> "video"
            "audio" in outputs || has(
                "whisper", "tts", "-asr", "asr-", "audio", "transcribe", "transcription",
                "speech", "voxtral", "lyria", "realtime", "livetranslate", "live-translate"
            ) -> "audio"
            "image" in outputs || has("image", "dall-e", "imagen", "nano-banana", "flux", "stable-diffusion", "sdxl") -> "image"
            has(
                "moderation", "guard", "ocr", "robotics", "computer-use", "deep-research",
                "classification", "aqa"
            ) -> "other"
            else -> "chat"
        }
    }
}
