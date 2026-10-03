package com.kitsune.core.memory.semantic

import com.kitsune.core.common.coroutines.DispatcherProvider
import com.kitsune.core.network.preferences.LlmModelResolver
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.repository.EmbeddingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteTextEmbedder @Inject constructor(
    private val embeddingRepository: EmbeddingRepository,
    private val llmModelResolver: LlmModelResolver,
    private val dispatchers: DispatcherProvider
) {
    suspend fun embed(text: String): Result<List<Float>> {
        val modelId = llmModelResolver.resolve(LlmOperation.EMBEDDING)
        return embeddingRepository.embed(text, modelId)
    }
}