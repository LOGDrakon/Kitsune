package com.kitsune.core.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.network.catalog.ModelCatalogRepository
import com.kitsune.core.network.catalog.ModelInfo
import com.kitsune.core.network.catalog.rank
import com.kitsune.core.network.catalog.withMaxCostPerMillionTokens
import com.kitsune.core.network.catalog.withMinContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelCatalogViewModel @Inject constructor(
    private val modelCatalogRepository: ModelCatalogRepository
) : ViewModel() {

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _sortOption = MutableStateFlow(ModelSortOption.QUALITY)
    private val _contextFilter = MutableStateFlow(ModelContextFilter.ANY)
    private val _costFilter = MutableStateFlow(ModelCostFilter.ANY)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val sortOption: StateFlow<ModelSortOption> = _sortOption.asStateFlow()
    val contextFilter: StateFlow<ModelContextFilter> = _contextFilter.asStateFlow()
    val costFilter: StateFlow<ModelCostFilter> = _costFilter.asStateFlow()

    /** Both filters apply simultaneously (combined filtering), on top of the chosen sort. */
    val displayedModels: StateFlow<List<ModelInfo>> = combine(
        _models,
        _sortOption,
        _contextFilter,
        _costFilter
    ) { models, sort, contextFilter, costFilter ->
        var filtered = models
        contextFilter.minTokens?.let { filtered = filtered.withMinContext(it) }
        costFilter.maxCostPerMillionTokens?.let { filtered = filtered.withMaxCostPerMillionTokens(it) }
        sort(filtered, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            modelCatalogRepository.getModels(forceRefresh)
                .onSuccess {
                    _models.value = it
                    _error.value = null
                }
                .onFailure { _error.value = it.message ?: "" }
            _isLoading.value = false
        }
    }

    fun setSortOption(option: ModelSortOption) {
        _sortOption.value = option
    }

    fun setContextFilter(filter: ModelContextFilter) {
        _contextFilter.value = filter
    }

    fun setCostFilter(filter: ModelCostFilter) {
        _costFilter.value = filter
    }

    private fun sort(models: List<ModelInfo>, option: ModelSortOption): List<ModelInfo> = when (option) {
        ModelSortOption.CONTEXT_SIZE -> models.sortedByDescending { it.contextWindowTokens ?: 0 }
        ModelSortOption.COST -> models.sortedBy { totalCostOrMax(it) }
        ModelSortOption.QUALITY -> models.sortedWith(compareByDescending<ModelInfo> { it.qualityTier.rank }.thenBy { totalCostOrMax(it) })
        ModelSortOption.SPEED -> models.sortedWith(compareByDescending<ModelInfo> { it.speedTier.rank }.thenBy { totalCostOrMax(it) })
    }

    private fun totalCostOrMax(model: ModelInfo): Double =
        if (model.hasPricing) model.inputCostPerMillionTokens!! + model.outputCostPerMillionTokens!! else Double.MAX_VALUE
}
