package com.kitsune.core.network.preferences

import com.kitsune.core.security.storage.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkPreferences @Inject constructor(private val secureStorage: SecureStorage) {

    fun getDefaultChatModelId(): String =
        secureStorage.getString(SecureStorage.KEY_DEFAULT_CHAT_MODEL_ID)?.takeIf { it.isNotBlank() } ?: DEFAULT_CHAT_MODEL_ID

    fun setDefaultChatModelId(modelId: String) {
        secureStorage.putString(SecureStorage.KEY_DEFAULT_CHAT_MODEL_ID, modelId.trim())
    }

    fun getDefaultChatProModelId(): String =
        secureStorage.getString(SecureStorage.KEY_DEFAULT_CHAT_PRO_MODEL_ID)?.takeIf { it.isNotBlank() } ?: DEFAULT_CHAT_PRO_MODEL_ID

    fun setDefaultChatProModelId(modelId: String) {
        secureStorage.putString(SecureStorage.KEY_DEFAULT_CHAT_PRO_MODEL_ID, modelId.trim())
    }

    /** Returns the model configured for [operation], falling back to a default when no
     * operation-specific preference has been set. The default is [DEFAULT_EMBEDDING_MODEL_ID] for
     * [LlmOperation.EMBEDDING] and the default chat/image model for everything else — falling back
     * to a *chat* model for an unset embedding preference was a real bug (a chat model can never
     * serve `/embeddings`; see [com.kitsune.core.network.catalog.ModelInfo.isEmbeddingCapable]). */
    fun getModelForOperation(operation: LlmOperation): String = when (operation) {
        LlmOperation.IMAGE_GENERATION -> getDefaultImageModelId()
        LlmOperation.CHAT_PRO -> getDefaultChatProModelId()
        LlmOperation.EMBEDDING -> secureStorage.getString(operation.storageKey)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_EMBEDDING_MODEL_ID
        else -> secureStorage.getString(operation.storageKey)?.takeIf { it.isNotBlank() }
            ?: getDefaultChatModelId()
    }

    fun setModelForOperation(operation: LlmOperation, modelId: String) {
        when (operation) {
            LlmOperation.IMAGE_GENERATION -> setDefaultImageModelId(modelId)
            LlmOperation.CHAT_PRO -> setDefaultChatProModelId(modelId)
            else -> secureStorage.putString(operation.storageKey, modelId.trim())
        }
    }

    /** Whether the user has toggled Pro chat mode on (better model, 2 credits/turn instead of 1). */
    fun isProModeEnabled(): Boolean = secureStorage.getInt(SecureStorage.KEY_CHAT_MODE_PRO_ENABLED, 0) == 1

    fun setProModeEnabled(enabled: Boolean) {
        secureStorage.putInt(SecureStorage.KEY_CHAT_MODE_PRO_ENABLED, if (enabled) 1 else 0)
    }

    fun getDefaultImageModelId(): String =
        secureStorage.getString(SecureStorage.KEY_DEFAULT_IMAGE_MODEL_ID)?.takeIf { it.isNotBlank() } ?: DEFAULT_IMAGE_MODEL_ID

    fun setDefaultImageModelId(modelId: String) {
        secureStorage.putString(SecureStorage.KEY_DEFAULT_IMAGE_MODEL_ID, modelId.trim())
    }

    /** The genuinely different second model GenerateImageUseCase retries with on failure — kept
     * distinct from [getDefaultImageModelId] (the primary) so the retry has a real chance of
     * succeeding when the primary model itself is down, rather than retrying the same model twice.
     * Server-enforced as one of two allowed image models (BUG-099, see BUGS.md). */
    fun getDefaultImageFallbackModelId(): String =
        secureStorage.getString(SecureStorage.KEY_DEFAULT_IMAGE_FALLBACK_MODEL_ID)?.takeIf { it.isNotBlank() } ?: getDefaultImageModelId()

    fun setDefaultImageFallbackModelId(modelId: String) {
        secureStorage.putString(SecureStorage.KEY_DEFAULT_IMAGE_FALLBACK_MODEL_ID, modelId.trim())
    }

    /** Creative temperature (FEATURES.md section 9), 0.0–2.0. */
    fun getDefaultTemperature(): Double =
        secureStorage.getInt(SecureStorage.KEY_DEFAULT_TEMPERATURE_X100, (DEFAULT_TEMPERATURE * 100).toInt()) / 100.0

    fun setDefaultTemperature(temperature: Double) {
        secureStorage.putInt(SecureStorage.KEY_DEFAULT_TEMPERATURE_X100, (temperature * 100).toInt())
    }

    fun applyBackendModelConfig(config: com.kitsune.core.backend.model.ModelConfigResponse) {
        setDefaultChatModelId(config.chat)
        setDefaultChatProModelId(config.chatPro)
        setDefaultImageModelId(config.imageGeneration)
        setDefaultImageFallbackModelId(config.imageGenerationFallback)
        setModelForOperation(LlmOperation.SUMMARY, config.summary)
        setModelForOperation(LlmOperation.LORE, config.lore)
        setModelForOperation(LlmOperation.QUICK_GENERATION, config.quickGeneration)
        setModelForOperation(LlmOperation.VISUAL_SHEET, config.visualSheet)
        setModelForOperation(LlmOperation.IMAGE_DESCRIPTION, config.imageDescription)
        setModelForOperation(LlmOperation.EMBEDDING, config.embedding)
        setModelForOperation(LlmOperation.TRANSLATION, config.translation)
        setModelForOperation(LlmOperation.INSPIRATION, config.inspiration)
        // Memory pipeline sizes (maxContextTokens, rawWindowSize(Pro), loreEntries(Pro)) are
        // applied straight into MemorySettingsHolder by KitsuneApp.kt, not persisted here — no
        // reader ever consumed a locally-persisted copy of them.
    }

    companion object {
        // DEFAULT_BASE_URL (https://api.mammouth.ai/) was removed with the provider migration: it had
        // no readers left, and the app no longer talks to any provider directly — every AI call and
        // the model catalog itself go through our backend, which now proxies to OpenRouter.

        /**
         * Fallbacks used only until `/config/models` has been fetched once, so they must be models
         * that actually resolve. These are OpenRouter's own `vendor/slug` ids — the previous
         * `provider:model` form (and the bare Mammouth-era ids before that) no longer resolve
         * anywhere.
         *
         * Kept in step with `application.conf`'s `kitsune.models` defaults on the backend.
         */
        // Dated snapshot ids are exactly that — a snapshot. OpenRouter retired `-0731` (confirmed
        // live 2026-08-18: it now 400s with "does not exist"), so this stays on the undated alias,
        // matching the backend's own default (KitsuneBackend's application.conf).
        const val DEFAULT_CHAT_MODEL_ID = "deepseek/deepseek-v4-flash"
        const val DEFAULT_CHAT_PRO_MODEL_ID = "deepseek/deepseek-v4-pro-0813"
        /** An actual image-*generating* model, served by OpenRouter's dedicated Images API (see the
         * backend's `OpenRouterImageGenerator`) — a plain chat model routed there would simply fail
         * rather than answer with a text description and no image. */
        const val DEFAULT_IMAGE_MODEL_ID = "google/gemini-2.5-flash-image"
        /** `openai/text-embedding-3-small` specifically: it's the model the app's stored 1536-dim
         * vectors were generated with (semantic memory, see `core:memory/semantic`) — switching it
         * would invalidate every existing embedding, silently making retrieval return nonsense
         * rather than fail loudly. Kept in step with the backend's own default (same file/comment as
         * [DEFAULT_CHAT_MODEL_ID]'s reference). */
        const val DEFAULT_EMBEDDING_MODEL_ID = "openai/text-embedding-3-small"
        const val DEFAULT_TEMPERATURE = 0.9
    }
}
