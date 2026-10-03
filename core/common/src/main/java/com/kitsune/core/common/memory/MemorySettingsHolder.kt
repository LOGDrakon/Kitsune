package com.kitsune.core.common.memory

/** Admin-configured, direct (non-tiered) memory pipeline sizes — pushed from the backend
 * (`GET /config/models`) and applied once at app startup (`KitsuneApp.kt`). See
 * `SummarizationConfig` for how these are consumed. */
object MemorySettingsHolder {
    @Volatile var maxContextTokens: Int? = null
    @Volatile var rawWindowSize: Int = 40
    @Volatile var rawWindowSizePro: Int = 60
    @Volatile var loreEntries: Int = 12
    @Volatile var loreEntriesPro: Int = 20

    /**
     * Whether the app may send `top_p` / `frequency_penalty` / `presence_penalty` (2026-08-23).
     *
     * Lives here rather than in `NetworkPreferences` for the same reason the memory budgets do: it is
     * an admin-pushed operational value, not a user preference, and it has to be readable from the
     * completion path without a DI hop. Turning it off falls every chat back to temperature alone —
     * exactly how the app behaved before the feature shipped.
     */
    @Volatile var samplingEnabled: Boolean = true

    fun apply(
        maxContextTokens: Int?,
        rawWindowSize: Int,
        rawWindowSizePro: Int,
        loreEntries: Int,
        loreEntriesPro: Int,
        samplingEnabled: Boolean
    ) {
        this.maxContextTokens = maxContextTokens
        this.rawWindowSize = rawWindowSize
        this.rawWindowSizePro = rawWindowSizePro
        this.loreEntries = loreEntries
        this.loreEntriesPro = loreEntriesPro
        this.samplingEnabled = samplingEnabled
    }
}
