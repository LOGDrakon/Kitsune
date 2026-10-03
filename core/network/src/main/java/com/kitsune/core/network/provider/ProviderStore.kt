package com.kitsune.core.network.provider

import com.kitsune.core.security.storage.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's providers, persisted as one JSON document in [SecureStorage] (encrypted at rest with a
 * Keystore-backed key — the API keys are the most sensitive thing this app stores after the vault
 * itself).
 *
 * The first provider in the list is the **default**: a model selection without a provider prefix
 * (see [ModelRef]) resolves against it.
 */
@Singleton
class ProviderStore @Inject constructor(private val secureStorage: SecureStorage) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(ProviderConfig.serializer())

    private val _providers = MutableStateFlow(load())
    val providers: StateFlow<List<ProviderConfig>> = _providers.asStateFlow()

    fun all(): List<ProviderConfig> = _providers.value

    fun isConfigured(): Boolean = _providers.value.isNotEmpty()

    fun default(): ProviderConfig? = _providers.value.firstOrNull()

    fun find(id: String?): ProviderConfig? =
        if (id == null) default() else _providers.value.firstOrNull { it.id == id } ?: default()

    /** Resolves a stored model selection to the provider that must serve it and the bare model id. */
    fun resolve(ref: String): Pair<ProviderConfig, String>? {
        val (providerId, modelId) = ModelRef.parse(ref)
        val provider = find(providerId) ?: return null
        return provider to modelId
    }

    fun newConfig(preset: ProviderPreset, baseUrl: String = preset.defaultBaseUrl, apiKey: String = ""): ProviderConfig =
        ProviderConfig(
            id = UUID.randomUUID().toString().take(8),
            preset = preset,
            name = preset.displayName,
            baseUrl = baseUrl,
            apiKey = apiKey.trim()
        )

    /** Adds [config], or replaces the provider with the same id. */
    fun upsert(config: ProviderConfig) {
        val current = _providers.value
        val updated = if (current.any { it.id == config.id }) {
            current.map { if (it.id == config.id) config else it }
        } else {
            current + config
        }
        save(updated)
    }

    fun remove(id: String) = save(_providers.value.filterNot { it.id == id })

    /** Moves [id] to the front, making it the default provider. */
    fun makeDefault(id: String) {
        val target = _providers.value.firstOrNull { it.id == id } ?: return
        save(listOf(target) + _providers.value.filterNot { it.id == id })
    }

    private fun load(): List<ProviderConfig> =
        secureStorage.getString(KEY_PROVIDERS)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private fun save(list: List<ProviderConfig>) {
        secureStorage.putString(KEY_PROVIDERS, json.encodeToString(serializer, list))
        _providers.value = list
    }

    private companion object {
        const val KEY_PROVIDERS = "ai_providers_v1"
    }
}
