package com.kitsune.core.network.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OpenRouter's provider routing preferences — the `provider` object of a request.
 *
 * A single OpenRouter model is usually served by several upstream providers, and they are not
 * equivalent: precision (quantization), speed and reliability vary a lot between them. By default
 * OpenRouter routes to whichever is cheapest right now, with no quality floor. These settings let
 * the user choose instead.
 *
 * Reference: https://openrouter.ai/docs/features/provider-routing
 *
 * ## A warning carried over from the hosted version of Kitsune
 *
 * [quantizations] is a hard allow-list. Restricting it to `bf16/fp16/fp32` once broke every request
 * to `deepseek/deepseek-v4-flash` outright (`404 No endpoints found ... with quantization`), because
 * all 18 of that model's endpoints served fp8 or fp4: fp8 is the normal serving precision for many
 * model families, not a degraded tier. The settings screen therefore shows, for the selected model,
 * which quantizations its endpoints actually report (see [LlmHttpClient.listEndpoints]) before the
 * user restricts anything.
 */
@Serializable
data class OpenRouterRouting(
    /** `null` keeps OpenRouter's default load balancing; otherwise `price`, `throughput` or `latency`. */
    val sort: String? = SORT_THROUGHPUT,
    /** Empty = no restriction. Values: int4, int8, fp4, fp6, fp8, fp16, bf16, fp32, unknown. */
    val quantizations: List<String> = emptyList(),
    /** Preferred minimum tokens/second (p50). Endpoints below it are deprioritised, not excluded. */
    val preferredMinThroughput: Double? = null,
    /** Preferred maximum latency in seconds (p50). Endpoints above it are deprioritised, not excluded. */
    val preferredMaxLatency: Double? = null,
    /** Provider slugs to try first, in order. */
    val order: List<String> = emptyList(),
    /** Provider slugs to use exclusively. Empty = all. */
    val only: List<String> = emptyList(),
    /** Provider slugs never to use. */
    val ignore: List<String> = emptyList(),
    /** Exclude providers that may store or train on prompts. */
    val denyDataCollection: Boolean = false,
    /** Only route to Zero Data Retention endpoints. */
    val zeroDataRetention: Boolean = false,
    /** Let OpenRouter fall back to other providers when the preferred ones fail. */
    val allowFallbacks: Boolean = true,
    /** Only route to providers that support every parameter in the request. */
    val requireParameters: Boolean = false
) {
    fun toJson(): JsonObject = buildJsonObject {
        sort?.let { put("sort", it) }
        if (quantizations.isNotEmpty()) put("quantizations", stringArray(quantizations))
        preferredMinThroughput?.let { put("preferred_min_throughput", it) }
        preferredMaxLatency?.let { put("preferred_max_latency", it) }
        if (order.isNotEmpty()) put("order", stringArray(order))
        if (only.isNotEmpty()) put("only", stringArray(only))
        if (ignore.isNotEmpty()) put("ignore", stringArray(ignore))
        if (denyDataCollection) put("data_collection", "deny")
        if (zeroDataRetention) put("zdr", true)
        put("allow_fallbacks", allowFallbacks)
        if (requireParameters) put("require_parameters", true)
    }

    companion object {
        const val SORT_PRICE = "price"
        const val SORT_THROUGHPUT = "throughput"
        const val SORT_LATENCY = "latency"

        val ALL_QUANTIZATIONS = listOf("fp32", "bf16", "fp16", "fp8", "fp6", "fp4", "int8", "int4", "unknown")

        /**
         * The simple level of the routing screen: four plain-language presets. Anything else is
         * "Personnalisé" and shown with the detailed controls open.
         */
        val PRESETS: Map<String, OpenRouterRouting> = linkedMapOf(
            PRESET_FAST to OpenRouterRouting(),
            PRESET_CHEAP to OpenRouterRouting(sort = SORT_PRICE),
            // Excludes the 4-bit and 6-bit quantisations that noticeably degrade long-form writing.
            PRESET_QUALITY to OpenRouterRouting(quantizations = listOf("fp32", "bf16", "fp16", "fp8", "int8", "unknown")),
            PRESET_PRIVATE to OpenRouterRouting(denyDataCollection = true, zeroDataRetention = true)
        )
        const val PRESET_FAST = "fast"
        const val PRESET_CHEAP = "cheap"
        const val PRESET_QUALITY = "quality"
        const val PRESET_PRIVATE = "private"

        /** The preset [routing] is exactly, or null when it has been customised. */
        fun presetOf(routing: OpenRouterRouting): String? = PRESETS.entries.firstOrNull { it.value == routing }?.key
    }
}

private fun stringArray(values: List<String>) = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }
