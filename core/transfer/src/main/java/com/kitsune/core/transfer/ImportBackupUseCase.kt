package com.kitsune.core.transfer

import android.content.Context
import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.security.storage.EncryptedImageStore
import com.kitsune.core.security.storage.SecureStorage
import com.kitsune.core.security.transfer.TransferCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Restores a backup written by [ExportBackupUseCase] on a fresh install (FEATURES.md section 2).
 *
 * Exposed as discrete steps because the caller must set up this device's own vault PIN
 * (`VaultKeyProvider.initialize`, which needs a `FragmentActivity` this module has no access to)
 * between [decryptAndUnpack] and [importDatabaseAndImages].
 */
class ImportBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: KitsuneDatabaseProvider,
    private val encryptedImageStore: EncryptedImageStore,
    private val transferCrypto: TransferCrypto,
    private val secureStorage: SecureStorage
) {
    /** Throws [NotABackupFileException] for a file that isn't one,
     * [com.kitsune.core.security.transfer.TransferDecryptionException] for a wrong passphrase or a
     * damaged file (deliberately indistinguishable), [ArchiveIntegrityException] if the CRC32 check
     * fails after a successful decryption. */
    suspend fun decryptAndUnpack(fileBytes: ByteArray, passphrase: CharArray): TransferArchive.UnpackedArchive =
        withContext(Dispatchers.Default) {
            if (!BackupFormat.hasMagic(fileBytes)) throw NotABackupFileException()
            val envelope = fileBytes.copyOfRange(BackupFormat.MAGIC.size, fileBytes.size)
            TransferArchive.unpack(transferCrypto.decrypt(envelope, passphrase, BackupFormat.FIXED_KEY_COMPONENT))
        }

    /** Must be called with a passphrase freshly derived by this device's own
     * `VaultKeyProvider.initialize(activity, newPin)`, and before any [KitsuneDatabaseProvider.open]. */
    suspend fun importDatabaseAndImages(archive: TransferArchive.UnpackedArchive, newPassphrase: ByteArray) =
        withContext(Dispatchers.IO) {
            val tempDbFile = File(context.cacheDir, "backup_import_${System.currentTimeMillis()}.db")
            try {
                tempDbFile.writeBytes(archive.dbBytes)
                databaseProvider.importPlaintextCopy(tempDbFile, newPassphrase)
            } finally {
                tempDbFile.delete()
            }

            databaseProvider.open(newPassphrase)
            archive.images.forEach { (id, bytes) -> encryptedImageStore.saveWithId(id, bytes) }

            // Settings travel with the data (profile, safe word, model and chat preferences, AI
            // providers) — see SecureStorage.exportTransferablePrefs for what is included and why
            // vault keys are not.
            secureStorage.importTransferablePrefs(archive.prefs)

            // A restored vault already has history: it must never get the "first time" treatment.
            context.getSharedPreferences("kitsune_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("welcome_shown", true).apply()
        }

    /** A real query against the freshly imported, re-encrypted database. Throws rather than
     * returning a boolean, so the real SQLite error reaches the caller's logging. */
    suspend fun verifyImportedDatabase() = withContext(Dispatchers.IO) {
        databaseProvider.requireOpen().openHelper.readableDatabase
            .query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
        Unit
    }
}
