package com.kitsune.core.security.taste

import com.kitsune.core.security.storage.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this player likes to read (2026-08-23).
 *
 * ## Why it is separate from `UserProfile`
 *
 * `UserProfile` answers *who the user is* — name, pronoun, age, appearance, orientation — so that
 * characters can address them correctly. It says nothing about what they enjoy, and until now nothing
 * else in the app did either: the whole product knew the player's body and not one thing about the
 * kind of story they came for. Every taste signal lived on a single `ChatEntity`, so it had to be
 * re-chosen from scratch for every new conversation and was in practice never chosen at all.
 *
 * ## Privacy
 *
 * Same posture as [com.kitsune.core.security.profile.UserProfileStore]: kept in the encrypted
 * preference store, never sent to Kitsune's servers, but [neverWrite] *is* sent to the model with
 * every message — it has to be, that is what makes it binding. The settings copy has to say so.
 */
data class StoryTaste(
    /** Default story-card preset id for new conversations, e.g. `slow_romance`. Empty until chosen. */
    val defaultPresetId: String = "",
    /**
     * Free text the model is told never to write, in any story, whatever a mode may suggest.
     *
     * Free-form rather than a checklist on purpose: what a reader will not tolerate is specific and
     * personal ("no animal harm", "never write my character crying"), and a fixed list would be both
     * too long and never quite right. It rides in the style contract every turn, which means it
     * carries the same moderation exposure as any user text — see the vocabulary warning in
     * `ChatStyleContract`.
     */
    val neverWrite: String = ""
) {
    val isBlank: Boolean get() = defaultPresetId.isBlank() && neverWrite.isBlank()
}

@Singleton
class StoryTasteStore @Inject constructor(private val secureStorage: SecureStorage) {

    fun get(): StoryTaste = StoryTaste(
        defaultPresetId = secureStorage.getString(SecureStorage.KEY_TASTE_DEFAULT_PRESET).orEmpty(),
        neverWrite = secureStorage.getString(SecureStorage.KEY_TASTE_NEVER_WRITE).orEmpty()
    )

    fun save(taste: StoryTaste) {
        secureStorage.putString(SecureStorage.KEY_TASTE_DEFAULT_PRESET, taste.defaultPresetId)
        secureStorage.putString(SecureStorage.KEY_TASTE_NEVER_WRITE, taste.neverWrite)
    }
}
