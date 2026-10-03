package com.kitsune.core.security.profile

/** Intradiegetic user profile (FEATURES.md section 7) — injected into the roleplay system prompt. */
data class UserProfile(
    val firstName: String = "",
    val lastName: String = "",
    val pronoun: String = "",
    val age: String = "",
    val physicalDescription: String = "",
    val sexualOrientation: String = ""
) {
    val isBlank: Boolean get() = firstName.isBlank() && lastName.isBlank() && pronoun.isBlank() && sexualOrientation.isBlank()
}
