package com.kitsune.core.security.model

/**
 * Kitsune supports two independent app PINs: the real one, and an optional
 * panic PIN that unlocks a decoy state instead (see FEATURES.md, section 2).
 * Both are stored and verified identically; only the caller's interpretation differs.
 */
enum class PinSlot {
    REAL,
    PANIC
}

sealed interface PinVerificationResult {
    class Match(val slot: PinSlot, val keyMaterial: ByteArray) : PinVerificationResult
    data object NoMatch : PinVerificationResult
}
