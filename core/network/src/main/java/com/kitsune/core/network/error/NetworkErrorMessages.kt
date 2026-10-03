package com.kitsune.core.network.error

import com.kitsune.core.network.provider.ProviderHttpException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns the errors a user can actually hit into short French messages, instead of leaking raw
 * exception text such as `Unable to resolve host "openrouter.ai": No address associated with
 * hostname` into error banners.
 *
 * Provider HTTP errors get a message that says what to do about them — most of them are about the
 * user's own account at their provider (key refused, credit exhausted, rate limit), which the app
 * cannot fix and the user can. The provider's own error text is appended when it has one, since it
 * is usually the most precise explanation available.
 */
object NetworkErrorMessages {
    fun forUser(throwable: Throwable, fallback: String = "Erreur inconnue"): String = when (throwable) {
        is ProviderHttpException -> forProvider(throwable)
        is UnknownHostException -> "Pas de connexion internet — vérifiez votre connexion et réessayez."
        is SocketTimeoutException -> "Le fournisseur ne répond pas (délai dépassé) — réessayez dans un instant."
        is IOException -> "Impossible de contacter le fournisseur — vérifiez votre connexion et réessayez."
        else -> throwable.message ?: fallback
    }

    private fun forProvider(e: ProviderHttpException): String {
        val base = when (e.code) {
            401, 403 -> "Clé API refusée par le fournisseur. Vérifiez-la dans Réglages → Fournisseurs d'IA."
            402 -> "Crédit insuffisant chez votre fournisseur. Rechargez votre compte chez lui."
            404 -> "Modèle introuvable chez ce fournisseur. Choisissez-en un autre dans Réglages → Modèles."
            408, 504 -> "Le fournisseur a mis trop de temps à répondre — réessayez."
            429 -> "Trop de requêtes : limite de débit du fournisseur atteinte. Patientez un peu."
            in 500..599 -> "Le fournisseur rencontre un problème (erreur ${e.code}). Réessayez plus tard."
            else -> "Le fournisseur a refusé la requête (erreur ${e.code})."
        }
        val detail = providerMessage(e.body)
        return if (detail.isNullOrBlank()) base else "$base\n« ${detail.take(200)} »"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** `{"error":{"message":"..."}}` or `{"error":"..."}` or `{"message":"..."}` — whatever the
     * provider used. */
    private fun providerMessage(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        when (val error = root["error"]) {
            is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> error.contentOrNull
            else -> (root["message"] as? JsonPrimitive)?.contentOrNull
        }
    }.getOrNull()
}
