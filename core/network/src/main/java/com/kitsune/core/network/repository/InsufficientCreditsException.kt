package com.kitsune.core.network.repository

/** Thrown when the backend rejects a chat completion with HTTP 402 (not enough Ofudas). Callers
 * should offer to send the user to the store rather than a generic retry/change-model banner. */
class InsufficientCreditsException(
    val currentBalance: Int,
    val requiredCredits: Int
) : Exception("Insufficient credits: balance=$currentBalance required=$requiredCredits")
