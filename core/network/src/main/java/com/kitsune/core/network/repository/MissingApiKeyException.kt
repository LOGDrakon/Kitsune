package com.kitsune.core.network.repository

/** Surfaced by [ChatCompletionRepository] instead of attempting a network call with no credentials.
 * Currently unused: the app hasn't held a provider API key itself since the backend took over proxying
 * every AI call (first to Mammouth.ai, now to OpenRouter) — kept in case a future direct-from-app call
 * path needs this again. */
class MissingApiKeyException : Exception("Aucune clé API configurée. Ouvrez les paramètres pour en ajouter une.")
