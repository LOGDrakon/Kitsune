package com.kitsune.core.security.decoy

import com.kitsune.core.security.crypto.Hkdf
import com.kitsune.core.security.storage.SecureStorage
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private const val HKDF_INFO_DECOY_NOTES = "kitsune-decoy-notes-passphrase-v1"
private const val DECOY_KEY_LENGTH_BYTES = 32

/**
 * Derives the SQLCipher passphrase for the decoy notes database (shown when the panic PIN is
 * entered — see `VaultKeyProviderImpl`'s two `PinSlot.PANIC` branches) from the panic PIN's own
 * Argon2id key material, mirroring exactly how `VaultKeyProviderImpl.unlockPinOnly` derives the
 * real vault's `PIN_ONLY`-mode passphrase — but with a fully disjoint random key and HKDF info
 * string, so this passphrase can never equal or derive the real vault's passphrase. Deliberately
 * kept out of `VaultKeyProviderImpl` itself so that class's "never touches the real Keystore-wrapped
 * key for a panic match" invariant stays trivially auditable in isolation.
 */
@Singleton
class DecoyNotesKeyProvider @Inject constructor(
    private val secureStorage: SecureStorage
) {
    fun derivePassphrase(panicPinKeyMaterial: ByteArray): ByteArray {
        val randomKey = ensureDecoyRandomKey()
        return Hkdf.deriveKey(randomKey + panicPinKeyMaterial, HKDF_INFO_DECOY_NOTES.toByteArray())
    }

    private fun ensureDecoyRandomKey(): ByteArray {
        secureStorage.getBytes(SecureStorage.KEY_DECOY_NOTES_DB_KEY)?.let { return it }
        val generated = ByteArray(DECOY_KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        secureStorage.putBytes(SecureStorage.KEY_DECOY_NOTES_DB_KEY, generated)
        return generated
    }
}
