package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateTransferResponse(
    val transferId: String,
    val uploadToken: String,
    val pullToken: String,
    val expiresAt: String
)

@Serializable
data class UploadTransferRequest(val blobBase64: String)

@Serializable
data class DownloadTransferResponse(val ready: Boolean, val blobBase64: String? = null)

@Serializable
data class ClaimTransferResponse(val accessToken: String, val refreshToken: String, val userId: String)

@Serializable
data class TransferStatusResponse(val status: String)
