package com.kitsune.core.network.repository

interface ChatCompletionRepository {
    /**
     * Runs a chat completion against [modelId], automatically continuing the request when the
     * model stops on a token-length cutoff, and automatically falling back to [fallbackModelId]
     * if [modelId] itself fails. If [fallbackModelId] is null, falls back to the most economical
     * chat-capable model from the catalog.
     *
     * [operationType] labels the call in debug logs only.
     *
     * [maxTokens] overrides the default completion token limit. Use lower values for
     * structured JSON generation to save cost and speed up responses.
     *
     * [allowContinuation] controls whether truncated responses (finishReason=length) trigger
     * automatic continuation requests. Disable for structured JSON generation where the
     * salvage parser can handle truncation.
     *
     * [modelId] is a [com.kitsune.core.network.provider.ModelRef]: it names the provider as well as
     * the model. Image generation does not go through here — see [ImageGenerationRepository].
     */
    suspend fun complete(
        modelId: String,
        systemPrompt: String,
        messages: List<ChatTurn>,
        temperature: Double = 0.9,
        /**
         * Decoder controls beyond [temperature] (2026-08-23). Defaults to [SamplingProfile.INHERIT],
         * which sends nothing and reproduces the exact request every caller made before this
         * parameter existed.
         *
         * Replaces a `seed: Int?` parameter that this interface declared and the implementation
         * silently discarded — it reached neither the request DTO nor the wire, so a caller passing
         * one got no error and no effect.
         */
        sampling: SamplingProfile = SamplingProfile.INHERIT,
        fallbackModelId: String? = null,
        operationType: String = "CHAT",
        maxTokens: Int = DEFAULT_MAX_TOKENS,
        allowContinuation: Boolean = true
    ): Result<ChatCompletionResult>

    companion object {
        const val DEFAULT_MAX_TOKENS = 4096

        /**
         * Output ceiling for a Pro-mode chat turn. Pro's craft directives ask for a longer, more
         * developed reply, but until now both modes shared [DEFAULT_MAX_TOKENS] — the instruction
         * had literally no extra room to execute in, which is a large part of why Pro replies did
         * not read as noticeably different.
         *
         * Raising it is close to cost-neutral rather than a straight increase: this path runs with
         * `allowContinuation = true`, so a reply that hits the ceiling already triggers a
         * continuation request that **resends the entire prompt**. A ceiling the reply fits under
         * trades those duplicated prompt tokens for the completion tokens it was going to spend
         * anyway.
         */
        const val PRO_MAX_TOKENS = 6144
        /**
         * Persona/NPC/faction/location — single JSON object. For personas this now also carries
         * the 4-field visual sheet in the same call (10 fields total), so it needs as much room as
         * a normal chat reply — a tighter cap here just means small models truncate mid-object and
         * later fields (scenario, exampleDialogues, the visual sheet) come back empty.
         */
        const val GENERATION_MAX_TOKENS = 4096
        /** Universe bundle — universe + 2 factions + 2 locations + 3 NPCs in one JSON */
        const val BUNDLE_MAX_TOKENS = 4096
        /** Memory operations — lore, inspiration, reply suggestions etc. */
        const val MEMORY_MAX_TOKENS = 1024
        /**
         * Rolling summary fold-in (UpdateChatSummaryUseCase). Same lesson as [GENERATION_MAX_TOKENS]:
         * a tight output cap here doesn't make the model summarize more concisely, it just truncates
         * mid-thought and drops whatever didn't fit — which is indistinguishable from "the prompt is
         * bad" unless you look at the token budget. [MEMORY_MAX_TOKENS] (1024, ~4000 chars) was
         * actually *smaller* than META_SUMMARY_TRIGGER_CHARS (5000, SummarizationConfig) — the
         * summary was structurally prevented from ever growing to the size that triggers compaction,
         * because every single fold-in call clipped it back down first. This must stay comfortably
         * above that 5000-char trigger so the summary can actually reach it before compaction (a
         * separate, deliberately tighter call) trims it.
         */
        const val SUMMARY_MAX_TOKENS = 2048
    }
}
