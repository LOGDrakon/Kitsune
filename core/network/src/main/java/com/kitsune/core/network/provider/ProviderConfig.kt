package com.kitsune.core.network.provider

import kotlinx.serialization.Serializable

/**
 * One provider account the user has added: which service, where it lives, and the key to reach it.
 *
 * Several can coexist — chat on Mammouth and embeddings on OpenAI, say — and every model selection
 * records which provider it belongs to (see [ModelRef]), so the same model id on two providers never
 * gets confused.
 *
 * [apiKey] lives only inside [ProviderStore]'s encrypted storage and is sent only to [baseUrl]. It is
 * never logged and never leaves the device any other way.
 */
@Serializable
data class ProviderConfig(
    /** Stable local id, generated once. Part of every [ModelRef] that points at this provider. */
    val id: String,
    val preset: ProviderPreset,
    /** Shown in pickers; defaults to the preset's name, editable so two accounts on the same service
     *  can be told apart. */
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    /** OpenRouter-only provider routing (quantization, sort, latency…). Ignored for other presets. */
    val routing: OpenRouterRouting = OpenRouterRouting()
) {
    val normalizedBaseUrl: String get() = baseUrl.trim().trimEnd('/')
}

/**
 * A model selection: which provider, and which of its models.
 *
 * Stored as a single string (`"<providerId>::<modelId>"`) so it fits the existing per-operation
 * preference slots and travels through every call site that already passes a model id around as a
 * `String`. A string without the separator is a bare model id from before providers existed, or
 * typed by hand; it resolves against the default provider.
 */
object ModelRef {
    private const val SEPARATOR = "::"

    fun of(providerId: String, modelId: String): String = "$providerId$SEPARATOR$modelId"

    /** Returns `(providerId or null, modelId)`. */
    fun parse(ref: String): Pair<String?, String> {
        val index = ref.indexOf(SEPARATOR)
        return if (index < 0) null to ref else ref.substring(0, index) to ref.substring(index + SEPARATOR.length)
    }

    fun modelIdOf(ref: String): String = parse(ref).second
}
