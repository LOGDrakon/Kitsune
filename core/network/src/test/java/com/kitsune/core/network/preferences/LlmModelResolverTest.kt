package com.kitsune.core.network.preferences

import com.kitsune.core.network.catalog.ModelCatalogRepository
import com.kitsune.core.network.catalog.ModelInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private fun chatModel(id: String) = ModelInfo(
    id = id, provider = "test", maxInputTokens = 100_000, maxOutputTokens = 100_000,
    inputCostPerMillionTokens = 1.0, outputCostPerMillionTokens = 1.0, chatCapable = true
)

private fun embeddingModel(id: String) = ModelInfo(
    id = id, provider = "test", maxInputTokens = 8_192, maxOutputTokens = null,
    inputCostPerMillionTokens = 0.02, outputCostPerMillionTokens = 0.0, chatCapable = false,
    category = "embedding"
)

private fun imageModel(id: String) = ModelInfo(
    id = id, provider = "test", maxInputTokens = 32_000, maxOutputTokens = null,
    inputCostPerMillionTokens = 0.25, outputCostPerMillionTokens = 1.5, chatCapable = true
)

/**
 * Real reported bug: [LlmModelResolver.resolve] special-cased [LlmOperation.IMAGE_GENERATION]'s
 * fallback (falling back to an image-capable model), but every other operation — including
 * [LlmOperation.EMBEDDING] — fell back to [NetworkPreferences.DEFAULT_CHAT_MODEL_ID], a chat
 * model, whenever the configured model wasn't found in the catalog. Confirmed live 2026-08-18: the
 * backend's `GET /models` never returned embedding models until a separate fix, so this fallback
 * fired on *every single* embedding request and sent a chat model id to `/embeddings`, which
 * OpenRouter rejected outright.
 */
class LlmModelResolverTest {

    private val networkPreferences = mockk<NetworkPreferences>()
    private val catalogRepository = mockk<ModelCatalogRepository>()
    private val resolver = LlmModelResolver(networkPreferences, catalogRepository)

    @Test
    fun `an embedding model missing from the catalog falls back to a real embedding model, not chat`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.EMBEDDING) } returns "openai:text-embedding-3-small" // stale legacy id, not in catalog
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(chatModel("deepseek/deepseek-v4-flash"), embeddingModel(NetworkPreferences.DEFAULT_EMBEDDING_MODEL_ID))
        )

        val resolved = resolver.resolve(LlmOperation.EMBEDDING)

        assertEquals(NetworkPreferences.DEFAULT_EMBEDDING_MODEL_ID, resolved)
    }

    @Test
    fun `a configured embedding model that IS in the catalog is used as-is`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.EMBEDDING) } returns "qwen/qwen3-embedding-8b"
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(embeddingModel("qwen/qwen3-embedding-8b"), embeddingModel(NetworkPreferences.DEFAULT_EMBEDDING_MODEL_ID))
        )

        val resolved = resolver.resolve(LlmOperation.EMBEDDING)

        assertEquals("qwen/qwen3-embedding-8b", resolved)
    }

    @Test
    fun `even if the hardcoded embedding default is also missing, any embedding-capable model in the catalog is preferred over a chat model`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.EMBEDDING) } returns "not-in-catalog"
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(chatModel("deepseek/deepseek-v4-flash"), embeddingModel("voyageai/voyage-code-4"))
        )

        val resolved = resolver.resolve(LlmOperation.EMBEDDING)

        assertEquals("voyageai/voyage-code-4", resolved)
    }

    @Test
    fun `a chat operation whose configured model is missing still falls back to a chat model`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.CHAT) } returns "retired/model"
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(embeddingModel(NetworkPreferences.DEFAULT_EMBEDDING_MODEL_ID), chatModel(NetworkPreferences.DEFAULT_CHAT_MODEL_ID))
        )

        val resolved = resolver.resolve(LlmOperation.CHAT)

        assertEquals(NetworkPreferences.DEFAULT_CHAT_MODEL_ID, resolved)
    }

    @Test
    fun `image generation still falls back to an image-capable model (pre-existing behaviour, unaffected)`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.IMAGE_GENERATION) } returns "retired/image-model"
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(chatModel("deepseek/deepseek-v4-flash"), imageModel(NetworkPreferences.DEFAULT_IMAGE_MODEL_ID))
        )

        val resolved = resolver.resolve(LlmOperation.IMAGE_GENERATION)

        assertEquals(NetworkPreferences.DEFAULT_IMAGE_MODEL_ID, resolved)
    }

    @Test
    fun `an empty catalog returns the configured model verbatim rather than guessing`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.EMBEDDING) } returns "openai/text-embedding-3-small"
        coEvery { catalogRepository.getModels(false) } returns Result.success(emptyList())

        val resolved = resolver.resolve(LlmOperation.EMBEDDING)

        assertEquals("openai/text-embedding-3-small", resolved)
    }
}
