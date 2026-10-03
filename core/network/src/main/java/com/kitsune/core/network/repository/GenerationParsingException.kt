package com.kitsune.core.network.repository

/** Thrown when a persona/universe generation call reached the LLM and got a response — so the
 * backend already billed a credit for it (charge-on-success, see `ProxyRoutes.kt`) — but that
 * response could not be parsed into a usable draft (malformed/incomplete JSON, missing fields).
 * Distinct from a failure of [ChatCompletionRepository.complete] itself, which never bills. */
class GenerationParsingException(message: String, cause: Throwable) : Exception(message, cause)
