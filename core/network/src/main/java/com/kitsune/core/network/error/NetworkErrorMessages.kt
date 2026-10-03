package com.kitsune.core.network.error

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Translates the handful of network exceptions a user is actually likely to hit (no internet,
 * server unreachable, request timeout) into short French messages, instead of leaking raw Java
 * exception text — [UnknownHostException]'s own `message` is literally the technical DNS failure
 * string (e.g. `Unable to resolve host "api.mammouth.ai": No address associated with hostname`),
 * which was showing up verbatim in error banners across the app (bug reported by the user).
 *
 * Anything not recognized as a connectivity issue falls back to [fallback] (or the exception's own
 * message if it has one) — this only narrows the small set of cases known to be confusing, it
 * doesn't hide or generalize every other error.
 */
object NetworkErrorMessages {
    fun forUser(throwable: Throwable, fallback: String = "Erreur inconnue"): String = when (throwable) {
        is UnknownHostException -> "Pas de connexion internet — vérifiez votre connexion et réessayez."
        is SocketTimeoutException -> "Le serveur ne répond pas (délai dépassé) — réessayez dans un instant."
        is IOException -> "Impossible de contacter le serveur — vérifiez votre connexion et réessayez."
        else -> throwable.message ?: fallback
    }
}
