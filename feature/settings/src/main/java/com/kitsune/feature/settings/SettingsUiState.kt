package com.kitsune.feature.settings

import com.kitsune.core.security.locale.AppLanguage

data class SettingsUiState(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val creditBalance: Int = 0,
    val backendUserId: String = "",
    val username: String? = null,
    val autoLockMinutes: Int = 1,
    val flagSecureEnabled: Boolean = true,
    val discreetModeEnabled: Boolean = false,
    val temperature: Float = 0.9f,
    val safeWord: String = "",
    val userFirstName: String = "",
    val userLastName: String = "",
    val userPronoun: String = "",
    val userAge: String = "",
    val userPhysicalDescription: String = "",
    val userSexualOrientation: String = "",
    val autoRecapEnabled: Boolean = true,
    val customStylePrompt: String = "",
    /** Story taste (2026-08-23) — free text the AI is told never to write, in any conversation. */
    val neverWrite: String = ""
)