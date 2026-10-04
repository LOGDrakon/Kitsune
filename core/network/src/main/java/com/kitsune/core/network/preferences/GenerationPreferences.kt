package com.kitsune.core.network.preferences

import com.kitsune.core.common.memory.MemorySettingsHolder
import com.kitsune.core.security.storage.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How much the story remembers and how long a reply may run — what used to be split between a
 * "Standard" and a paid "Pro" mode. Every user now chooses for themselves, because only they know
 * their model's context window and what their provider charges.
 *
 * Two levels, like the rest of the settings: a preset ([MemoryDepth]) for anyone who just wants it to
 * work, and the raw numbers behind it ([MemoryDepth.CUSTOM]) for anyone who wants to tune them.
 */
enum class MemoryDepth(val rawWindow: Int, val loreEntries: Int) {
    /** Small or local models (8–16k tokens of context), or keeping each turn cheap. */
    LIGHT(rawWindow = 24, loreEntries = 8),
    /** The default: fits any model with 32k tokens of context. */
    BALANCED(rawWindow = 40, loreEntries = 12),
    /** Large-context models; the former "Pro" values. */
    EXTENDED(rawWindow = 60, loreEntries = 20),
    /** The user's own numbers. */
    CUSTOM(rawWindow = 40, loreEntries = 12)
}

@Singleton
class GenerationPreferences @Inject constructor(
    private val secureStorage: SecureStorage
) {
    init {
        applyToMemoryHolder()
    }

    fun memoryDepth(): MemoryDepth =
        secureStorage.getString(SecureStorage.KEY_MEMORY_DEPTH)
            ?.let { stored -> MemoryDepth.entries.firstOrNull { it.name == stored } }
            ?: MemoryDepth.BALANCED

    fun setMemoryDepth(depth: MemoryDepth) {
        // Entering CUSTOM starts from the preset that was active, so the sliders open on familiar
        // values instead of jumping.
        if (depth == MemoryDepth.CUSTOM && memoryDepth() != MemoryDepth.CUSTOM) {
            val from = memoryDepth()
            secureStorage.putInt(SecureStorage.KEY_MEMORY_RAW_WINDOW, from.rawWindow)
            secureStorage.putInt(SecureStorage.KEY_MEMORY_LORE_ENTRIES, from.loreEntries)
        }
        secureStorage.putString(SecureStorage.KEY_MEMORY_DEPTH, depth.name)
        applyToMemoryHolder()
    }

    /** Messages sent verbatim each turn. */
    fun rawWindow(): Int = when (val depth = memoryDepth()) {
        MemoryDepth.CUSTOM -> secureStorage.getInt(SecureStorage.KEY_MEMORY_RAW_WINDOW, depth.rawWindow)
            .coerceIn(RAW_WINDOW_RANGE)
        else -> depth.rawWindow
    }

    fun setRawWindow(value: Int) {
        secureStorage.putInt(SecureStorage.KEY_MEMORY_RAW_WINDOW, value.coerceIn(RAW_WINDOW_RANGE))
        secureStorage.putString(SecureStorage.KEY_MEMORY_DEPTH, MemoryDepth.CUSTOM.name)
        applyToMemoryHolder()
    }

    /** Lore sheets carried in the prompt. */
    fun loreEntries(): Int = when (val depth = memoryDepth()) {
        MemoryDepth.CUSTOM -> secureStorage.getInt(SecureStorage.KEY_MEMORY_LORE_ENTRIES, depth.loreEntries)
            .coerceIn(LORE_ENTRIES_RANGE)
        else -> depth.loreEntries
    }

    fun setLoreEntries(value: Int) {
        secureStorage.putInt(SecureStorage.KEY_MEMORY_LORE_ENTRIES, value.coerceIn(LORE_ENTRIES_RANGE))
        secureStorage.putString(SecureStorage.KEY_MEMORY_DEPTH, MemoryDepth.CUSTOM.name)
        applyToMemoryHolder()
    }

    /** Hard ceiling on a reply's tokens when the chat asks for no particular length. */
    fun maxReplyTokens(): Int =
        secureStorage.getInt(SecureStorage.KEY_MAX_REPLY_TOKENS, DEFAULT_MAX_REPLY_TOKENS).coerceIn(MAX_REPLY_TOKENS_RANGE)

    fun setMaxReplyTokens(value: Int) {
        secureStorage.putInt(SecureStorage.KEY_MAX_REPLY_TOKENS, value.coerceIn(MAX_REPLY_TOKENS_RANGE))
    }

    /** The demanding craft rules (concrete opening, no stock phrases, end on a turn) that used to be
     * reserved to Pro. Off by default: they make replies longer and some models over-apply them. */
    fun isEnhancedCraftEnabled(): Boolean = secureStorage.getInt(SecureStorage.KEY_ENHANCED_CRAFT, 0) == 1

    fun setEnhancedCraftEnabled(enabled: Boolean) {
        secureStorage.putInt(SecureStorage.KEY_ENHANCED_CRAFT, if (enabled) 1 else 0)
    }

    private fun applyToMemoryHolder() {
        val lore = loreEntries()
        MemorySettingsHolder.apply(
            rawWindowSize = rawWindow(),
            loreEntries = lore,
            detailedExtraction = lore >= DETAILED_EXTRACTION_FROM
        )
    }

    companion object {
        val RAW_WINDOW_RANGE = 10..120
        val LORE_ENTRIES_RANGE = 4..40
        val MAX_REPLY_TOKENS_RANGE = 512..16_384
        const val DEFAULT_MAX_REPLY_TOKENS = 4096
        /** The reply ceilings offered in the UI. */
        val MAX_REPLY_TOKENS_CHOICES = listOf(2048, 4096, 6144, 8192)
        private const val DETAILED_EXTRACTION_FROM = 16
    }
}
