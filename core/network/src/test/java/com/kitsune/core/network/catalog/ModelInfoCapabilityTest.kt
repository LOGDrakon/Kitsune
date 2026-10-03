package com.kitsune.core.network.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun modelWithId(
    id: String,
    chatCapable: Boolean = true,
    deprecated: Boolean = false,
    category: String = "chat"
) = ModelInfo(
    id = id,
    provider = "test",
    maxInputTokens = 100_000,
    maxOutputTokens = 100_000,
    inputCostPerMillionTokens = 1.0,
    outputCostPerMillionTokens = 1.0,
    chatCapable = chatCapable,
    deprecated = deprecated,
    category = category
)

class ModelInfoCapabilityTest {

    @Test
    fun `chat models are chat capable and not image capable`() {
        listOf("gpt-4.1", "gpt-4o", "claude-sonnet-4-5", "mistral-medium-3.1", "sonar-pro").forEach { id ->
            val model = modelWithId(id)
            assertTrue("$id should be chat capable", model.isChatCapable)
            assertFalse("$id should not be image capable", model.isImageCapable)
        }
    }

    @Test
    fun `embeddings models are excluded from chat`() {
        listOf("text-embedding-3-small", "text-embedding-3-large").forEach { id ->
            assertFalse("$id should not be chat capable", modelWithId(id).isChatCapable)
        }
    }

    @Test
    fun `embedding models are embedding capable, chat and image models are not`() {
        // Real reported bug (LlmModelResolver): nothing distinguished an embedding model from a
        // chat model at the resolver's fallback layer, so an unresolved EMBEDDING request could
        // silently fall back to a chat model instead of failing loudly or picking a real embedding
        // model.
        // No `category` from the backend here — exercises the id-substring backstop only.
        listOf("openai/text-embedding-3-small", "mistralai/mistral-embed", "qwen/qwen3-embedding-8b").forEach { id ->
            assertTrue("$id should be embedding capable", modelWithId(id).isEmbeddingCapable)
        }
        listOf("deepseek/deepseek-v4-flash", "openai/gpt-image-2", "gemini-2.5-flash-image").forEach { id ->
            assertFalse("$id should not be embedding capable", modelWithId(id).isEmbeddingCapable)
        }
    }

    @Test
    fun `the backend's stated category catches real embedding ids the substring backstop would miss`() {
        // These are real OpenRouter embedding model ids (confirmed live 2026-08-18) that contain no
        // "embed" substring at all — exactly what the id-only heuristic used to miss, and exactly
        // why `category`, not just the id, must be consulted first.
        listOf("voyageai/voyage-code-4", "thenlper/gte-base", "intfloat/e5-large-v2", "baai/bge-m3", "sentence-transformers/all-minilm-l6-v2").forEach { id ->
            assertFalse("$id has no 'embed' substring, so it must rely on category alone", id.contains("embed", ignoreCase = true))
            assertTrue("$id should be embedding capable via category", modelWithId(id, category = "embedding").isEmbeddingCapable)
        }
    }

    @Test
    fun `dedicated image-generation-only models are excluded from chat but are image capable`() {
        listOf("gpt-image-2", "gpt-image", "dall-e-3").forEach { id ->
            val model = modelWithId(id)
            assertFalse("$id should not be chat capable", model.isChatCapable)
            assertTrue("$id should be image capable", model.isImageCapable)
        }
    }

    @Test
    fun `chat models that also emit inline images stay chat capable and are image capable`() {
        listOf("gemini-2.5-flash-image", "gemini-3-pro-image-preview", "gemini-3.1-flash-image-preview").forEach { id ->
            val model = modelWithId(id)
            assertTrue("$id should stay chat capable", model.isChatCapable)
            assertTrue("$id should be image capable", model.isImageCapable)
        }
    }

    @Test
    fun `speech models are excluded from chat`() {
        listOf("whisper-1", "tts-1").forEach { id ->
            assertFalse("$id should not be chat capable", modelWithId(id).isChatCapable)
        }
    }

    // --- OpenRouter's vendor/slug ids: the only form the backend now serves ---

    @Test
    fun `non-chat families are still excluded once the id carries a vendor prefix`() {
        // The local backstop matches on substrings, but the image pattern was anchored with `^` and
        // would silently stop matching once ids became `vendor/model`.
        listOf(
            "openai/text-embedding-3-small",
            "mistralai/mistral-embed",
            "openai/whisper-1",
            "openai/tts-1",
            "openai/dall-e-3",
            "openai/gpt-image-2",
            "qwen/qwen3.7-text-embedding"
        ).forEach {
            assertFalse("$it should not be chat-capable", modelWithId(it).isChatCapable)
        }
    }

    @Test
    fun `a vendor-qualified image model with an OpenRouter variant suffix is still excluded`() {
        // e.g. `openai/gpt-image-2:free` — the free/nitro/floor variant suffix must not defeat the
        // end-anchor and let the model back into the chat picker.
        listOf("openai/gpt-image-2:free", "openai/gpt-image:nitro").forEach {
            assertFalse("$it should not be chat-capable", modelWithId(it).isChatCapable)
        }
    }

    @Test
    fun `vendor-qualified chat models remain chat-capable`() {
        listOf(
            "deepseek/deepseek-v4-flash-0731",
            "qwen/qwen3.7-plus",
            "mistralai/mistral-small-latest",
            "google/gemini-3.6-flash",
            "openai/gpt-5.6-luna"
        ).forEach {
            assertTrue("$it should be chat-capable", modelWithId(it).isChatCapable)
        }
    }

    @Test
    fun `a model the backend marks as not chat-capable is excluded even if its name looks fine`() {
        // This is the half the backend contributes: OpenRouter's own architecture metadata can mark
        // a model not chat-capable even when its name looks unremarkable.
        assertFalse(modelWithId("mistralai/some-classifier-model", chatCapable = false).isChatCapable)
    }

    @Test
    fun `a deprecated model is not offered for new traffic`() {
        assertFalse(modelWithId("google/gemini-2.5-flash", deprecated = true).isChatCapable)
    }
}
