package com.kitsune.core.moderation.model

sealed interface ModerationResult {
    data object Clean : ModerationResult
    data class Flagged(val category: ModerationCategory, val reason: String) : ModerationResult
}
