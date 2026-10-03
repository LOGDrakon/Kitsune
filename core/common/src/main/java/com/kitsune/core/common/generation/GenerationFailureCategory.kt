package com.kitsune.core.common.generation

/**
 * Why an async persona/universe generation job failed — lets the UI explain the failure instead of
 * a single opaque message, and lets a bug report built from it include structured context.
 *
 * The names are stored with failed jobs, so they are kept stable even where their meaning moved
 * with the switch to the user's own provider.
 */
enum class GenerationFailureCategory {
    /** The provider refused the content (its own moderation). Retrying the same description will
     * usually fail again; another model or provider may accept it. */
    CONTENT_POLICY,
    /** The provider says the account has no credit left (HTTP 402). */
    INSUFFICIENT_CREDITS,
    /** The model answered, but its output couldn't be parsed into a usable draft. */
    PARSING_FAILED,
    /** Network or provider error (timeout, 5xx, connectivity, bad key). */
    TECHNICAL
}
