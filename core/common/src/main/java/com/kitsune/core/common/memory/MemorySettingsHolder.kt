package com.kitsune.core.common.memory

/** The memory pipeline sizes in force, read without a DI hop by `SummarizationConfig`.
 *
 * They are the user's choice (Settings → Mémoire et longueur), written here by
 * `GenerationPreferences` when it is created and every time the user changes them. The defaults below
 * are the "Équilibrée" preset, so code that runs before the preferences exist still gets sane values. */
object MemorySettingsHolder {
    /** Messages sent verbatim to the model each turn — also the window kept out of the summary. */
    @Volatile var rawWindowSize: Int = 40

    /** Lore sheets carried in the prompt roster. */
    @Volatile var loreEntries: Int = 12

    /** Asks the lore extraction pass for finer sub-beats and denser sheets. On with the larger
     * memory presets, where there is room in the prompt to use them. */
    @Volatile var detailedExtraction: Boolean = false

    /**
     * Whether the app may send `top_p` / `frequency_penalty` / `presence_penalty` (2026-08-23).
     * Turning it off falls every chat back to temperature alone. Not exposed in the UI: it stays as an
     * escape hatch for a provider that rejects those parameters.
     */
    @Volatile var samplingEnabled: Boolean = true

    fun apply(rawWindowSize: Int, loreEntries: Int, detailedExtraction: Boolean) {
        this.rawWindowSize = rawWindowSize
        this.loreEntries = loreEntries
        this.detailedExtraction = detailedExtraction
    }
}
