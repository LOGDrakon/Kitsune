package com.kitsune.core.network.provider

import com.google.common.truth.Truth.assertThat
import com.kitsune.core.network.catalog.ModelCategoryClassifier
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.core.network.repository.ChatCompletionRepositoryImpl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test

class ProviderLayerTest {

    // ---- ModelRef ---------------------------------------------------------------------------

    @Test
    fun `a model ref round-trips provider and model, including slashes and colons in the model id`() {
        val ref = ModelRef.of("abc", "deepseek/deepseek-v4-flash:free")
        assertThat(ModelRef.parse(ref)).isEqualTo("abc" to "deepseek/deepseek-v4-flash:free")
    }

    @Test
    fun `a bare model id has no provider and resolves against the default`() {
        assertThat(ModelRef.parse("gpt-5")).isEqualTo(null to "gpt-5")
    }

    // ---- OpenRouter routing -----------------------------------------------------------------

    @Test
    fun `default routing only states a sort and fallbacks`() {
        val json = OpenRouterRouting().toJson()
        assertThat(json.keys).containsExactly("sort", "allow_fallbacks")
        assertThat(json["sort"]!!.jsonPrimitive.content).isEqualTo("throughput")
    }

    @Test
    fun `routing serialises every restriction with OpenRouter's field names`() {
        val json = OpenRouterRouting(
            sort = null,
            quantizations = listOf("fp8", "bf16"),
            preferredMinThroughput = 40.0,
            preferredMaxLatency = 2.0,
            order = listOf("deepinfra"),
            only = listOf("deepinfra", "together"),
            ignore = listOf("cheapco"),
            denyDataCollection = true,
            zeroDataRetention = true,
            allowFallbacks = false,
            requireParameters = true
        ).toJson()

        assertThat(json.containsKey("sort")).isFalse()
        assertThat((json["quantizations"] as JsonArray).map { it.jsonPrimitive.content }).containsExactly("fp8", "bf16")
        assertThat(json["preferred_min_throughput"]!!.jsonPrimitive.content.toDouble()).isEqualTo(40.0)
        assertThat(json["preferred_max_latency"]!!.jsonPrimitive.content.toDouble()).isEqualTo(2.0)
        assertThat(json["data_collection"]!!.jsonPrimitive.content).isEqualTo("deny")
        assertThat(json["zdr"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(json["allow_fallbacks"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat(json["require_parameters"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat((json["ignore"] as JsonArray).single().jsonPrimitive.content).isEqualTo("cheapco")
    }

    // ---- Workarounds ported from the hosted backend ----------------------------------------

    @Test
    fun `a 400 naming one of our sampler fields is recognised as a refused parameter`() {
        val body = """{"error":{"message":"Unsupported value: 'temperature' does not support 0.9 with this model. Only the default (1) value is supported."}}"""
        assertThat(ChatCompletionRepositoryImpl.rejectedSamplerParam(400, body)).isEqualTo("temperature")
    }

    @Test
    fun `a genuine bad request or another status is not retried`() {
        assertThat(ChatCompletionRepositoryImpl.rejectedSamplerParam(400, """{"error":"messages is required"}""")).isNull()
        assertThat(ChatCompletionRepositoryImpl.rejectedSamplerParam(401, "top_p does not support")).isNull()
    }

    @Test
    fun `a Mistral conversation ending on an assistant turn gets a trailing user turn, others do not`() {
        val endsOnAssistant = listOf(
            buildJsonObject { put("role", "system"); put("content", "s") },
            buildJsonObject { put("role", "assistant"); put("content", "a") }
        )
        assertThat(ChatCompletionRepositoryImpl.needsTrailingUserTurn("mistralai/mistral-medium-3.1", endsOnAssistant)).isTrue()
        assertThat(ChatCompletionRepositoryImpl.needsTrailingUserTurn("mistral-large-latest", endsOnAssistant)).isTrue()
        assertThat(ChatCompletionRepositoryImpl.needsTrailingUserTurn("deepseek/deepseek-v4-flash", endsOnAssistant)).isFalse()
    }

    @Test
    fun `reply text is recovered from content parts or reasoning when content is null`() {
        val parts = buildJsonObject {
            put("content", buildJsonArray {
                add(buildJsonObject { put("type", "text"); put("text", "Hello ") })
                add(buildJsonObject { put("type", "text"); put("text", "world") })
            })
        }
        assertThat(ChatCompletionRepositoryImpl.extractContent(parts)).isEqualTo("Hello world")

        val reasoning = buildJsonObject {
            put("content", JsonPrimitive(null as String?))
            put("reasoning_content", "the reply")
        }
        assertThat(ChatCompletionRepositoryImpl.extractContent(reasoning)).isEqualTo("the reply")
    }

    // ---- Classification ---------------------------------------------------------------------

    @Test
    fun `stated output modalities win over the id, and the id is the fallback`() {
        assertThat(ModelCategoryClassifier.classify("voyageai/voyage-code-4", listOf("embeddings"))).isEqualTo("embedding")
        assertThat(ModelCategoryClassifier.classify("google/gemini-2.5-flash-image", listOf("image", "text"))).isEqualTo("image")
        assertThat(ModelCategoryClassifier.classify("text-embedding-3-small")).isEqualTo("embedding")
        assertThat(ModelCategoryClassifier.classify("whisper-1")).isEqualTo("audio")
        assertThat(ModelCategoryClassifier.classify("mistral-large-latest")).isEqualTo("chat")
    }

    // ---- Error messages ---------------------------------------------------------------------

    @Test
    fun `provider errors say what to do and quote the provider's own message`() {
        val msg = NetworkErrorMessages.forUser(ProviderHttpException(402, """{"error":{"message":"Insufficient credits"}}"""))
        assertThat(msg).contains("Crédit insuffisant")
        assertThat(msg).contains("Insufficient credits")

        assertThat(NetworkErrorMessages.forUser(ProviderHttpException(401, "nope"))).contains("Clé API refusée")
        assertThat(NetworkErrorMessages.forUser(ProviderHttpException(429, ""))).contains("limite de débit")
    }

    // ---- OpenRouter endpoints ---------------------------------------------------------------

    @Test
    fun `an endpoint entry is parsed with per-million prices and latency in seconds`() {
        val endpoint = OpenRouterEndpoint.fromJson(buildJsonObject {
            put("provider_name", "DeepInfra")
            put("tag", "deepinfra/fp8")
            put("quantization", "fp8")
            put("context_length", 163840)
            put("pricing", buildJsonObject { put("prompt", "0.00000027"); put("completion", "0.0000011") })
            put("uptime_last_30m", 99.5)
            put("latency_last_30m", buildJsonObject { put("p50", 850) })
            put("throughput_last_30m", buildJsonObject { put("p50", 42.0) })
        })
        assertThat(endpoint.providerSlug).isEqualTo("deepinfra")
        assertThat(endpoint.quantization).isEqualTo("fp8")
        assertThat(endpoint.promptPricePerMillion!!).isWithin(1e-9).of(0.27)
        assertThat(endpoint.latencySeconds!!).isWithin(1e-9).of(0.85)
        assertThat(endpoint.throughputTokensPerSecond).isEqualTo(42.0)
        assertThat(endpoint.contextLength).isEqualTo(163840)
    }
}
