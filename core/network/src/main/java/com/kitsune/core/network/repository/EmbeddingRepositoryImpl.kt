package com.kitsune.core.network.repository

import com.kitsune.core.network.provider.LlmHttpClient
import com.kitsune.core.network.provider.NoProviderConfiguredException
import com.kitsune.core.network.provider.ProviderStore
import javax.inject.Inject
import javax.inject.Singleton

/** Embeddings, sent straight to the provider that serves the selected embedding model. [modelId] is
 * a [com.kitsune.core.network.provider.ModelRef]. */
@Singleton
class EmbeddingRepositoryImpl @Inject constructor(
    private val httpClient: LlmHttpClient,
    private val providerStore: ProviderStore
) : EmbeddingRepository {

    override suspend fun embed(text: String, modelId: String): Result<List<Float>> = runCatching {
        val (provider, model) = providerStore.resolve(modelId) ?: throw NoProviderConfiguredException()
        httpClient.embeddings(provider, model, text)
    }
}
