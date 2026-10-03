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
import java.io.OutputStream
import javax.inject.Inject

/**
 * Writes an encrypted backup of everything local — conversations, personas, universes, images and
 * settings — to [output] (FEATURES.md section 2).
 *
 * The plaintext database copy only ever exists as a temp file in the app's private cache, deleted
 * as soon as it has been read. The caller owns [output] and closes it.
 */
class ExportBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: KitsuneDatabaseProvider,
    private val encryptedImageStore: EncryptedImageStore,
    private val transferCrypto: TransferCrypto,
    private val secureStorage: SecureStorage
) {
    suspend operator fun invoke(passphrase: CharArray, output: OutputStream) = withContext(Dispatchers.IO) {
        require(passphrase.size >= BackupFormat.MIN_PASSPHRASE_LENGTH) {
            "La phrase secrète doit contenir au moins ${BackupFormat.MIN_PASSPHRASE_LENGTH} caractères."
        }

        val tempDbFile = File(context.cacheDir, "backup_export_${System.currentTimeMillis()}.db")
        val dbBytes: ByteArray
        try {
            databaseProvider.exportPlaintextCopy(tempDbFile)
            dbBytes = tempDbFile.readBytes()
        } finally {
            tempDbFile.delete()
        }

        val images = encryptedImageStore.listAllIds().mapNotNull { id ->
            encryptedImageStore.load(id)?.let { id to it }
        }

        val archive = TransferArchive.pack(dbBytes, images, secureStorage.exportTransferablePrefs())
        val envelope = transferCrypto.encrypt(archive, passphrase, BackupFormat.FIXED_KEY_COMPONENT)

        output.write(BackupFormat.MAGIC)
        output.write(envelope)
        output.flush()
    }
}
