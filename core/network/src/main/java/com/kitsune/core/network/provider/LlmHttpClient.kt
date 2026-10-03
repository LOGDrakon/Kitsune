package com.kitsune.core.network.provider

import android.util.Base64
import com.kitsune.core.common.coroutines.DispatcherProvider
import com.kitsune.core.network.BuildConfig
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A non-2xx answer from a provider, with its body kept for error messages and retry decisions. */
class ProviderHttpException(val code: Int, val body: String) :
    IOException("HTTP $code: ${body.take(300)}")

/**
 * The one HTTP client for every provider. All of them speak the OpenAI-compatible format, so the
 * differences are limited to a base URL, an optional key, and OpenRouter's extra `provider` routing
 * object and attribution headers.
 *
 * This replaces the hosted version's backend proxy. The workarounds that proxy applied on every
 * request live on in [com.kitsune.core.network.repository.ChatCompletionRepositoryImpl], which
 * builds the bodies sent through here.
 */
@Singleton
class LlmHttpClient @Inject constructor(
    private val dispatchers: DispatcherProvider
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val jsonMedia = "application/json".toMediaType()

    // A heavy generation (persona or universe sheet, 4096 max tokens) can legitimately take well
    // over a minute under model load; a short read timeout only abandons a request the provider was
    // about to answer.
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC only: headers would print the API key, BODY would print the conversation.
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    suspend fun chatCompletion(provider: ProviderConfig, body: JsonObject): JsonObject =
        postJson(provider, "chat/completions", withRouting(provider, body))

    suspend fun embeddings(provider: ProviderConfig, model: String, input: String): List<Float> {
        val response = postJson(provider, "embeddings", withRouting(provider, buildJsonObject {
            put("model", model)
            put("input", input)
        }))
        val vector = response["data"]?.jsonArray?.firstOrNull()?.jsonObject?.get("embedding")?.jsonArray
            ?: throw IOException("Embedding response contained no vector")
        return vector.map { it.jsonPrimitive.content.toFloat() }
    }

    /**
     * Generates images, trying the endpoints that exist in the wild in order:
     *
     * 1. OpenRouter's dedicated `POST /images` (OpenRouter does not return generated images through
     *    `/chat/completions` for dedicated image models — found and verified by the hosted backend,
     *    whose `OpenRouterImageGenerator` this ports), or OpenAI's `POST /images/generations` for
     *    every other preset.
     * 2. `/chat/completions` with `modalities: ["image","text"]`, which is how multimodal chat models
     *    (Gemini image variants and the like) return images on several gateways.
     *
     * [referenceDataUris] are `data:` URIs of reference images (the persona's avatar, for visual
     * continuity); providers that do not support them ignore them.
     */
    suspend fun generateImages(
        provider: ProviderConfig,
        model: String,
        prompt: String,
        referenceDataUris: List<String>,
        resolution: String,
        quality: String
    ): List<ByteArray> {
        val dedicated = runCatching {
            if (provider.preset.isOpenRouter) {
                val response = postJson(provider, "images", withRouting(provider, buildJsonObject {
                    put("model", model)
                    put("prompt", prompt)
                    put("n", 1)
                    put("resolution", resolution)
                    put("quality", quality)
                    if (referenceDataUris.isNotEmpty()) {
                        put("input_references", buildJsonArray {
                            referenceDataUris.forEach { uri ->
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject { put("url", uri) })
                                })
                            }
                        })
                    }
                }))
                imagesFromDataArray(response)
            } else {
                val response = postJson(provider, "images/generations", buildJsonObject {
                    put("model", model)
                    put("prompt", prompt)
                    put("n", 1)
                    put("response_format", "b64_json")
                })
                imagesFromDataArray(response)
            }
        }
        dedicated.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        val viaChat = runCatching {
            val response = chatCompletion(provider, buildJsonObject {
                put("model", model)
                put("modalities", buildJsonArray { add(JsonPrimitive("image")); add(JsonPrimitive("text")) })
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", userContent(prompt, referenceDataUris))
                    })
                })
            })
            imagesFromChatResponse(response)
        }
        viaChat.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        // Neither path produced an image: surface the most informative failure.
        throw (dedicated.exceptionOrNull() ?: viaChat.exceptionOrNull()
            ?: IOException("The model returned no image"))
    }

    /** `GET /models`, plus OpenRouter's separate embeddings listing (its default listing omits
     * embedding models entirely — they only appear for `?output_modalities=embeddings`). */
    suspend fun listModels(provider: ProviderConfig): List<JsonObject> {
        val main = getJson(provider, "models")["data"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
        if (!provider.preset.isOpenRouter) return main
        val embeddings = runCatching {
            getJson(provider, "models", mapOf("output_modalities" to "embeddings"))["data"]?.jsonArray.orEmpty()
                .mapNotNull { it as? JsonObject }
        }.getOrDefault(emptyList())
        val seen = main.mapNotNull { it.string("id") }.toSet()
        return main + embeddings.filter { it.string("id") !in seen }
    }

    /** OpenRouter only: the upstream endpoints serving [modelId], with their quantization, price,
     * uptime, latency and throughput — what the routing settings screen shows before the user
     * restricts anything. */
    suspend fun listEndpoints(provider: ProviderConfig, modelId: String): List<OpenRouterEndpoint> {
        val clean = modelId.substringBefore(':')
        val response = getJson(provider, "models/$clean/endpoints")
        val endpoints = response["data"]?.jsonObject?.get("endpoints")?.jsonArray.orEmpty()
        return endpoints.mapNotNull { (it as? JsonObject)?.let(OpenRouterEndpoint::fromJson) }
    }

    /** Cheap reachability + key check for the provider setup screen. */
    suspend fun testConnection(provider: ProviderConfig): Result<Int> = runCatching {
        getJson(provider, "models")["data"]?.jsonArray?.size ?: 0
    }

    // ---------------------------------------------------------------------------------------------

    private fun withRouting(provider: ProviderConfig, body: JsonObject): JsonObject =
        if (!provider.preset.isOpenRouter || body.containsKey("provider")) body
        else JsonObject(body + ("provider" to provider.routing.toJson()))

    private fun requestBuilder(provider: ProviderConfig, path: String, query: Map<String, String> = emptyMap()): Request.Builder {
        val base = "${provider.normalizedBaseUrl}/$path".toHttpUrlOrNull()
            ?: throw IOException("Invalid provider URL: ${provider.baseUrl}")
        val url = base.newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return Request.Builder().url(url).apply {
            if (provider.apiKey.isNotBlank()) header("Authorization", "Bearer ${provider.apiKey}")
            if (provider.preset.isOpenRouter) {
                header("HTTP-Referer", APP_URL)
                header("X-Title", APP_NAME)
            }
        }
    }

    private suspend fun postJson(provider: ProviderConfig, path: String, body: JsonObject): JsonObject =
        execute(requestBuilder(provider, path).post(body.toString().toRequestBody(jsonMedia)).build())

    private suspend fun getJson(provider: ProviderConfig, path: String, query: Map<String, String> = emptyMap()): JsonObject =
        execute(requestBuilder(provider, path, query).get().build())

    private suspend fun execute(request: Request): JsonObject = withContext(dispatchers.io) {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ProviderHttpException(response.code, text)
            // Some gateways answer 200 with an `error` object instead of a status code.
            val parsed = runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrElse { throw IOException("Unreadable provider response (${text.length} bytes)") }
            parsed["error"]?.let { error ->
                if (parsed["choices"] == null && parsed["data"] == null) {
                    val code = (error as? JsonObject)?.get("code")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 502
                    throw ProviderHttpException(code, error.toString())
                }
            }
            parsed
        }
    }

    private suspend fun imagesFromDataArray(response: JsonObject): List<ByteArray> =
        response["data"]?.jsonArray.orEmpty().mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            obj.string("b64_json")?.let { decodeBase64(it) }
                ?: obj.string("url")?.let { url -> if (url.startsWith("data:")) decodeDataUri(url) else download(url) }
        }

    private fun imagesFromChatResponse(response: JsonObject): List<ByteArray> {
        val message = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: return emptyList()
        val fromImages = message["images"]?.jsonArray.orEmpty().mapNotNull { img ->
            (img as? JsonObject)?.get("image_url")?.jsonObject?.string("url")?.let(::decodeDataUri)
        }
        if (fromImages.isNotEmpty()) return fromImages
        // Some gateways put images among the content parts instead.
        return (message["content"] as? JsonArray).orEmpty().mapNotNull { part ->
            (part as? JsonObject)?.get("image_url")?.jsonObject?.string("url")?.let(::decodeDataUri)
        }
    }

    private suspend fun download(url: String): ByteArray? = withContext(dispatchers.io) {
        runCatching { http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() } }.getOrNull()
    }

    companion object {
        const val APP_URL = "https://github.com/LOGDrakon/Kitsune"
        const val APP_NAME = "Kitsune"

        /** OpenAI-style user content: a plain string, or text + image parts when images are attached. */
        fun userContent(text: String, imageDataUris: List<String>): JsonElement =
            if (imageDataUris.isEmpty()) JsonPrimitive(text)
            else buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", text) })
                imageDataUris.forEach { uri ->
                    add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", uri) })
                    })
                }
            }

        fun decodeDataUri(uri: String): ByteArray? =
            if (!uri.startsWith("data:")) null else decodeBase64(uri.substringAfter(','))

        fun decodeBase64(b64: String): ByteArray? = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
    }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

/** One upstream serving an OpenRouter model. Fields OpenRouter does not report stay null. */
data class OpenRouterEndpoint(
    val providerName: String,
    /** Slug usable in [OpenRouterRouting.only]/[OpenRouterRouting.ignore]/[OpenRouterRouting.order]. */
    val providerSlug: String?,
    val quantization: String?,
    val contextLength: Int?,
    val promptPricePerMillion: Double?,
    val completionPricePerMillion: Double?,
    val uptimePercent: Double?,
    val latencySeconds: Double?,
    val throughputTokensPerSecond: Double?,
    val status: Int?
) {
    companion object {
        fun fromJson(obj: JsonObject): OpenRouterEndpoint {
            val pricing = obj["pricing"] as? JsonObject
            fun perMillion(key: String) = pricing?.double(key)?.times(1_000_000)
            fun percentileOrNumber(key: String): Double? = when (val el = obj[key]) {
                is JsonPrimitive -> el.contentOrNull?.toDoubleOrNull()
                is JsonObject -> el.double("p50")
                else -> null
            }
            val latencyRaw = percentileOrNumber("latency_last_30m")
            return OpenRouterEndpoint(
                providerName = obj.string("provider_name") ?: obj.string("name") ?: "?",
                providerSlug = obj.string("tag")?.substringBefore('/') ?: obj.string("provider_slug"),
                quantization = obj.string("quantization"),
                contextLength = obj.string("context_length")?.toIntOrNull(),
                promptPricePerMillion = perMillion("prompt"),
                completionPricePerMillion = perMillion("completion"),
                uptimePercent = obj.double("uptime_last_30m"),
                // OpenRouter reports latency in milliseconds; the routing preference takes seconds.
                latencySeconds = latencyRaw?.let { if (it > 100) it / 1000.0 else it },
                throughputTokensPerSecond = percentileOrNumber("throughput_last_30m"),
                status = obj.string("status")?.toIntOrNull()
            )
        }
    }
}
