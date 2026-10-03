package com.kitsune.app

import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsune.core.common.coroutines.DefaultDispatcherProvider
import com.kitsune.core.data.local.database.KitsuneDatabaseProviderImpl
import com.kitsune.core.security.biometric.BiometricAuthManager
import com.kitsune.core.security.keystore.KeystoreManager
import com.kitsune.core.security.pin.Argon2Hasher
import com.kitsune.core.security.pin.PinCredentialManager
import com.kitsune.core.security.storage.SecureStorage
import com.kitsune.core.security.vault.VaultKeyProviderImpl
import com.kitsune.core.security.vault.VaultUnlockResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * THROWAWAY rescue tool — delete this file (and app/src/androidTest/AndroidManifest.xml's
 * RescueHostActivity entry) once the database has been recovered.
 *
 * Real diagnosed cause (verified against the actual crash log via `adb logcat -b crash`, NOT
 * assumed): `net.zetetic.database.sqlcipher.SQLiteNotADatabaseException: file is not a database
 * (code 26)` — the passphrase derived for the persisted VaultSecurityMode does not match the key
 * the on-disk database is actually encrypted with. This is almost certainly a stale-key situation
 * from an interrupted/inconsistent security-mode switch, NOT missing code: this exact source tree
 * already dispatches `VaultKeyProvider.unlock()` by the persisted mode correctly (see
 * VaultKeyProviderImpl.unlock), so there is no "wrong formula hardcoded" bug to fix here.
 *
 * This tool reuses the app's real, already-correct unlock/dispatch logic (VaultKeyProviderImpl,
 * KitsuneDatabaseProviderImpl) rather than reimplementing SQLCipher/HKDF calls by hand, and
 * deliberately:
 * - Never logs any raw key material (only pass/fail status).
 * - Verifies the passphrase actually works with a real query before trusting it, instead of
 *   assuming a derived passphrase is correct.
 * - Exports to this app's own app-private external files dir (still `adb pull`-able while USB
 *   debugging is on), NOT the public Downloads folder — avoids leaving a permanent, world-readable
 *   plaintext copy of your data on the device once recovery is done.
 * - Runs with no Hilt test setup (all dependencies here are plain classes) to keep this file fully
 *   self-contained and easy to delete without touching any other test infrastructure.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseRescueTest {

    private val tag = "DatabaseRescue"

    @Test
    fun exportDecryptedDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dispatchers = DefaultDispatcherProvider()
        val secureStorage = SecureStorage(context)
        val pinCredentialManager = PinCredentialManager(secureStorage, Argon2Hasher())
        val vaultKeyProvider = VaultKeyProviderImpl(
            KeystoreManager(),
            BiometricAuthManager(),
            pinCredentialManager,
            secureStorage,
            dispatchers
        )

        // Raw byte-for-byte backup of the still-encrypted file FIRST, before anything below opens
        // it — this repo's schema is v8 (a migration was added this session) while the on-disk DB
        // may still be v7, so the very act of opening it through Room below will trigger an
        // automatic ALTER TABLE migration on the original file. That migration is additive/safe in
        // principle, but this copy exists so an untouched original always survives regardless.
        val liveDbFile = context.getDatabasePath("kitsune.db")
        val backupFile = File(context.getExternalFilesDir(null), "kitsune_original_backup.db")
        if (liveDbFile.exists()) {
            liveDbFile.copyTo(backupFile, overwrite = true)
            Log.i(tag, "Backed up untouched original to: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
        } else {
            Log.e(tag, "No database file found at ${liveDbFile.absolutePath} — aborting.")
            return
        }

        ActivityScenario.launch(RescueHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    val mode = vaultKeyProvider.getSecurityMode()
                    Log.i(tag, "Persisted security mode: $mode")

                    val unlockResult = vaultKeyProvider.unlock(activity, CharArray(0))
                    val passphrase = (unlockResult as? VaultUnlockResult.Unlocked)?.passphrase
                    if (passphrase == null) {
                        Log.e(tag, "Unlock failed: $unlockResult — cannot proceed with export.")
                        return@runBlocking
                    }

                    val databaseProvider = KitsuneDatabaseProviderImpl(context, dispatchers)
                    val opened = try {
                        val db = databaseProvider.open(passphrase)
                        // Force a real read now, so a wrong passphrase surfaces here rather than
                        // lazily crashing the app later, same failure the real app hit.
                        db.query("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
                        db
                    } catch (e: Exception) {
                        Log.e(tag, "This mode's derived passphrase did NOT open the database (${e.message}). " +
                            "The database is genuinely keyed with a different passphrase than the persisted " +
                            "mode expects — recovering it needs the PIN/mode that was actually used to encrypt " +
                            "it (see RESCUE_PLAN.md section 7). Not attempting further guesses automatically.")
                        return@runBlocking
                    }

                    Log.i(tag, "Database opened and verified successfully with the current mode's passphrase.")

                    val exportFile = File(context.getExternalFilesDir(null), "kitsune_rescue_plain.db")
                    if (exportFile.exists()) exportFile.delete()

                    val writable = opened.openHelper.writableDatabase
                    writable.execSQL("ATTACH DATABASE '${exportFile.absolutePath}' AS plaintext KEY ''")
                    writable.execSQL("SELECT sqlcipher_export('plaintext')")
                    writable.execSQL("DETACH DATABASE plaintext")

                    Log.i(tag, "RESCUE SUCCESS: ${exportFile.absolutePath} (${exportFile.length()} bytes)")
                    Log.i(tag, "Pull it with: adb pull ${exportFile.absolutePath} ./kitsune_rescue_plain.db")
                }
            }
        }
    }
}
