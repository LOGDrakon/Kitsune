package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

/** Wire format for a hybrid RSA/AES-encrypted bug report — see `BugReportCrypto` (core:security).
 * Only the backend's RSA private key can ever decrypt this. */
@Serializable
data class SubmitBugReportRequest(
    val encryptedKey: String,
    val iv: String,
    val ciphertext: String
)

@Serializable
data class SubmitBugReportResponse(val id: String)
