package com.kitsune.core.security.vault

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.kitsune.core.common.coroutines.DispatcherProvider
import com.kitsune.core.security.biometric.BiometricAuthManager
import com.kitsune.core.security.decoy.DecoyNotesKeyProvider
import com.kitsune.core.security.keystore.KeystoreManager
import com.kitsune.core.security.model.BiometricAuthResult
import com.kitsune.core.security.model.PinSlot
import com.kitsune.core.security.model.PinVerificationResult
import com.kitsune.core.security.model.VaultSecurityMode
import com.kitsune.core.security.pin.PinCredentialManager
import com.kitsune.core.security.storage.SecureStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/**
 * [KeystoreManager] wraps the real (JNI/hardware-backed) Android Keystore, which doesn't exist on
 * a plain JVM unit test — this fake swaps in an ordinary in-memory AES-GCM key so the wrap/unwrap
 * round trip in [VaultKeyProviderImpl] is exercised for real, just without touching
 * `AndroidKeyStore`. [Argon2Hasher] (native library) is avoided entirely by mocking
 * [PinCredentialManager] directly instead of going through real PIN hashing.
 */
private fun fakeKeystoreManager(): KeystoreManager {
    val secretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    return mockk<KeystoreManager> {
        every { newEncryptCipher() } answers {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey) }
        }
        every { newDecryptCipher(any()) } answers {
            val iv = firstArg<ByteArray>()
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv)) }
        }
    }
}

private fun fakeDispatchers(): DispatcherProvider = mockk {
    every { default } returns Dispatchers.Unconfined
    every { io } returns Dispatchers.Unconfined
    every { main } returns Dispatchers.Unconfined
}

class VaultKeyProviderImplTest {

    private val keystoreManager = fakeKeystoreManager()
    private val biometricAuthManager = mockk<BiometricAuthManager>()
    private val pinCredentialManager = mockk<PinCredentialManager>()
    private val secureStorage = InMemorySecureStorage()
    private val secureStorageMock = secureStorage.asMock()
    private val activity = mockk<FragmentActivity>(relaxed = true)

    private val provider = VaultKeyProviderImpl(
        keystoreManager,
        biometricAuthManager,
        pinCredentialManager,
        secureStorageMock,
        DecoyNotesKeyProvider(secureStorageMock),
        fakeDispatchers()
    )

    @Before
    fun setUp() {
        // authenticate(activity, title, subtitle = null, cryptoObject) — arg index 3 is the cipher.
        coEvery { biometricAuthManager.authenticate(any(), any(), any(), any()) } coAnswers {
            val cipher = arg<BiometricPrompt.CryptoObject?>(3)?.cipher
            BiometricAuthResult.Success(cipher?.let { BiometricPrompt.CryptoObject(it) })
        }
    }

    @Test
    fun `security mode defaults to ALL when nothing is stored`() = runTest {
        assertEquals(VaultSecurityMode.ALL, provider.getSecurityMode())
    }

    @Test
    fun `security mode falls back to ALL on a corrupted stored value`() = runTest {
        secureStorage.strings[SecureStorage.KEY_VAULT_SECURITY_MODE] = "not-a-real-mode"
        assertEquals(VaultSecurityMode.ALL, provider.getSecurityMode())
    }

    @Test
    fun `initialize sets mode to ALL and derives a working passphrase`() = runTest {
        every { pinCredentialManager.setPin(any(), PinSlot.REAL) } returns byteArrayOf(1, 2, 3)

        val result = provider.initialize(activity, "1234".toCharArray())

        assertTrue(result is VaultUnlockResult.Unlocked)
        assertEquals(VaultSecurityMode.ALL, provider.getSecurityMode())
    }

    @Test
    fun `unlocking with the real PIN in ALL mode derives the same passphrase initialize produced`() = runTest {
        every { pinCredentialManager.setPin(any(), PinSlot.REAL) } returns byteArrayOf(1, 2, 3)
        val initResult = provider.initialize(activity, "1234".toCharArray()) as VaultUnlockResult.Unlocked

        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.Match(PinSlot.REAL, byteArrayOf(1, 2, 3))
        val unlockResult = provider.unlock(activity, "1234".toCharArray()) as VaultUnlockResult.Unlocked

        assertArrayEquals(initResult.passphrase, unlockResult.passphrase)
    }

    @Test
    fun `unlock returns WrongPin when PinCredentialManager finds no match`() = runTest {
        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.NoMatch

        val result = provider.unlock(activity, "0000".toCharArray())

        assertEquals(VaultUnlockResult.WrongPin, result)
    }

    @Test
    fun `unlock with the panic PIN never touches biometrics and derives a decoy passphrase`() = runTest {
        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.Match(PinSlot.PANIC, byteArrayOf(9, 9))

        val result = provider.unlock(activity, "6666".toCharArray()) as VaultUnlockResult.Unlocked

        assertEquals(PinSlot.PANIC, result.slot)
        assertTrue(result.passphrase.isNotEmpty())
        io.mockk.coVerify(exactly = 0) { biometricAuthManager.authenticate(any(), any(), any(), any()) }
    }

    @Test
    fun `PIN_ONLY mode unlock never calls biometrics and derives a different passphrase than ALL would`() = runTest {
        every { pinCredentialManager.setPin(any(), PinSlot.REAL) } returns byteArrayOf(1, 2, 3)
        val initResult = provider.initialize(activity, "1234".toCharArray()) as VaultUnlockResult.Unlocked

        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.Match(PinSlot.REAL, byteArrayOf(1, 2, 3))
        val prepared = provider.prepareSecurityModeChange(activity, VaultSecurityMode.PIN_ONLY, currentPin = "1234".toCharArray())
            as VaultModeChangeResult.Prepared
        provider.commitSecurityMode(VaultSecurityMode.PIN_ONLY)

        assertEquals(VaultSecurityMode.PIN_ONLY, provider.getSecurityMode())
        assertNotEquals(initResult.passphrase.toList(), prepared.newPassphrase.toList())

        io.mockk.clearMocks(biometricAuthManager, answers = false)
        val unlockResult = provider.unlock(activity, "1234".toCharArray()) as VaultUnlockResult.Unlocked
        assertArrayEquals(prepared.newPassphrase, unlockResult.passphrase)
        io.mockk.coVerify(exactly = 0) { biometricAuthManager.authenticate(any(), any(), any(), any()) }
    }

    @Test
    fun `panic PIN still unlocks after switching to PIN_ONLY`() = runTest {
        every { pinCredentialManager.setPin(any(), PinSlot.REAL) } returns byteArrayOf(1, 2, 3)
        provider.initialize(activity, "1234".toCharArray())
        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.Match(PinSlot.REAL, byteArrayOf(1, 2, 3))
        provider.prepareSecurityModeChange(activity, VaultSecurityMode.PIN_ONLY, currentPin = "1234".toCharArray())
        provider.commitSecurityMode(VaultSecurityMode.PIN_ONLY)

        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.Match(PinSlot.PANIC, byteArrayOf(9))
        val result = provider.unlock(activity, "6666".toCharArray()) as VaultUnlockResult.Unlocked

        assertEquals(PinSlot.PANIC, result.slot)
        assertTrue(result.passphrase.isNotEmpty())
    }

    @Test
    fun `BIOMETRIC_ONLY mode ignores the pin parameter and derives a passphrase with no PIN material`() = runTest {
        every { pinCredentialManager.setPin(any(), PinSlot.REAL) } returns byteArrayOf(1, 2, 3)
        provider.initialize(activity, "1234".toCharArray())
        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.Match(PinSlot.REAL, byteArrayOf(1, 2, 3))

        val prepared = provider.prepareSecurityModeChange(activity, VaultSecurityMode.BIOMETRIC_ONLY, currentPin = "1234".toCharArray())
            as VaultModeChangeResult.Prepared
        provider.commitSecurityMode(VaultSecurityMode.BIOMETRIC_ONLY)

        assertEquals(VaultSecurityMode.BIOMETRIC_ONLY, provider.getSecurityMode())
        val unlockResult = provider.unlock(activity, "".toCharArray()) as VaultUnlockResult.Unlocked
        assertArrayEquals(prepared.newPassphrase, unlockResult.passphrase)
    }

    @Test
    fun `switching away from PIN_ONLY removes the software-held DB key`() = runTest {
        every { pinCredentialManager.setPin(any(), PinSlot.REAL) } returns byteArrayOf(1, 2, 3)
        provider.initialize(activity, "1234".toCharArray())
        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.Match(PinSlot.REAL, byteArrayOf(1, 2, 3))

        provider.prepareSecurityModeChange(activity, VaultSecurityMode.PIN_ONLY, currentPin = "1234".toCharArray())
        provider.commitSecurityMode(VaultSecurityMode.PIN_ONLY)
        assertTrue(secureStorage.bytes.containsKey(SecureStorage.KEY_DB_KEY_SOFTWARE))

        provider.prepareSecurityModeChange(activity, VaultSecurityMode.ALL, currentPin = "1234".toCharArray())
        provider.commitSecurityMode(VaultSecurityMode.ALL)
        assertFalse(secureStorage.bytes.containsKey(SecureStorage.KEY_DB_KEY_SOFTWARE))
    }

    @Test
    fun `prepareSecurityModeChange requires the current PIN when the current mode is not BIOMETRIC_ONLY`() = runTest {
        every { pinCredentialManager.setPin(any(), PinSlot.REAL) } returns byteArrayOf(1, 2, 3)
        provider.initialize(activity, "1234".toCharArray())

        val result = provider.prepareSecurityModeChange(activity, VaultSecurityMode.PIN_ONLY, currentPin = null)

        assertEquals(VaultModeChangeResult.WrongPin, result)
    }

    @Test
    fun `re-enabling PIN from BIOMETRIC_ONLY requires a fresh PIN`() = runTest {
        every { pinCredentialManager.setPin(any(), PinSlot.REAL) } returns byteArrayOf(1, 2, 3)
        provider.initialize(activity, "1234".toCharArray())
        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.Match(PinSlot.REAL, byteArrayOf(1, 2, 3))
        provider.prepareSecurityModeChange(activity, VaultSecurityMode.BIOMETRIC_ONLY, currentPin = "1234".toCharArray())
        provider.commitSecurityMode(VaultSecurityMode.BIOMETRIC_ONLY)

        val missing = provider.prepareSecurityModeChange(activity, VaultSecurityMode.ALL, newPin = null)
        assertEquals(VaultModeChangeResult.MissingNewPin, missing)

        val newPinSlot = slot<CharArray>()
        every { pinCredentialManager.setPin(capture(newPinSlot), PinSlot.REAL) } returns byteArrayOf(7, 7, 7)
        val prepared = provider.prepareSecurityModeChange(activity, VaultSecurityMode.ALL, newPin = "5678".toCharArray())
        assertTrue(prepared is VaultModeChangeResult.Prepared)
        assertEquals("5678", String(newPinSlot.captured))
    }

    @Test
    fun `setPanicPin is rejected in BIOMETRIC_ONLY mode`() = runTest {
        every { pinCredentialManager.setPin(any(), any()) } returns byteArrayOf(1, 2, 3)
        every { pinCredentialManager.isConfigured(PinSlot.REAL) } returns true
        provider.initialize(activity, "1234".toCharArray())
        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.Match(PinSlot.REAL, byteArrayOf(1, 2, 3))
        provider.prepareSecurityModeChange(activity, VaultSecurityMode.BIOMETRIC_ONLY, currentPin = "1234".toCharArray())
        provider.commitSecurityMode(VaultSecurityMode.BIOMETRIC_ONLY)

        val thrown = runCatching { provider.setPanicPin("9999".toCharArray()) }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `setPanicPin rejects a candidate equal to the real PIN`() = runTest {
        // Pentest finding (see BUGS.md): PinCredentialManager.verify() checks REAL before PANIC —
        // a panic PIN equal to the real PIN would silently unlock the real vault under duress.
        every { pinCredentialManager.setPin(any(), PinSlot.REAL) } returns byteArrayOf(1, 2, 3)
        every { pinCredentialManager.isConfigured(PinSlot.REAL) } returns true
        provider.initialize(activity, "1234".toCharArray())
        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.Match(PinSlot.REAL, byteArrayOf(1, 2, 3))

        val thrown = runCatching { provider.setPanicPin("1234".toCharArray()) }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        io.mockk.verify(exactly = 0) { pinCredentialManager.setPin(any(), PinSlot.PANIC) }
    }

    @Test
    fun `setPanicPin succeeds in ALL mode`() = runTest {
        every { pinCredentialManager.setPin(any(), any()) } returns byteArrayOf(1, 2, 3)
        every { pinCredentialManager.isConfigured(PinSlot.REAL) } returns true
        provider.initialize(activity, "1234".toCharArray())
        // "9999" must not match the real PIN "1234" — setPanicPin now checks this first.
        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.NoMatch

        val result = provider.setPanicPin("9999".toCharArray())

        io.mockk.verify { pinCredentialManager.setPin(any(), PinSlot.PANIC) }
        assertTrue(result.newPassphrase.isNotEmpty())
    }

    @Test
    fun `setPanicPin reports no old passphrase on first-time setup, but one on a later change`() = runTest {
        every { pinCredentialManager.setPin(any(), PinSlot.REAL) } returns byteArrayOf(1, 2, 3)
        every { pinCredentialManager.isConfigured(PinSlot.REAL) } returns true
        provider.initialize(activity, "1234".toCharArray())
        // Neither "9999" nor "1111" match the real PIN "1234" — setPanicPin now checks this first.
        every { pinCredentialManager.verify(any()) } returns PinVerificationResult.NoMatch

        // pinCredentialManager is a pure mock here (Argon2Kt's native lib can't run on the plain
        // JVM, per this file's own doc comment) — its real behavior of persisting the hash it
        // returns (see PinCredentialManager.setPin) has to be simulated by hand so the second call
        // can find what the first one "stored" via VaultKeyProviderImpl's direct SecureStorage read.
        every { pinCredentialManager.setPin(any(), PinSlot.PANIC) } answers {
            secureStorageMock.putBytes(SecureStorage.KEY_PIN_HASH_PANIC, byteArrayOf(9, 9))
            byteArrayOf(9, 9)
        }
        val firstSetup = provider.setPanicPin("9999".toCharArray())
        assertEquals(null, firstSetup.oldPassphrase)

        every { pinCredentialManager.setPin(any(), PinSlot.PANIC) } answers {
            secureStorageMock.putBytes(SecureStorage.KEY_PIN_HASH_PANIC, byteArrayOf(7, 7))
            byteArrayOf(7, 7)
        }
        val changed = provider.setPanicPin("1111".toCharArray())
        assertTrue(changed.oldPassphrase != null)
        assertNotEquals(changed.oldPassphrase!!.toList(), changed.newPassphrase.toList())
        // The old passphrase reported for the change must match what the first setup actually derived.
        assertArrayEquals(firstSetup.newPassphrase, changed.oldPassphrase)
    }
}

/** Minimal in-memory stand-in for [SecureStorage] (a concrete class wrapping real
 * `EncryptedSharedPreferences`, which needs an Android context this test doesn't have). */
private class InMemorySecureStorage {
    val bytes = mutableMapOf<String, ByteArray>()
    val strings = mutableMapOf<String, String>()

    fun asMock(): SecureStorage {
        val storage = mockk<SecureStorage>()
        // Real SecureStorage Base64-encodes into a new String (EncryptedSharedPreferences), which
        // is naturally a defensive copy — store a copy here too, so a caller zeroing its own
        // ByteArray after use (as VaultKeyProviderImpl does with sensitive key material) doesn't
        // also corrupt what was "persisted", which a live-reference fake would incorrectly do.
        every { storage.putBytes(any(), any()) } answers { bytes[firstArg()] = secondArg<ByteArray>().copyOf() }
        every { storage.getBytes(any()) } answers { bytes[firstArg()]?.copyOf() }
        every { storage.putString(any(), any()) } answers { strings[firstArg()] = secondArg() }
        every { storage.getString(any()) } answers { strings[firstArg()] }
        every { storage.contains(any()) } answers { bytes.containsKey(firstArg()) || strings.containsKey(firstArg()) }
        every { storage.remove(any()) } answers { bytes.remove(firstArg<String>()); strings.remove(firstArg<String>()); Unit }
        return storage
    }
}
