package com.kitsune.core.security.bugreport

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.inject.Inject
import javax.inject.Singleton

private const val IV_LENGTH_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val AES_KEY_BITS = 256

/**
 * Base64 X.509 SubjectPublicKeyInfo — public half of the RSA-3072 keypair whose private half is
 * held only by the backend (`KitsuneBackend`'s `bugReport.rsaPrivateKey` config / `RsaCrypto`).
 * Regenerate both halves together if this ever needs rotating — see `KitsuneBackend/.env.example`
 * for the openssl commands.
 */
private const val BUG_REPORT_PUBLIC_KEY_B64 =
    "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAvI2el2CxssaWVGb693BmxWeqXNFmP3JLiklh+j2T4PtpFhAH" +
        "jk0MEFA+0C4E/tdRMhNRZ0x1dZlcwb4o2M4kRya/8ImPRdY1VH0nd4EecDQwgunVkcSU+mR2IQMtgRlbu2cqLTbP6Z5q" +
        "gEN7yTpa7DpmIb/QA2ojR312Vr50Fw/RO/oi9KPr0YKaEu4Wpb4QfXWCyZBuOWR/VYuHm6uelsG9SLySMQTbSV7OyPxj" +
        "LLbwlgJ1vshocdDZUzaofBk4IEpCiaX4SCitr6+NXmDoco7GJiKFbYdkYbnyx00Ka/YVQ0yjMPXNzG3B7gO55wEQLe3H" +
        "ZWcfQBDlQ8uNnW0BTHy/3doz5rkbypIO1yLaVoUWeFSNHxEnwlIkmOKBe/am3FC3qme/10LSltsVWaZ+/sOImNbSyMPm" +
        "eDaO11fwXzfgCy1yEDOF4YGpi4ngW6Pgq/NuX1A6WLOlui9D4+7ogVqKttj2Bv+vM1eY/De2gBRebo7XNNbY5g5UxQ4Z" +
        "vaKnAgMBAAE="

/** RSA-OAEP-wrapped AES key + AES/GCM ciphertext, all base64 — the wire format the backend expects
 * on `/v1/bugreports`. Decryptable only by whoever holds the matching RSA private key. */
data class EncryptedBugReport(
    val encryptedKey: String,
    val iv: String,
    val ciphertext: String
)

/**
 * Hybrid RSA/AES encryption for bug reports (FEATURES.md section 9): the report is redacted
 * client-side (see `BuildBugReportUseCase`) then encrypted here so that only the backend — which
 * holds the matching RSA private key — can ever read it in the clear, not anything or anyone in
 * between. RSA alone can't encrypt an arbitrarily long report, hence the hybrid scheme: a random
 * per-report AES-256 key does the actual work (AES/GCM), and only that small key is wrapped with
 * RSA-OAEP. The explicit [OAEPParameterSpec] (rather than relying on a provider's default) is
 * deliberate — it guarantees byte-for-byte interop with the backend's JVM decryption regardless of
 * which JCA provider either side's default happens to be.
 */
@Singleton
class BugReportCrypto @Inject constructor() {

    private val random = SecureRandom()

    fun encrypt(plaintext: String): EncryptedBugReport {
        val aesKey = KeyGenerator.getInstance("AES").apply { init(AES_KEY_BITS, random) }.generateKey()
        val iv = ByteArray(IV_LENGTH_BYTES).also { random.nextBytes(it) }

        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = aesCipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey(), oaepParams())
        val encryptedKey = rsaCipher.doFinal(aesKey.encoded)

        return EncryptedBugReport(
            encryptedKey = Base64.encodeToString(encryptedKey, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    private fun oaepParams() =
        OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)

    private fun publicKey(): PublicKey {
        val keyBytes = Base64.decode(BUG_REPORT_PUBLIC_KEY_B64, Base64.NO_WRAP)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
    }
}
