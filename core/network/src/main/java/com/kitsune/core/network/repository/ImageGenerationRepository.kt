package com.kitsune.core.network.repository

import com.kitsune.core.network.provider.LlmHttpClient
import com.kitsune.core.network.provider.NoProviderConfiguredException
import com.kitsune.core.network.provider.ProviderStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Image generation against the provider serving [modelRef]. Kept apart from
 * [ChatCompletionRepository] because no provider serves dedicated image models through
 * `/chat/completions` reliably — see [LlmHttpClient.generateImages] for the endpoints tried.
 */
@Singleton
class ImageGenerationRepository @Inject constructor(
    private val httpClient: LlmHttpClient,
    private val providerStore: ProviderStore
) {
    /** [quality] is `"STANDARD"` or `"HD"`; providers that ignore render settings simply ignore it. */
    suspend fun generate(
        modelRef: String,
        prompt: String,
        referenceImages: List<ByteArray>,
        quality: String = "STANDARD"
    ): Result<List<ByteArray>> = runCatching {
        val (provider, model) = providerStore.resolve(modelRef) ?: throw NoProviderConfiguredException()
        val hd = quality.equals("HD", ignoreCase = true)
        httpClient.generateImages(
            provider = provider,
            model = model,
            prompt = prompt,
            referenceDataUris = referenceImages.map {
                encodeBase64ImageDataUri(it, ChatCompletionRepositoryImpl.sniffImageMime(it))
            },
            resolution = if (hd) "2K" else "1K",
            quality = if (hd) "high" else "medium"
        )
    }
}
