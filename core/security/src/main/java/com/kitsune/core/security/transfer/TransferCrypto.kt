package com.kitsune.core.security.transfer

import com.kitsune.core.security.crypto.Hkdf
import com.kitsune.core.security.pin.Argon2Hasher
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

private const val SALT_LENGTH_BYTES = 16
private const val IV_LENGTH_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val TRANSFER_HKDF_INFO = "kitsune-transfer-v2"

/** Length of the random, high-entropy component of the transfer key (see class doc) — 256 bits,
 * matching [com.kitsune.core.security.vault.VaultKeyProviderImpl]'s `randomDbKey`. */
const val TRANSFER_KEY_LENGTH_BYTES = 32

/** Thrown when [TransferCrypto.decrypt] fails — either a wrong PIN or a tampered/corrupted
 * envelope (AES-GCM's authentication tag doesn't distinguish the two, and neither should the
 * caller's error message: revealing which one it was would help an attacker brute-forcing PINs). */
class TransferDecryptionException(cause: Throwable) : Exception("Wrong PIN or corrupted transfer data", cause)

/**
 * Account-transfer transport encryption (FEATURES.md section 2). The PIN the user sets on the old
 * phone never leaves either device and is never sent to or stored by the backend — only this
 * envelope (ciphertext + the salt/IV needed to re-derive the key from the SAME PIN typed on the
 * new phone) ever crosses the network, via the backend's opaque blob relay.
 *
 * The envelope is decryptable only from the combination of TWO independent secrets, exactly
 * mirroring [com.kitsune.core.security.vault.VaultKeyProviderImpl]'s own two-factor pattern
 * (`HKDF(randomDbKey + pinKeyMaterial, info)`):
 * - the transfer PIN (low entropy by design — a human has to type it twice from memory), and
 * - [TRANSFER_KEY_LENGTH_BYTES] of random bytes ([generateTransferKey]) that never touch the
 *   backend at all — they travel only inside the QR code scanned directly between the two
 *   devices (see `TransferPairing`).
 *
 * This closes a real weakness a 6-digit-minimum PIN alone would have: the encrypted blob does sit
 * on the backend relay for the duration of the transfer, and Argon2id being slow-but-bruteforceable
 * doesn't change that a 6-digit keyspace (10^6) is small enough to exhaust offline. Anyone who
 * only obtains the blob (e.g. a server compromise) — without also having captured the QR code at
 * the exact moment of a specific transfer — gains nothing from also knowing or guessing the PIN.
 *
 * Own random salt and a distinct HKDF info string vs. the vault's, so a transfer key can never
 * collide with a vault passphrase even if the same PIN digits were reused.
 */
class TransferCrypto @Inject constructor(private val argon2Hasher: Argon2Hasher) {

    private val random = SecureRandom()

    /** Generates the random, QR-only half of the transfer key. Call once per transfer session on
     * the old phone; never persisted, never sent to the backend. */
    fun generateTransferKey(): ByteArray = ByteArray(TRANSFER_KEY_LENGTH_BYTES).also { random.nextBytes(it) }

    /** Encrypts [plaintext] with a key derived from [pin] AND [transferKey] (see class doc).
     * Returns `salt(16) + iv(12) + ciphertext+tag`. */
    fun encrypt(plaintext: ByteArray, pin: CharArray, transferKey: ByteArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(pin, transferKey, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        return salt + iv + ciphertext
    }

    /** Reverses [encrypt]. Throws [TransferDecryptionException] on a wrong PIN/transfer key, or
     * any tampering. */
    fun decrypt(envelope: ByteArray, pin: CharArray, transferKey: ByteArray): ByteArray {
        require(envelope.size > SALT_LENGTH_BYTES + IV_LENGTH_BYTES) { "Transfer envelope too short" }
        val salt = envelope.copyOfRange(0, SALT_LENGTH_BYTES)
        val iv = envelope.copyOfRange(SALT_LENGTH_BYTES, SALT_LENGTH_BYTES + IV_LENGTH_BYTES)
        val ciphertext = envelope.copyOfRange(SALT_LENGTH_BYTES + IV_LENGTH_BYTES, envelope.size)
        val key = deriveKey(pin, transferKey, salt)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw TransferDecryptionException(e)
        }
    }

    private fun deriveKey(pin: CharArray, transferKey: ByteArray, salt: ByteArray): ByteArray {
        val argon2Output = argon2Hasher.hash(pin, salt)
        return Hkdf.deriveKey(argon2Output + transferKey, TRANSFER_HKDF_INFO.toByteArray(Charsets.US_ASCII))
    }
}
