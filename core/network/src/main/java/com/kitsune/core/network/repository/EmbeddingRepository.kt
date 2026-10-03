package com.kitsune.core.network.repository

interface EmbeddingRepository {
    suspend fun embed(text: String, modelId: String): Result<List<Float>>
}