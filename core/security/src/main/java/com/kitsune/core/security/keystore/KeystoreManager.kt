package com.kitsune.core.security.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "kitsune_db_key_wrapper"
private const val GCM_TAG_LENGTH_BITS = 128

/**
 * Owns the hardware-backed AES key that wraps the random database key (see
 * [com.kitsune.core.security.vault.VaultKeyProvider]). The key requires a fresh
 * biometric/device-credential auth for every single use: callers must run the returned
 * [Cipher] through a [androidx.biometric.BiometricPrompt.CryptoObject] before calling
 * `doFinal` on it, otherwise the Keystore throws `UserNotAuthenticatedException`.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Cipher ready to be wrapped in a [androidx.biometric.BiometricPrompt.CryptoObject]. Read [Cipher.getIV] before auth if needed. */
    fun newEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher
    }

    /** Cipher ready to be wrapped in a [androidx.biometric.BiometricPrompt.CryptoObject], for the IV produced by [newEncryptCipher]. */
    fun newDecryptCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher
    }

    fun isKeyPresent(): Boolean = keyStore.containsAlias(KEY_ALIAS)

    private fun getOrCreateKey(): SecretKey {
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        return generateKey()
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val baseSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                // setUserAuthenticationParameters (with DEVICE_CREDENTIAL as a fallback) needs API 30;
                // older devices fall back to biometric-only, auth-per-use.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }

        return try {
            generator.init(baseSpec.setIsStrongBoxBacked(true).build())
            generator.generateKey()
        } catch (_: StrongBoxUnavailableException) {
            generator.init(baseSpec.setIsStrongBoxBacked(false).build())
            generator.generateKey()
        }
    }

    companion object {
        private const val TRANSFORMATION = "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
        const val IV_LENGTH_BYTES = 12
    }
}
