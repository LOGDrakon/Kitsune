package com.kitsune.feature.settings.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.network.catalog.ModelCatalogRepository
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.preferences.NetworkPreferences
import com.kitsune.core.network.provider.LlmHttpClient
import com.kitsune.core.network.provider.ModelRef
import com.kitsune.core.network.provider.OpenRouterEndpoint
import com.kitsune.core.network.provider.OpenRouterRouting
import com.kitsune.core.network.provider.ProviderConfig
import com.kitsune.core.network.provider.ProviderPreset
import com.kitsune.core.network.provider.ProviderStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionTest {
    data object Running : ConnectionTest
    data class Success(val modelCount: Int) : ConnectionTest
    data class Failure(val message: String) : ConnectionTest
}

sealed interface EndpointsState {
    data object Idle : EndpointsState
    data object Loading : EndpointsState
    data class Loaded(val modelId: String, val endpoints: List<OpenRouterEndpoint>) : EndpointsState
    data class Failure(val message: String) : EndpointsState
}

/**
 * The user's AI providers: add, edit (key, URL, OpenRouter routing), test, remove, choose the
 * default. Edits happen on a [draft] and are written to [ProviderStore] only on [save], so a
 * half-typed key never replaces a working one.
 */
@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val providerStore: ProviderStore,
    private val httpClient: LlmHttpClient,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val networkPreferences: NetworkPreferences
) : ViewModel() {

    val providers: StateFlow<List<ProviderConfig>> = providerStore.providers

    private val _draft = MutableStateFlow<ProviderConfig?>(null)
    /** The provider being added or edited, or null on the list. */
    val draft: StateFlow<ProviderConfig?> = _draft.asStateFlow()

    private val _isNew = MutableStateFlow(false)
    val isNew: StateFlow<Boolean> = _isNew.asStateFlow()

    private val _test = MutableStateFlow<ConnectionTest?>(null)
    val test: StateFlow<ConnectionTest?> = _test.asStateFlow()

    private val _endpoints = MutableStateFlow<EndpointsState>(EndpointsState.Idle)
    val endpoints: StateFlow<EndpointsState> = _endpoints.asStateFlow()

    fun startAdd(preset: ProviderPreset) {
        _draft.value = providerStore.newConfig(preset)
        _isNew.value = true
        _test.value = null
        _endpoints.value = EndpointsState.Idle
    }

    fun startEdit(id: String) {
        _draft.value = providerStore.all().firstOrNull { it.id == id } ?: return
        _isNew.value = false
        _test.value = null
        _endpoints.value = EndpointsState.Idle
    }

    fun closeEditor() {
        _draft.value = null
        _test.value = null
    }

    fun updateDraft(transform: (ProviderConfig) -> ProviderConfig) {
        _draft.value = _draft.value?.let(transform)
        _test.value = null
    }

    fun updateRouting(transform: (OpenRouterRouting) -> OpenRouterRouting) {
        _draft.value = _draft.value?.let { it.copy(routing = transform(it.routing)) }
    }

    fun testConnection() {
        val config = _draft.value ?: return
        _test.value = ConnectionTest.Running
        viewModelScope.launch {
            _test.value = httpClient.testConnection(config).fold(
                onSuccess = { ConnectionTest.Success(it) },
                onFailure = { ConnectionTest.Failure(NetworkErrorMessages.forUser(it)) }
            )
        }
    }

    /** Saves the draft. The first provider ever added becomes the default and, when its preset
     * suggests models, gets them pre-selected so chatting works right away. */
    fun save() {
        val config = _draft.value ?: return
        val firstProvider = providerStore.all().isEmpty()
        providerStore.upsert(config.copy(name = config.name.ifBlank { config.preset.displayName }))
        if (firstProvider) {
            config.preset.suggestedChatModel?.let {
                if (!networkPreferences.hasOwnModel(LlmOperation.CHAT)) {
                    networkPreferences.setModelForOperation(LlmOperation.CHAT, ModelRef.of(config.id, it))
                }
            }
        }
        viewModelScope.launch { modelCatalogRepository.getModels(forceRefresh = true) }
        closeEditor()
    }

    fun delete(id: String) {
        providerStore.remove(id)
        if (_draft.value?.id == id) closeEditor()
        viewModelScope.launch { modelCatalogRepository.getModels(forceRefresh = true) }
    }

    fun makeDefault(id: String) = providerStore.makeDefault(id)

    /** OpenRouter only: which upstream providers serve [modelId], with their quantization, speed and
     * uptime — so the user can see the effect of a routing restriction before applying it. */
    fun loadEndpoints(modelId: String) {
        val config = _draft.value ?: return
        val clean = ModelRef.modelIdOf(modelId).trim()
        if (clean.isBlank()) return
        _endpoints.value = EndpointsState.Loading
        viewModelScope.launch {
            _endpoints.value = runCatching { httpClient.listEndpoints(config, clean) }.fold(
                onSuccess = { EndpointsState.Loaded(clean, it) },
                onFailure = { EndpointsState.Failure(NetworkErrorMessages.forUser(it)) }
            )
        }
    }

    /** The chat model currently selected, when it belongs to the provider being edited — the
     * natural default for the endpoints lookup. */
    fun currentChatModelFor(providerId: String): String? {
        val (pid, model) = ModelRef.parse(networkPreferences.getModelForOperation(LlmOperation.CHAT))
        return model.takeIf { (pid ?: providerStore.default()?.id) == providerId && it.isNotBlank() }
    }
}
