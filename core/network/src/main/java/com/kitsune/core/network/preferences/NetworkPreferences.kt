package com.kitsune.core.network.preferences

import com.kitsune.core.network.provider.ModelRef
import com.kitsune.core.network.provider.ProviderStore
import com.kitsune.core.security.storage.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which model serves which operation, and the creative temperature.
 *
 * Every stored model is a [ModelRef] (`"<providerId>::<modelId>"`). When an operation has no
 * selection of its own it inherits the chat model; when nothing at all has been chosen yet, the
 * default provider's suggested models (see [com.kitsune.core.network.provider.ProviderPreset]) are
 * used, so a freshly added OpenRouter key works without visiting the model screen.
 */
@Singleton
class NetworkPreferences @Inject constructor(
    private val secureStorage: SecureStorage,
    private val providerStore: ProviderStore
) {

    fun getDefaultChatModelId(): String =
        secureStorage.getString(SecureStorage.KEY_DEFAULT_CHAT_MODEL_ID)?.takeIf { it.isNotBlank() }
            ?: suggested { it.suggestedChatModel }
            ?: ""

    fun setDefaultChatModelId(modelId: String) {
        secureStorage.putString(SecureStorage.KEY_DEFAULT_CHAT_MODEL_ID, modelId.trim())
    }

    /** Whether an operation has a selection of its own (as opposed to inheriting a default). */
    fun hasOwnModel(operation: LlmOperation): Boolean = when (operation) {
        LlmOperation.CHAT -> secureStorage.getString(SecureStorage.KEY_DEFAULT_CHAT_MODEL_ID).isNullOrBlank().not()
        else -> secureStorage.getString(operation.storageKey).isNullOrBlank().not()
    }

    /**
     * The model for [operation]. Embedding and image generation never inherit the chat model — a
     * chat model can serve neither `/embeddings` nor image generation — so they fall back to the
     * provider's suggestion, or to an empty string meaning "not configured" (the feature then
     * reports it instead of failing obscurely).
     */
    fun getModelForOperation(operation: LlmOperation): String = when (operation) {
        LlmOperation.CHAT -> getDefaultChatModelId()
        LlmOperation.IMAGE_GENERATION -> getDefaultImageModelId()
        LlmOperation.EMBEDDING -> stored(operation) ?: suggested { it.suggestedEmbeddingModel } ?: ""
        else -> stored(operation) ?: getDefaultChatModelId()
    }

    fun setModelForOperation(operation: LlmOperation, modelId: String) {
        when (operation) {
            LlmOperation.CHAT -> setDefaultChatModelId(modelId)
            LlmOperation.IMAGE_GENERATION -> setDefaultImageModelId(modelId)
            else -> secureStorage.putString(operation.storageKey, modelId.trim())
        }
    }

    /** Clears an operation's own selection so it inherits the default again. */
    fun clearModelForOperation(operation: LlmOperation) {
        when (operation) {
            LlmOperation.CHAT -> secureStorage.remove(SecureStorage.KEY_DEFAULT_CHAT_MODEL_ID)
            LlmOperation.IMAGE_GENERATION -> secureStorage.remove(SecureStorage.KEY_DEFAULT_IMAGE_MODEL_ID)
            else -> secureStorage.remove(operation.storageKey)
        }
    }

    fun getDefaultImageModelId(): String =
        secureStorage.getString(SecureStorage.KEY_DEFAULT_IMAGE_MODEL_ID)?.takeIf { it.isNotBlank() }
            ?: suggested { it.suggestedImageModel }
            ?: ""

    fun setDefaultImageModelId(modelId: String) {
        secureStorage.putString(SecureStorage.KEY_DEFAULT_IMAGE_MODEL_ID, modelId.trim())
    }

    /** A second image model tried when the first fails, or null when none is set. */
    fun getDefaultImageFallbackModelId(): String? =
        secureStorage.getString(SecureStorage.KEY_DEFAULT_IMAGE_FALLBACK_MODEL_ID)?.takeIf { it.isNotBlank() }

    fun setDefaultImageFallbackModelId(modelId: String) {
        secureStorage.putString(SecureStorage.KEY_DEFAULT_IMAGE_FALLBACK_MODEL_ID, modelId.trim())
    }

    /** Creative temperature (FEATURES.md section 9), 0.0–2.0. */
    fun getDefaultTemperature(): Double =
        secureStorage.getInt(SecureStorage.KEY_DEFAULT_TEMPERATURE_X100, (DEFAULT_TEMPERATURE * 100).toInt()) / 100.0

    fun setDefaultTemperature(temperature: Double) {
        secureStorage.putInt(SecureStorage.KEY_DEFAULT_TEMPERATURE_X100, (temperature * 100).toInt())
    }

    private fun stored(operation: LlmOperation): String? =
        secureStorage.getString(operation.storageKey)?.takeIf { it.isNotBlank() }

    /** The default provider's suggested model for a role, as a [ModelRef]. */
    private fun suggested(pick: (com.kitsune.core.network.provider.ProviderPreset) -> String?): String? {
        val provider = providerStore.default() ?: return null
        return pick(provider.preset)?.let { ModelRef.of(provider.id, it) }
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.9
    }
}
