package com.kitsune.core.common.generation

/**
 * Why an async persona/universe generation job failed — lets the UI explain the failure instead of
 * a single opaque message, and lets a bug report built from it include structured context.
 *
 * Credit-consumption note (see `KitsuneBackend/.../proxy/ProxyRoutes.kt`): for these operations the
 * backend only ever deducts a credit *after* the LLM call itself succeeds (charge-on-success, no
 * pre-deduction/refund like the image path). So [CONTENT_POLICY]/[INSUFFICIENT_CREDITS]/[TECHNICAL]
 * — which all happen before or instead of a successful LLM response — never consume a credit; only
 * [PARSING_FAILED] does, since by definition the LLM already answered successfully by the time
 * parsing runs.
 */
enum class GenerationFailureCategory {
    /** Backend's server-side moderation backstop rejected the request (HTTP 451) — e.g. content
     * implying a minor in a sexual context. Retrying with the same description will fail again. */
    CONTENT_POLICY,
    /** Not enough Ofudas to run the generation (HTTP 402). */
    INSUFFICIENT_CREDITS,
    /** The LLM responded successfully but its output couldn't be parsed into a usable draft. */
    PARSING_FAILED,
    /** Network/server error (timeout, 5xx, connectivity) — nothing was ever billed. */
    TECHNICAL;

    /** Only [PARSING_FAILED] happens after the backend already billed for a successful LLM call. */
    val creditConsumed: Boolean get() = this == PARSING_FAILED
}
