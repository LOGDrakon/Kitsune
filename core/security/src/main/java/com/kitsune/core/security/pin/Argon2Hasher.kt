package com.kitsune.core.security.pin

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.security.SecureRandom
import javax.inject.Inject

private const val SALT_LENGTH_BYTES = 16
private const val T_COST_ITERATIONS = 3
private const val M_COST_KIBIBYTE = 65536 // 64 MiB
private const val PARALLELISM = 4
internal const val KEY_LENGTH_BYTES = 32

/** Thin wrapper around Argon2Kt so [PinCredentialManager] doesn't depend on the raw library API directly. */
class Argon2Hasher @Inject constructor() {

    private val argon2 = Argon2Kt()
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }

    /** Raw Argon2id output, used both to verify a PIN and to derive key material for the vault passphrase. */
    fun hash(pin: CharArray, salt: ByteArray): ByteArray {
        val password = String(pin).toByteArray(Charsets.UTF_8)
        val result = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = password,
            salt = salt,
            tCostInIterations = T_COST_ITERATIONS,
            mCostInKibibyte = M_COST_KIBIBYTE,
            parallelism = PARALLELISM,
            hashLengthInBytes = KEY_LENGTH_BYTES
        )
        password.fill(0)
        return result.rawHashAsByteArray()
    }
}
