package com.kitsune.core.network.api

import com.kitsune.core.network.dto.ChatCompletionRequest
import com.kitsune.core.network.dto.ChatCompletionResponse
import com.kitsune.core.network.dto.EmbeddingRequest
import com.kitsune.core.network.dto.EmbeddingResponse
import com.kitsune.core.network.dto.ModerationClassifyRequest
import com.kitsune.core.network.dto.ModerationClassifyResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BackendChatApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatCompletionRequest,
        @Header("X-Operation-Type") operationType: String = "CHAT",
        @Header("X-Chat-Mode") chatMode: String = "STANDARD",
        /** Niveau de génération d'image ("STANDARD"/"HD"). Ignoré par le serveur hors requête
         *  d'image ; s'il est absent, le serveur applique le niveau par défaut configuré. */
        @Header("X-Image-Quality") imageQuality: String = "STANDARD"
    ): Response<ChatCompletionResponse>

    @POST("v1/embeddings")
    suspend fun createEmbedding(
        @Body request: EmbeddingRequest,
        @Header("X-Operation-Type") operationType: String = "EMBEDDING"
    ): Response<EmbeddingResponse>

    /** Second-opinion classifier for a keyword match `LocalKeywordFilter` flagged as ambiguous. */
    @POST("v1/moderation/classify")
    suspend fun classifyModeration(@Body request: ModerationClassifyRequest): Response<ModerationClassifyResponse>
}
