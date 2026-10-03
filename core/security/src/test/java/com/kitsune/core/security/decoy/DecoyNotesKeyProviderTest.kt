package com.kitsune.core.security.decoy

import com.kitsune.core.security.crypto.Hkdf
import com.kitsune.core.security.storage.SecureStorage
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Minimal in-memory [SecureStorage] fake — see the identical pattern/rationale in
 *  `VaultKeyProviderImplTest`'s `InMemorySecureStorage`. */
private fun fakeSecureStorage(): SecureStorage {
    val bytes = mutableMapOf<String, ByteArray>()
    return mockk {
        every { putBytes(any(), any()) } answers { bytes[firstArg()] = secondArg<ByteArray>().copyOf() }
        every { getBytes(any()) } answers { bytes[firstArg()]?.copyOf() }
    }
}

class DecoyNotesKeyProviderTest {

    @Test
    fun `derives the same passphrase for the same PIN key material`() {
        val provider = DecoyNotesKeyProvider(fakeSecureStorage())
        val keyMaterial = byteArrayOf(1, 2, 3)

        val first = provider.derivePassphrase(keyMaterial)
        val second = provider.derivePassphrase(keyMaterial)

        assertArrayEquals(first, second)
    }

    @Test
    fun `derives a different passphrase for different PIN key material`() {
        val provider = DecoyNotesKeyProvider(fakeSecureStorage())

        val a = provider.derivePassphrase(byteArrayOf(1, 2, 3))
        val b = provider.derivePassphrase(byteArrayOf(4, 5, 6))

        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun `generates the random decoy key only once and reuses it across calls`() {
        val storage = fakeSecureStorage()
        val provider = DecoyNotesKeyProvider(storage)

        provider.derivePassphrase(byteArrayOf(1, 2, 3))
        val keyAfterFirstCall = storage.getBytes(SecureStorage.KEY_DECOY_NOTES_DB_KEY)
        provider.derivePassphrase(byteArrayOf(1, 2, 3))
        val keyAfterSecondCall = storage.getBytes(SecureStorage.KEY_DECOY_NOTES_DB_KEY)

        assertArrayEquals(keyAfterFirstCall, keyAfterSecondCall)
    }

    @Test
    fun `never produces the same passphrase the real vault's HKDF info strings would for identical raw inputs`() {
        val storage = fakeSecureStorage()
        val provider = DecoyNotesKeyProvider(storage)
        val keyMaterial = byteArrayOf(1, 2, 3)

        val decoyPassphrase = provider.derivePassphrase(keyMaterial)
        val decoyRandomKey = storage.getBytes(SecureStorage.KEY_DECOY_NOTES_DB_KEY)!!

        // Same raw inputs (random key + PIN material), but through the real vault's own HKDF info
        // strings — must never collide with the decoy passphrase above, or the two databases would
        // no longer be cryptographically independent.
        val allModePassphrase = Hkdf.deriveKey(decoyRandomKey + keyMaterial, "kitsune-vault-passphrase-v1".toByteArray())
        val pinOnlyPassphrase = Hkdf.deriveKey(decoyRandomKey + keyMaterial, "kitsune-vault-passphrase-pin-only-v1".toByteArray())

        assertNotEquals(decoyPassphrase.toList(), allModePassphrase.toList())
        assertNotEquals(decoyPassphrase.toList(), pinOnlyPassphrase.toList())
    }
}
