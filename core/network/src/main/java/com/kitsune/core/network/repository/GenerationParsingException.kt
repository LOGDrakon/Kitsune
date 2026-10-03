package com.kitsune.core.network.repository

/** Thrown when a persona/universe generation call reached the LLM and got a response — so the
 * provider has already charged for it — but that response could not be parsed into a usable draft
 * (malformed/incomplete JSON, missing fields). Distinct from a failure of
 * [ChatCompletionRepository.complete] itself. */
class GenerationParsingException(message: String, cause: Throwable) : Exception(message, cause)
