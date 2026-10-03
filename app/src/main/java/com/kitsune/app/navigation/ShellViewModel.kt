package com.kitsune.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.network.provider.ProviderStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The shell's own small amount of state: whether an AI provider still has to be configured. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    providerStore: ProviderStore
) : ViewModel() {

    val needsProvider: StateFlow<Boolean> = providerStore.providers
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, providerStore.all().isEmpty())
}
