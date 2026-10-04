package com.kitsune.feature.settings.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.network.catalog.ModelCatalogRepository
import com.kitsune.core.network.catalog.ModelInfo
import com.kitsune.core.network.preferences.LlmOperation
import com.kitsune.core.network.preferences.NetworkPreferences
import com.kitsune.core.network.provider.ModelRef
import com.kitsune.core.network.provider.ProviderStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A model slot as shown on the Models screen. [ref] is empty when nothing applies (no provider, or
 * an embedding/image slot with no model chosen and no suggestion). */
data class ModelSlot(
    val operation: LlmOperation?,
    val ref: String,
    /** True when the slot inherits a default instead of having its own selection. */
    val inherited: Boolean
)

/** The image fallback model is a slot of its own, outside [LlmOperation]. */
const val IMAGE_FALLBACK_SLOT = "image_fallback"

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val networkPreferences: NetworkPreferences,
    private val modelCatalogRepository: ModelCatalogRepository,
    private val providerStore: ProviderStore,
    private val llmModelResolver: com.kitsune.core.network.preferences.LlmModelResolver
) : ViewModel() {

    private val _automatic = MutableStateFlow<Map<String, String>>(emptyMap())
    /** For slots with nothing chosen: the model the app will actually use, by slot key. */
    val automatic: StateFlow<Map<String, String>> = _automatic.asStateFlow()

    private fun refreshAutomatic() {
        viewModelScope.launch {
            _automatic.value = LlmOperation.entries
                .filter { _slots.value[it.name]?.ref.isNullOrBlank() || _slots.value[it.name]?.inherited == true }
                .associate { it.name to llmModelResolver.resolve(it) }
                .filterValues { it.isNotBlank() }
        }
    }

    private val _slots = MutableStateFlow(loadSlots())
    val slots: StateFlow<Map<String, ModelSlot>> = _slots.asStateFlow()

    private val _catalog = MutableStateFlow<Map<String, ModelInfo>>(emptyMap())
    /** Catalog by ModelRef, to show display names instead of raw ids. */
    val catalog: StateFlow<Map<String, ModelInfo>> = _catalog.asStateFlow()

    val hasProvider: Boolean get() = providerStore.isConfigured()

    init {
        viewModelScope.launch {
            modelCatalogRepository.getModels().onSuccess { models -> _catalog.value = models.associateBy { it.id } }
            refreshAutomatic()
        }
    }

    fun select(slotKey: String, ref: String) {
        if (slotKey == IMAGE_FALLBACK_SLOT) {
            networkPreferences.setDefaultImageFallbackModelId(ref)
        } else {
            networkPreferences.setModelForOperation(LlmOperation.valueOf(slotKey), ref)
        }
        _slots.value = loadSlots()
        refreshAutomatic()
    }

    fun reset(slotKey: String) {
        if (slotKey == IMAGE_FALLBACK_SLOT) {
            networkPreferences.setDefaultImageFallbackModelId("")
        } else {
            networkPreferences.clearModelForOperation(LlmOperation.valueOf(slotKey))
        }
        _slots.value = loadSlots()
        refreshAutomatic()
    }

    /** "provider · model" for a ref, falling back to the bare id when the catalog has not loaded. */
    fun label(ref: String): String {
        if (ref.isBlank()) return ""
        _catalog.value[ref]?.let { return "${it.displayName} · ${it.provider}" }
        val (providerId, modelId) = ModelRef.parse(ref)
        val providerName = providerStore.find(providerId)?.name
        return if (providerName != null) "$modelId · $providerName" else modelId
    }

    private fun loadSlots(): Map<String, ModelSlot> {
        val map = LlmOperation.entries.associate { op ->
            op.name to ModelSlot(
                operation = op,
                ref = networkPreferences.getModelForOperation(op),
                inherited = !networkPreferences.hasOwnModel(op)
            )
        }.toMutableMap()
        val fallback = networkPreferences.getDefaultImageFallbackModelId()
        map[IMAGE_FALLBACK_SLOT] = ModelSlot(operation = null, ref = fallback.orEmpty(), inherited = fallback == null)
        return map
    }
}
