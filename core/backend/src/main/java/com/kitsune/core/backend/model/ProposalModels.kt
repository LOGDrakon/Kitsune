package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class ProposalResponse(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val upVotes: Int,
    val downVotes: Int,
    val myVote: String? = null,
    val createdAt: String
)

@Serializable
data class CreateProposalRequest(val title: String, val description: String)

@Serializable
data class VoteProposalRequest(val voteType: String)

@Serializable
data class ProposalIdResponse(val id: String)
