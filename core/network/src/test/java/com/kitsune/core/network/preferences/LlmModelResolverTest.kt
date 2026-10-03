package com.kitsune.core.network.preferences

import com.kitsune.core.network.catalog.ModelCatalogRepository
import com.kitsune.core.network.catalog.ModelInfo
import com.kitsune.core.network.provider.ModelRef
import com.kitsune.core.network.provider.ProviderConfig
import com.kitsune.core.network.provider.ProviderPreset
import com.kitsune.core.network.provider.ProviderStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val OR = "or1"
private const val MAM = "mam1"

private fun chatModel(provider: String, id: String) = ModelInfo(
    id = ModelRef.of(provider, id), provider = provider, providerId = provider, modelId = id,
    maxInputTokens = 100_000, maxOutputTokens = 100_000,
    inputCostPerMillionTokens = 1.0, outputCostPerMillionTokens = 1.0, chatCapable = true
)

private fun embeddingModel(provider: String, id: String) = ModelInfo(
    id = ModelRef.of(provider, id), provider = provider, providerId = provider, modelId = id,
    maxInputTokens = 8_192, maxOutputTokens = null,
    inputCostPerMillionTokens = 0.02, outputCostPerMillionTokens = 0.0, chatCapable = false,
    category = "embedding"
)

private fun imageModel(provider: String, id: String) = ModelInfo(
    id = ModelRef.of(provider, id), provider = provider, providerId = provider, modelId = id,
    maxInputTokens = 32_000, maxOutputTokens = null,
    inputCostPerMillionTokens = 0.25, outputCostPerMillionTokens = 1.5, chatCapable = false,
    category = "image"
)

/**
 * Real reported bug, kept as a regression test: a resolver that fell back to a *chat* model for an
 * unresolvable embedding selection sent a chat model id to `/embeddings`, which the provider rejected
 * on every single request. Each operation must fall back within its own category.
 */
class LlmModelResolverTest {

    private val networkPreferences = mockk<NetworkPreferences>()
    private val catalogRepository = mockk<ModelCatalogRepository>()
    private val providerStore = mockk<ProviderStore>().also {
        every { it.default() } returns ProviderConfig(OR, ProviderPreset.OPENROUTER, "OpenRouter", "https://openrouter.ai/api/v1")
    }
    private val resolver = LlmModelResolver(networkPreferences, catalogRepository, providerStore)

    @Test
    fun `an embedding selection missing from the catalog falls back to an embedding model, not chat`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.EMBEDDING) } returns ModelRef.of(OR, "retired-embedder")
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(chatModel(OR, "deepseek/deepseek-v4-flash"), embeddingModel(OR, "openai/text-embedding-3-small"))
        )

        assertEquals(ModelRef.of(OR, "openai/text-embedding-3-small"), resolver.resolve(LlmOperation.EMBEDDING))
    }

    @Test
    fun `a configured model that IS in the catalog is used as-is`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.EMBEDDING) } returns ModelRef.of(OR, "qwen/qwen3-embedding-8b")
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(embeddingModel(OR, "qwen/qwen3-embedding-8b"), embeddingModel(OR, "openai/text-embedding-3-small"))
        )

        assertEquals(ModelRef.of(OR, "qwen/qwen3-embedding-8b"), resolver.resolve(LlmOperation.EMBEDDING))
    }

    @Test
    fun `an embedding model whose id lacks the word embed is still recognised by its category`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.EMBEDDING) } returns ""
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(chatModel(OR, "deepseek/deepseek-v4-flash"), embeddingModel(OR, "voyageai/voyage-code-4"))
        )

        assertEquals(ModelRef.of(OR, "voyageai/voyage-code-4"), resolver.resolve(LlmOperation.EMBEDDING))
    }

    @Test
    fun `the fallback prefers the default provider`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.CHAT) } returns ""
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(chatModel(MAM, "gpt-5"), chatModel(OR, "deepseek/deepseek-v4-flash"))
        )

        assertEquals(ModelRef.of(OR, "deepseek/deepseek-v4-flash"), resolver.resolve(LlmOperation.CHAT))
    }

    @Test
    fun `the same model id on another provider is not mistaken for the selection`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.CHAT) } returns ModelRef.of(MAM, "gpt-5")
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(chatModel(OR, "gpt-5"), chatModel(MAM, "gpt-5"))
        )

        assertEquals(ModelRef.of(MAM, "gpt-5"), resolver.resolve(LlmOperation.CHAT))
    }

    @Test
    fun `image generation falls back to an image-capable model`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.IMAGE_GENERATION) } returns ModelRef.of(OR, "retired/image-model")
        coEvery { catalogRepository.getModels(false) } returns Result.success(
            listOf(chatModel(OR, "deepseek/deepseek-v4-flash"), imageModel(OR, "google/gemini-2.5-flash-image"))
        )

        assertEquals(ModelRef.of(OR, "google/gemini-2.5-flash-image"), resolver.resolve(LlmOperation.IMAGE_GENERATION))
    }

    @Test
    fun `an empty catalog returns the configured model verbatim rather than guessing`() = runTest {
        every { networkPreferences.getModelForOperation(LlmOperation.EMBEDDING) } returns ModelRef.of(OR, "openai/text-embedding-3-small")
        coEvery { catalogRepository.getModels(false) } returns Result.success(emptyList())

        assertEquals(ModelRef.of(OR, "openai/text-embedding-3-small"), resolver.resolve(LlmOperation.EMBEDDING))
    }
}
