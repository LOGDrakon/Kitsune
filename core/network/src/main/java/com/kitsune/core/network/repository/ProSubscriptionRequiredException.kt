package com.kitsune.core.network.repository

/** Thrown when the backend rejects a chat completion with HTTP 403 / `PRO_REQUIRES_SUBSCRIPTION`
 * (Pro mode is a Kitsune+ perk again — see `ProxyRoutes.kt`'s access gate). Reached only as a defensive
 * fallback: `ChatViewModel` already keeps a non-subscriber from setting Pro mode client-side, but an
 * in-flight request can still land here if the subscription lapsed between opening the app and sending.
 * Callers should reset Pro mode and offer to send the user to the store, same as
 * [InsufficientCreditsException], rather than a generic retry/change-model banner. */
class ProSubscriptionRequiredException :
    Exception("Pro mode requires an active Kitsune+ subscription")
