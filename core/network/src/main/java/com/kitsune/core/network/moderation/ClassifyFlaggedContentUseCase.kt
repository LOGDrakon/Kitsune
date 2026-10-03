package com.kitsune.core.network.moderation

import android.util.Log
import com.kitsune.core.network.api.BackendChatApi
import com.kitsune.core.network.dto.ModerationClassifyRequest
import javax.inject.Inject

private const val TAG = "ClassifyFlaggedContentUC"
private const val MAX_TEXT_LENGTH = 4000

/**
 * Second opinion for content `LocalKeywordFilter` (`core:moderation`) flagged as a candidate —
 * never call this for `ModerationCategory.MINOR_CONTENT`, which stays a hard, non-appealable block.
 * The bare keyword/co-occurrence filter can't distinguish narrated dark fiction (a rejected advance,
 * a past assault told as backstory) from content that actually glorifies, encourages, or instructs
 * real-world harm, so this delegates that call to the server's classifier (`POST
 * /v1/moderation/classify`, backed by `ModerationService.classifyAmbiguousContent`) instead of
 * blocking unconditionally (see BUGS.md).
 *
 * Fails closed: any network/API error is treated as "uphold the block" — no worse than the filter's
 * previous unconditional-block behavior when the appeal itself can't be resolved.
 */
class ClassifyFlaggedContentUseCase @Inject constructor(
    private val api: BackendChatApi
) {
    suspend operator fun invoke(text: String, category: String): Boolean {
        return try {
            val response = api.classifyModeration(
                ModerationClassifyRequest(text = text.take(MAX_TEXT_LENGTH), category = category)
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "invoke: HTTP ${response.code()} — failing closed (uphold block)")
                return false
            }
            response.body()?.allowed ?: false
        } catch (e: Exception) {
            Log.w(TAG, "invoke: call failed, failing closed (uphold block): ${e.message}")
            false
        }
    }
}
