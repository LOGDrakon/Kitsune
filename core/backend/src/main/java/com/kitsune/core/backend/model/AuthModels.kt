package com.kitsune.core.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val isNewUser: Boolean
)

@Serializable
data class RegisterRequest(val deviceId: String? = null)

@Serializable
data class GoogleLoginRequest(val googleIdToken: String, val displayName: String? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class ErrorResponse(val error: String)
