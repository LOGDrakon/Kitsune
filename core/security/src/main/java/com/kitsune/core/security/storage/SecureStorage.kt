package com.kitsune.core.security.storage

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_FILE_NAME = "kitsune_secure_prefs"

/**
 * At-rest storage for small secrets (PIN salts/hashes, the wrapped database key, auth
 * preferences). Protected by its own Keystore-backed master key managed by Jetpack Security —
 * this key does not require user presence, unlike [com.kitsune.core.security.keystore.KeystoreManager]'s
 * key, which is the biometric-gated second factor used specifically to unwrap the database key.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext context: Context
) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun putBytes(key: String, value: ByteArray) {
        prefs.edit().putString(key, Base64.encodeToString(value, Base64.NO_WRAP)).apply()
    }

    fun getBytes(key: String): ByteArray? =
        prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    fun contains(key: String): Boolean = prefs.contains(key)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /** Reads every key in [TRANSFERABLE_STRING_KEYS]/[TRANSFERABLE_INT_KEYS] that's actually set,
     * for bundling into an encrypted backup (see `ExportBackupUseCase`) — everything a user
     * would think of as "my settings" (profile, safe word, model/chat prefs, personalization),
     * deliberately excluding vault/PIN-internal keys (device-specific by design), age verification
     * (re-done per device on purpose), and the first-chat-mini-arc marker (handled separately by
     * the restore flow itself, not copied as-is). */
    fun exportTransferablePrefs(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        TRANSFERABLE_STRING_KEYS.forEach { key -> getString(key)?.let { result[key] = it } }
        TRANSFERABLE_INT_KEYS.forEach { key -> if (contains(key)) result[key] = getInt(key, 0).toString() }
        return result
    }

    /** Reverses [exportTransferablePrefs] on the restoring device — see `ImportBackupUseCase`. Keys
     * outside the transferable allow-lists are ignored rather than trusted blindly. */
    fun importTransferablePrefs(values: Map<String, String>) {
        values.forEach { (key, value) ->
            when (key) {
                in TRANSFERABLE_INT_KEYS -> value.toIntOrNull()?.let { putInt(key, it) }
                in TRANSFERABLE_STRING_KEYS -> putString(key, value)
            }
        }
    }

    companion object {
        const val KEY_WRAPPED_DB_KEY = "wrapped_db_key"
        /** Same random DB key as [KEY_WRAPPED_DB_KEY], stored directly (protected only by this
         * store's own non-biometric master key) for use in [com.kitsune.core.security.model.VaultSecurityMode.PIN_ONLY] —
         * never sufficient on its own to derive the passphrase, the PIN's key material is always
         * still required too. */
        const val KEY_DB_KEY_SOFTWARE = "db_key_software"
        /** Random key for the decoy notes database (see [com.kitsune.core.security.decoy.DecoyNotesKeyProvider])
         * — same "unwrapped, PIN-material-gated" pattern as [KEY_DB_KEY_SOFTWARE], but fully
         * disjoint: this key/passphrase must never overlap with anything the real vault uses. */
        const val KEY_DECOY_NOTES_DB_KEY = "decoy_notes_db_key"
        const val KEY_VAULT_SECURITY_MODE = "vault_security_mode"
        const val KEY_PIN_SALT_REAL = "pin_salt_real"
        const val KEY_PIN_HASH_REAL = "pin_hash_real"
        const val KEY_PIN_SALT_PANIC = "pin_salt_panic"
        const val KEY_PIN_HASH_PANIC = "pin_hash_panic"
        const val KEY_DISCREET_MODE_ENABLED = "discreet_mode_enabled"
        const val KEY_AUTO_LOCK_TIMEOUT_SECONDS = "auto_lock_timeout_seconds"
        const val KEY_AGE_VERIFIED_ADULT = "age_verified_adult"
        const val KEY_APP_LANGUAGE_SELECTED = "app_language_selected"
        const val KEY_FLAG_SECURE_ENABLED = "flag_secure_enabled"
        const val KEY_DEFAULT_CHAT_MODEL_ID = "default_chat_model_id"
        const val KEY_DEFAULT_IMAGE_MODEL_ID = "default_image_model_id"
        const val KEY_DEFAULT_IMAGE_FALLBACK_MODEL_ID = "default_image_fallback_model_id"
        const val KEY_DEFAULT_CHAT_PRO_MODEL_ID = "default_chat_pro_model_id"
        /** Stored as an integer (temperature × 100) since [SecureStorage] has no float accessor. */
        const val KEY_DEFAULT_TEMPERATURE_X100 = "default_temperature_x100"
        const val KEY_SAFE_WORD = "safe_word"
        const val KEY_USER_FIRST_NAME = "user_first_name"
        const val KEY_USER_LAST_NAME = "user_last_name"
        const val KEY_USER_PRONOUN = "user_pronoun"
        const val KEY_USER_AGE = "user_age"
        const val KEY_USER_PHYSICAL_DESCRIPTION = "user_physical_description"
        const val KEY_USER_SEXUAL_ORIENTATION = "user_sexual_orientation"
        /** Story taste (2026-08-23) — what the player likes to read, as opposed to who they are. */
        const val KEY_TASTE_DEFAULT_PRESET = "taste_default_preset"
        const val KEY_TASTE_NEVER_WRITE = "taste_never_write"
        const val KEY_AUTO_RECAP_ENABLED = "auto_recap_enabled"
        const val KEY_CUSTOM_STYLE_PROMPT = "custom_style_prompt"
        const val KEY_APPLIED_STYLE_PACK = "applied_style_pack"
        const val KEY_APPLIED_TIMELINE_THEME = "applied_timeline_theme"
        const val KEY_CHAT_MODE_PRO_ENABLED = "chat_mode_pro_enabled"
        /** The user's AI providers, API keys included (see `ProviderStore`). Travels inside an
         * encrypted backup on purpose: restoring onto a new phone should not mean hunting for keys. */
        const val KEY_AI_PROVIDERS = "ai_providers_v1"
        const val KEY_MARKETPLACE_ENABLED = "marketplace_enabled"
        const val KEY_MARKETPLACE_SERVER_URL = "marketplace_server_url"
        /** The very first chat ever opened in the app, set once and never changed — used to detect
         * "is this still the user's first-ever conversation" so it can be shaped into a guaranteed
         * mini story arc (see ChatViewModel.buildSystemPrompt). */
        const val KEY_FIRST_CHAT_ID = "first_chat_id"

        /** String-valued keys eligible for account-transfer — see [exportTransferablePrefs]. */
        private val TRANSFERABLE_STRING_KEYS = listOf(
            KEY_AI_PROVIDERS, KEY_MARKETPLACE_SERVER_URL,
            "op_summary_model_id", "op_lore_model_id", "op_quick_generation_model_id",
            "op_visual_sheet_model_id", "op_image_description_model_id", "op_embedding_model_id",
            "op_translation_model_id", "op_inspiration_model_id", "op_next_reply_suggestions_model_id",
            KEY_APP_LANGUAGE_SELECTED, KEY_DEFAULT_CHAT_MODEL_ID, KEY_DEFAULT_IMAGE_MODEL_ID,
            KEY_DEFAULT_IMAGE_FALLBACK_MODEL_ID,
            KEY_DEFAULT_CHAT_PRO_MODEL_ID, KEY_SAFE_WORD, KEY_USER_FIRST_NAME, KEY_USER_LAST_NAME,
            KEY_USER_PRONOUN, KEY_USER_AGE, KEY_USER_PHYSICAL_DESCRIPTION, KEY_USER_SEXUAL_ORIENTATION,
            KEY_CUSTOM_STYLE_PROMPT, KEY_APPLIED_STYLE_PACK, KEY_APPLIED_TIMELINE_THEME,
            KEY_TASTE_DEFAULT_PRESET, KEY_TASTE_NEVER_WRITE
        )

        /** Int-valued (including boolean-as-0/1) keys eligible for account-transfer — see
         * [exportTransferablePrefs]. */
        private val TRANSFERABLE_INT_KEYS = listOf(
            KEY_DISCREET_MODE_ENABLED, KEY_AUTO_LOCK_TIMEOUT_SECONDS, KEY_FLAG_SECURE_ENABLED,
            KEY_DEFAULT_TEMPERATURE_X100, KEY_AUTO_RECAP_ENABLED, KEY_CHAT_MODE_PRO_ENABLED,
            KEY_MARKETPLACE_ENABLED
        )
    }
}
