package com.kitsune.core.network.repository

import android.util.Base64
import com.kitsune.core.network.provider.LlmHttpClient
import com.kitsune.core.network.provider.NoProviderConfiguredException
import com.kitsune.core.network.provider.ProviderHttpException
import com.kitsune.core.network.provider.ProviderStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_CONTINUATIONS = 3
private const val CONTINUE_PROMPT =
    "Your previous answer was cut off. Continue exactly where you stopped, without repeating anything."

/** Appended only when a Mistral model would otherwise receive a conversation ending on an assistant
 * turn — see [ChatCompletionRepositoryImpl.needsTrailingUserTurn]. Deliberately content-free: it
 * points back at the system prompt instead of restating anything. */
private const val TRAILING_USER_TURN = "Follow the instructions in the system prompt now."

/** The decoder fields we send and are therefore willing to drop when a model refuses one. */
private val SAMPLER_PARAMS = listOf("temperature", "top_p", "frequency_penalty", "presence_penalty")

/**
 * Chat completions, sent straight to the user's provider.
 *
 * Everything the hosted backend used to do to a request before forwarding it now happens here,
 * because there is no backend any more:
 *
 * - **Sampler retry** — some models accept only their default temperature (or reject top_p, or the
 *   penalties) and answer 400. The call is retried without the refused field rather than keeping a
 *   per-model list, which would be stale by the next release.
 * - **Mistral trailing turn** — Mistral models reject a conversation ending on an assistant turn
 *   (`400 invalid_request_message_order`), which "what could I say next?" naturally produces.
 * - **Content recovery** — reasoning models sometimes leave `content` null and put the reply in
 *   `reasoning_content`/`reasoning`, or return `content` as an array of parts.
 * - **Continuation** — a reply cut off by `max_tokens` is continued automatically.
 */
@Singleton
class ChatCompletionRepositoryImpl @Inject constructor(
    private val httpClient: LlmHttpClient,
    private val providerStore: ProviderStore
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
        allowContinuation: Boolean
    ): Result<ChatCompletionResult> {
        val primary = run(modelId, systemPrompt, messages, temperature, sampling, maxTokens, allowContinuation)
        if (primary.isSuccess) return primary
        if (fallbackModelId != null && fallbackModelId != modelId) {
            val fallback = run(fallbackModelId, systemPrompt, messages, temperature, sampling, maxTokens, allowContinuation)
            if (fallback.isSuccess) return fallback
        }
        return primary
    }

    private suspend fun run(
        modelRef: String,
        systemPrompt: String,
        messages: List<ChatTurn>,
        temperature: Double,
        sampling: SamplingProfile,
        maxTokens: Int,
        allowContinuation: Boolean
    ): Result<ChatCompletionResult> = runCatching {
        val (provider, model) = providerStore.resolve(modelRef) ?: throw NoProviderConfiguredException()

        val baseMessages: List<JsonObject> = buildList {
            add(message("system", JsonPrimitive(systemPrompt)))
            messages.forEach { turn ->
                val content = if (turn.role == ROLE_USER && turn.referenceImages.isNotEmpty()) {
                    LlmHttpClient.userContent(turn.content, turn.referenceImages.map(::dataUri))
                } else {
                    JsonPrimitive(turn.content)
                }
                add(message(turn.role, content))
            }
        }

        val fullContent = StringBuilder()
        var continuations = 0
        var lastUsage: TokenUsage? = null
        val dropped = mutableSetOf<String>()

        while (true) {
            var requestMessages = if (fullContent.isEmpty()) baseMessages else baseMessages + listOf(
                message(ROLE_ASSISTANT, JsonPrimitive(fullContent.toString())),
                message(ROLE_USER, JsonPrimitive(CONTINUE_PROMPT))
            )
            if (needsTrailingUserTurn(model, requestMessages)) {
                requestMessages = requestMessages + message(ROLE_USER, JsonPrimitive(TRAILING_USER_TURN))
            }

            val response = sendWithSamplerRetry(dropped) { skip ->
                httpClient.chatCompletion(provider, buildJsonObject {
                    put("model", model)
                    put("messages", JsonArray(requestMessages))
                    put("max_tokens", maxTokens)
                    if ("temperature" !in skip) put("temperature", sampling.temperature ?: temperature)
                    if ("top_p" !in skip) sampling.topP?.let { put("top_p", it) }
                    if ("frequency_penalty" !in skip) sampling.frequencyPenalty?.let { put("frequency_penalty", it) }
                    if ("presence_penalty" !in skip) sampling.presencePenalty?.let { put("presence_penalty", it) }
                })
            }

            val choice = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error("No choices in the provider response")
            val content = extractContent(choice["message"] as? JsonObject).orEmpty()
            fullContent.append(content)
            lastUsage = (response["usage"] as? JsonObject)?.let { usage ->
                TokenUsage(
                    promptTokens = usage.int("prompt_tokens"),
                    completionTokens = usage.int("completion_tokens"),
                    totalTokens = usage.int("total_tokens")
                )
            }

            val finishReason = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull
            val truncated = finishReason.equals("length", ignoreCase = true)
            if (!allowContinuation || !truncated || content.isBlank() || continuations >= MAX_CONTINUATIONS) break
            continuations++
        }

        ChatCompletionResult(
            content = fullContent.toString().trim(),
            usage = lastUsage,
            modelUsed = modelRef
        )
    }

    /** Sends, and on a 400 that names one of our sampler fields, retries without it. Accumulates
     * into [dropped] so a model refusing two fields converges instead of ping-ponging. */
    private suspend fun sendWithSamplerRetry(
        dropped: MutableSet<String>,
        send: suspend (Set<String>) -> JsonObject
    ): JsonObject {
        while (true) {
            try {
                return send(dropped)
            } catch (e: ProviderHttpException) {
                val rejected = rejectedSamplerParam(e.code, e.body)
                if (rejected == null || rejected in dropped) throw e
                dropped += rejected
            }
        }
    }

    private fun message(role: String, content: JsonElement) = buildJsonObject {
        put("role", role)
        put("content", content)
    }

    private fun dataUri(bytes: ByteArray): String =
        "data:${sniffImageMime(bytes)};base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    internal companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        /** Mistral rejects a conversation ending on an assistant turn. Matched on the id rather than
         * the provider, since Mistral models are served by many gateways. */
        fun needsTrailingUserTurn(modelId: String, messages: List<JsonObject>): Boolean =
            modelId.contains("mistral", ignoreCase = true) &&
                (messages.lastOrNull()?.get("role") as? JsonPrimitive)?.contentOrNull == ROLE_ASSISTANT

        /** Names the sampler parameter a model has just refused, or null if this error is something
         * else. Deliberately narrow: only a 400 whose body names one of *our* fields next to an
         * "unsupported" phrasing, so a genuine bad request still surfaces. */
        fun rejectedSamplerParam(code: Int, body: String): String? {
            if (code != 400) return null
            val looksUnsupported = body.contains("Unsupported value", ignoreCase = true) ||
                body.contains("does not support", ignoreCase = true) ||
                body.contains("only the default", ignoreCase = true) ||
                body.contains("unsupported parameter", ignoreCase = true) ||
                body.contains("not supported", ignoreCase = true)
            if (!looksUnsupported) return null
            return SAMPLER_PARAMS.firstOrNull { body.contains(it, ignoreCase = true) }
        }

        /** The reply text, wherever the model put it. */
        fun extractContent(message: JsonObject?): String? {
            message ?: return null
            when (val content = message["content"]) {
                is JsonPrimitive -> content.contentOrNull?.takeIf { it.isNotEmpty() }?.let { return it }
                is JsonArray -> content.mapNotNull { part ->
                    ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull
                }.joinToString("").takeIf { it.isNotEmpty() }?.let { return it }
                else -> Unit
            }
            return (message["reasoning_content"] as? JsonPrimitive)?.contentOrNull
                ?: (message["reasoning"] as? JsonPrimitive)?.contentOrNull
        }

        fun sniffImageMime(bytes: ByteArray): String = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            bytes.size >= 4 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() -> "image/webp"
            else -> "image/png"
        }
    }
}
