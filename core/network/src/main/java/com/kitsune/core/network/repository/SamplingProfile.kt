package com.kitsune.core.network.repository

/**
 * Decoder controls for one completion, beyond the temperature the app has always sent (2026-08-23).
 *
 * ## Why this exists
 *
 * Until now `ChatCompletionRequest` carried `temperature` and nothing else — and the backend proxy
 * re-serialises its own typed request object, so no other parameter could reach OpenRouter even if
 * the app had sent one. That meant **no repetition control anywhere in the product**, which is the
 * failure users of this kind of app report first: replies start echoing their own phrasing after a
 * few dozen turns. Prose instructions cannot fix that; they operate on what to write, not on how the
 * next token is chosen.
 *
 * ## Why every field is nullable
 *
 * `null` means "say nothing about it", and the whole chain is built so that saying nothing produces
 * exactly the request the app sent before this type existed: the app omits the field, the proxy's
 * `Json` runs with `explicitNulls = false`, and the upstream applies its own default. That is what
 * makes this safe to add to a live product — every existing path keeps its current behaviour until
 * something deliberately opts in.
 *
 * Support is uneven across the upstreams OpenRouter fronts; a model that refuses one of these
 * answers 400, and the proxy retries once without the offending field (`rejectedSamplerParam`).
 */
data class SamplingProfile(
    val temperature: Double? = null,
    val topP: Double? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null
) {
    companion object {
        /** Says nothing at all — the behaviour of every call made before sampling profiles existed. */
        val INHERIT = SamplingProfile()
    }
}
