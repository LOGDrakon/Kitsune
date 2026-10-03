package com.kitsune.feature.settings.proposals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.model.ProposalResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VoteType { UP, DOWN }

/**
 * Community "suggest an improvement" board — backed by the real, shared, multi-user `/proposals`
 * API (previously an in-memory, single-device mock that admins could never see or moderate).
 */
@HiltViewModel
class ProposalsViewModel @Inject constructor(
    private val backendClient: KitsuneBackendClient
) : ViewModel() {

    private val _proposals = MutableStateFlow<List<ProposalResponse>>(emptyList())
    val proposals: StateFlow<List<ProposalResponse>> = _proposals.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadProposals()
    }

    fun loadProposals() {
        viewModelScope.launch {
            backendClient.getProposals()
                .onSuccess { _proposals.value = it.sortedByDescending { p -> p.upVotes - p.downVotes } }
                .onFailure { _error.value = it.message }
        }
    }

    fun createProposal(title: String, description: String) {
        viewModelScope.launch {
            backendClient.createProposal(title, description)
                .onSuccess { loadProposals() }
                .onFailure { _error.value = it.message }
        }
    }

    fun voteOnProposal(proposalId: String, voteType: VoteType) {
        viewModelScope.launch {
            backendClient.voteProposal(proposalId, voteType.name)
                .onSuccess { loadProposals() }
                .onFailure { _error.value = it.message }
        }
    }

    fun dismissError() { _error.value = null }
}
