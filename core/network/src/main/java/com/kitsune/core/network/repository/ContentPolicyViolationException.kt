package com.kitsune.core.network.repository

/** Thrown when the backend rejects a chat completion with HTTP 451 — the server-side moderation
 * backstop (`ModerationService.checkChatContent` on the backend), now the primary, unconditional,
 * multilingual moderation gate (a small FR/EN hard keyword net plus an AI classifier that runs on
 * every message regardless of language). This runs in addition to the app-side `LocalKeywordFilter`
 * (now reduced to just that hard net) and cannot be bypassed by a modified client. Callers should
 * show a fixed explanatory dialog, not a generic retry banner — resending the same content will
 * just be rejected again.
 *
 * [category]/[flaggedExcerpt] (when present) let the caller capture a tamper-resistant "Contenu
 * d'intérêt" excerpt for a report at the moment of blocking — sourced from the server's own
 * classifier judgment, not anything the user could edit before reporting (demande explicite). */
class ContentPolicyViolationException(
    val category: String? = null,
    val flaggedExcerpt: String? = null
) : Exception("Content policy violation (451)")
