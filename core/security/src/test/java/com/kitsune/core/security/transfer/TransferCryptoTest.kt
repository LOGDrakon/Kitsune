package com.kitsune.core.security.transfer

import com.kitsune.core.security.pin.Argon2Hasher
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [Argon2Hasher] (native library) is mocked rather than exercised for real, same reasoning as
 * `VaultKeyProviderImplTest` — it doesn't run on a plain JVM unit test. Each fake Argon2Hasher
 * returns a fixed, distinct byte array regardless of the actual PIN digits passed in, standing in
 * for "the Argon2id stretch of a given PIN" — good enough to verify [TransferCrypto]'s own logic
 * (how it combines that output with the random transfer key), which is what these tests target,
 * not Argon2id itself.
 */
private fun fakeArgon2Hasher(fixedOutput: ByteArray): Argon2Hasher = mockk {
    every { hash(any(), any()) } returns fixedOutput
}

class TransferCryptoTest {

    private val pin = "123456".toCharArray()
    private val argonOutputA = ByteArray(32) { 0xAA.toByte() }
    private val argonOutputB = ByteArray(32) { 0xBB.toByte() }

    @Test
    fun `round trip succeeds with the same PIN and the same transfer key`() {
        val crypto = TransferCrypto(fakeArgon2Hasher(argonOutputA))
        val transferKey = crypto.generateTransferKey()
        val plaintext = "kitsune transfer payload".toByteArray()

        val envelope = crypto.encrypt(plaintext, pin, transferKey)
        val decrypted = crypto.decrypt(envelope, pin, transferKey)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `decryption fails when the transfer key does not match, even with the correct PIN`() {
        val crypto = TransferCrypto(fakeArgon2Hasher(argonOutputA))
        val realTransferKey = crypto.generateTransferKey()
        val wrongTransferKey = crypto.generateTransferKey()
        val envelope = crypto.encrypt("secret".toByteArray(), pin, realTransferKey)

        assertThrows(TransferDecryptionException::class.java) {
            crypto.decrypt(envelope, pin, wrongTransferKey)
        }
    }

    @Test
    fun `decryption fails when the PIN-derived material does not match, even with the correct transfer key`() {
        val cryptoCorrectPin = TransferCrypto(fakeArgon2Hasher(argonOutputA))
        val cryptoWrongPin = TransferCrypto(fakeArgon2Hasher(argonOutputB))
        val transferKey = cryptoCorrectPin.generateTransferKey()
        val envelope = cryptoCorrectPin.encrypt("secret".toByteArray(), pin, transferKey)

        assertThrows(TransferDecryptionException::class.java) {
            cryptoWrongPin.decrypt(envelope, pin, transferKey)
        }
    }

    @Test
    fun `decryption fails on a tampered envelope`() {
        val crypto = TransferCrypto(fakeArgon2Hasher(argonOutputA))
        val transferKey = crypto.generateTransferKey()
        val envelope = crypto.encrypt("secret".toByteArray(), pin, transferKey)
        envelope[envelope.size - 1] = (envelope[envelope.size - 1] + 1).toByte()

        assertThrows(TransferDecryptionException::class.java) {
            crypto.decrypt(envelope, pin, transferKey)
        }
    }

    @Test
    fun `generateTransferKey returns TRANSFER_KEY_LENGTH_BYTES of high-entropy, non-repeating output`() {
        val crypto = TransferCrypto(fakeArgon2Hasher(argonOutputA))
        val first = crypto.generateTransferKey()
        val second = crypto.generateTransferKey()

        assertEquals(TRANSFER_KEY_LENGTH_BYTES, first.size)
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun `two independently generated transfer keys produce different ciphertext for the same plaintext and PIN`() {
        val crypto = TransferCrypto(fakeArgon2Hasher(argonOutputA))
        val plaintext = "same plaintext every time".toByteArray()

        val envelopeA = crypto.encrypt(plaintext, pin, crypto.generateTransferKey())
        val envelopeB = crypto.encrypt(plaintext, pin, crypto.generateTransferKey())

        assertNotEquals(envelopeA.toList(), envelopeB.toList())
    }
}
