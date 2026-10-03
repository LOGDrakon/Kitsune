package com.kitsune.core.network.repository

import com.kitsune.core.common.coroutines.DispatcherProvider
import com.kitsune.core.network.api.BackendChatApi
import com.kitsune.core.network.dto.EmbeddingRequest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingRepositoryImpl @Inject constructor(
    private val apiService: BackendChatApi,
    private val dispatchers: DispatcherProvider
) : EmbeddingRepository {

    override suspend fun embed(text: String, modelId: String): Result<List<Float>> =
        withContext(dispatchers.io) {
            runCatching {
                val request = EmbeddingRequest(model = modelId, input = text)
                val response = apiService.createEmbedding(request)
                if (!response.isSuccessful) {
                    error("Embedding API error ${response.code()}: ${response.errorBody()?.string()}")
                }
                response.body()?.data?.firstOrNull()?.embedding
                    ?: error("Embedding API returned no data")
            }
        }
}