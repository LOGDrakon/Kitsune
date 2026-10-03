package com.kitsune.core.network.repository

import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.common.coroutines.DispatcherProvider
import com.kitsune.core.network.api.BackendChatApi
import com.kitsune.core.network.catalog.ModelCatalogRepository
import com.kitsune.core.network.dto.ChatCompletionRequest
import com.kitsune.core.network.dto.ChatMessageDto
import com.kitsune.core.network.dto.ChatMessageImageDto
import com.kitsune.core.network.dto.ImageUrlDto
import com.kitsune.core.network.dto.UsageDto
import com.kitsune.core.network.preferences.NetworkPreferences
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_CONTINUATIONS = 3
private const val CONTINUE_PROMPT =
    "Your previous answer was cut off. Continue exactly where you stopped, without repeating anything."

private val insufficientCreditsJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class InsufficientCreditsBody(
    val error: String,
    val currentBalance: Int,
    val requiredCredits: Int
)

@Serializable
private data class ProxyErrorBody(
    val error: String,
    val code: String? = null
)

@Serializable
private data class ContentPolicyViolationBody(
    val category: String? = null,
    val flaggedExcerpt: String? = null
)

@Singleton
class ChatCompletionRepositoryImpl @Inject constructor(
    private val apiService: BackendChatApi,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val networkPreferences: NetworkPreferences,
    private val dispatchers: DispatcherProvider,
    private val backendClient: KitsuneBackendClient
) : ChatCompletionRepository {

    override suspend fun complete(
        modelId: String,
        systemPrompt: String,
        messages: List<ChatTurn>,
        temperature: Double,
        sampling: SamplingProfile,
        fallbackModelId: String?,
        operationType: String,
        maxTokens: Int,
        allowContinuation: Boolean,
        chatMode: String,
        imageQuality: String
    ): Result<ChatCompletionResult> = withContext(dispatchers.io) {
        val primary = runChatCompletion(modelId, systemPrompt, messages, temperature, sampling, operationType, maxTokens, allowContinuation, chatMode, imageQuality)
        if (primary.isSuccess) return@withContext primary

        // Try the provided fallback model (if any)
        if (fallbackModelId != null && fallbackModelId != modelId) {
            val fallbackResult = runChatCompletion(fallbackModelId, systemPrompt, messages, temperature, sampling, operationType, maxTokens, allowContinuation, chatMode, imageQuality)
            if (fallbackResult.isSuccess) return@withContext fallbackResult
        }

        // Return the primary error — no more cascade through random models
        primary
    }

    private suspend fun runChatCompletion(
        modelId: String,
        systemPrompt: String,
        messages: List<ChatTurn>,
        temperature: Double,
        sampling: SamplingProfile,
        operationType: String,
        maxTokens: Int,
        allowContinuation: Boolean,
        chatMode: String = "STANDARD",
        imageQuality: String = "STANDARD"
    ): Result<ChatCompletionResult> = runCatching {
        val baseMessages = buildList {
            add(ChatMessageDto(role = ChatMessageDto.ROLE_SYSTEM, content = systemPrompt))
            messages.forEach { turn ->
                add(
                    ChatMessageDto(
                        role = turn.role,
                        content = turn.content,
                        images = turn.referenceImages.takeIf { it.isNotEmpty() }
                            ?.map { ChatMessageImageDto(imageUrl = ImageUrlDto(url = encodeBase64ImageDataUri(it))) }
                    )
                )
            }
        }

        val fullContent = StringBuilder()
        val images = mutableListOf<ByteArray>()
        var continuations = 0
        var lastUsage: UsageDto? = null

        while (true) {
            val requestMessages = if (fullContent.isEmpty()) {
                baseMessages
            } else {
                baseMessages + listOf(
                    ChatMessageDto(role = ChatMessageDto.ROLE_ASSISTANT, content = fullContent.toString()),
                    ChatMessageDto(role = ChatMessageDto.ROLE_USER, content = CONTINUE_PROMPT)
                )
            }

            val request = ChatCompletionRequest(
                model = modelId,
                messages = requestMessages,
                maxTokens = maxTokens,
                // The profile's temperature wins when it states one: a preset that asks for a cooler
                // decode must not be silently overridden by the global preference. When it says
                // nothing, the caller's temperature applies exactly as before.
                temperature = sampling.temperature ?: temperature,
                topP = sampling.topP,
                frequencyPenalty = sampling.frequencyPenalty,
                presencePenalty = sampling.presencePenalty
            )
            val response = apiService.chatCompletion(request, operationType, chatMode, imageQuality)
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                if (response.code() == 402 && errorBody != null) {
                    runCatching { insufficientCreditsJson.decodeFromString<InsufficientCreditsBody>(errorBody) }
                        .getOrNull()
                        ?.let { throw InsufficientCreditsException(it.currentBalance, it.requiredCredits) }
                }
                if (response.code() == 451) {
                    val body = errorBody?.let {
                        runCatching { insufficientCreditsJson.decodeFromString<ContentPolicyViolationBody>(it) }.getOrNull()
                    }
                    throw ContentPolicyViolationException(category = body?.category, flaggedExcerpt = body?.flaggedExcerpt)
                }
                // Defensive fallback: the app already keeps a non-subscriber from setting Pro mode
                // (see ChatViewModel), but a subscription can lapse between opening the app and
                // sending. Other 403s (banned/frozen account) fall through to the generic error below
                // unchanged — only this specific code gets special handling.
                if (response.code() == 403 && errorBody != null) {
                    runCatching { insufficientCreditsJson.decodeFromString<ProxyErrorBody>(errorBody) }
                        .getOrNull()
                        ?.takeIf { it.code == "PRO_REQUIRES_SUBSCRIPTION" }
                        ?.let { throw ProSubscriptionRequiredException() }
                }
                error("API error ${response.code()}: $errorBody")
            }
            val body = response.body() ?: error("Empty API response body")
            body.creditBalance?.let { backendClient.syncCreditBalance(it) }
            val choice = body.choices.firstOrNull() ?: error("No choices in API response")
            val content = choice.message.content.orEmpty()
            fullContent.append(content)
            lastUsage = body.usage
            choice.message.images.orEmpty().mapNotNull { it.imageUrl?.url }
                .mapNotNull(::decodeBase64ImageDataUri)
                .forEach(images::add)

            val truncated = choice.finishReason.equals("length", ignoreCase = true)
            if (!allowContinuation || !truncated || content.isBlank() || continuations >= MAX_CONTINUATIONS) break
            continuations++
        }

        ChatCompletionResult(
            content = fullContent.toString().trim(),
            usage = lastUsage?.let { TokenUsage(it.promptTokens, it.completionTokens, it.totalTokens) },
            modelUsed = modelId,
            images = images
        )
    }
}
