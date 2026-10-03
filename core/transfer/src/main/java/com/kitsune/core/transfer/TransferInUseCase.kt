package com.kitsune.core.transfer

import android.content.Context
import android.util.Base64
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.model.ClaimTransferResponse
import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.security.storage.EncryptedImageStore
import com.kitsune.core.security.storage.SecureStorage
import com.kitsune.core.security.transfer.TransferCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val POLL_INTERVAL_MS = 2000L
private const val COMPLETE_RETRY_ATTEMPTS = 3

/**
 * New-phone side of account transfer (FEATURES.md section 2). Exposed as discrete suspend steps
 * (rather than one end-to-end call) because the caller must drive its own vault PIN setup
 * ([com.kitsune.core.security.vault.VaultKeyProvider.initialize], which needs a `FragmentActivity`
 * this module has no access to) in between [decryptAndUnpack] and [importDatabaseAndImages].
 */
class TransferInUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: KitsuneDatabaseProvider,
    private val encryptedImageStore: EncryptedImageStore,
    private val transferCrypto: TransferCrypto,
    private val backendClient: KitsuneBackendClient,
    private val secureStorage: SecureStorage
) {
    /** Suspends, polling, until the old phone has finished uploading its blob. */
    suspend fun downloadBlob(transferId: String, pullToken: String): ByteArray = withContext(Dispatchers.IO) {
        var result = backendClient.downloadTransferBlob(transferId, pullToken).getOrThrow()
        while (!result.ready || result.blobBase64 == null) {
            delay(POLL_INTERVAL_MS)
            result = backendClient.downloadTransferBlob(transferId, pullToken).getOrThrow()
        }
        Base64.decode(result.blobBase64, Base64.NO_WRAP)
    }

    /** [transferKey] is the value parsed from the old phone's QR code ([TransferPairing]) —
     * required alongside [pin] to derive the decryption key (see [TransferCrypto]'s doc). Throws
     * [com.kitsune.core.security.transfer.TransferDecryptionException] on a wrong PIN/transfer
     * key, [ArchiveIntegrityException] if the CRC32 check fails after a successful decryption. */
    suspend fun decryptAndUnpack(envelope: ByteArray, pin: CharArray, transferKey: ByteArray): TransferArchive.UnpackedArchive =
        withContext(Dispatchers.Default) {
            TransferArchive.unpack(transferCrypto.decrypt(envelope, pin, transferKey))
        }

    /** Must be called with a passphrase freshly derived by this device's own
     * `VaultKeyProvider.initialize(activity, newPin)` (this device's real vault PIN going forward
     * — unrelated to the transfer PIN), and before any [KitsuneDatabaseProvider.open] call. */
    suspend fun importDatabaseAndImages(archive: TransferArchive.UnpackedArchive, newPassphrase: ByteArray) =
        withContext(Dispatchers.IO) {
            val tempDbFile = File(context.cacheDir, "transfer_import_${System.currentTimeMillis()}.db")
            try {
                tempDbFile.writeBytes(archive.dbBytes)
                databaseProvider.importPlaintextCopy(tempDbFile, newPassphrase)
            } finally {
                tempDbFile.delete()
            }

            databaseProvider.open(newPassphrase)
            archive.images.forEach { (id, bytes) -> encryptedImageStore.saveWithId(id, bytes) }

            // Carry over the old device's settings/preferences (profile, safe word, model/chat
            // prefs, personalization) — see SecureStorage.exportTransferablePrefs for exactly
            // what's included and why the vault/PIN-internal keys are deliberately excluded.
            secureStorage.importTransferablePrefs(archive.prefs)

            // A recovered account already has real chat history — it must never be treated as a
            // brand-new account for "first time" UI: mark the first-chat mini-arc marker as
            // already used (a sentinel that can never equal a real chat id, so ChatViewModel's
            // "if unset, this is the first-ever chat" check never fires again on this device) and
            // mark the welcome carousel as already seen (same SharedPreferences key HomeScreen.kt
            // checks) so neither shows up for an account that isn't actually new.
            secureStorage.putString(SecureStorage.KEY_FIRST_CHAT_ID, "transferred-account-no-first-chat")
            context.getSharedPreferences("kitsune_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("welcome_shown", true).apply()
        }

    /** "Test d'ouverture de la DB" (explicit user requirement) — a real sanity query against the
     * just-imported+re-encrypted database, kept separate from [importDatabaseAndImages] so the UI
     * can show a distinct verification step and a distinct failure mode. Throws (rather than
     * swallowing into a bare boolean) so the real SQLite exception reaches the caller's existing
     * catch/logging instead of being impossible to diagnose from a live device. */
    suspend fun verifyImportedDatabase() = withContext(Dispatchers.IO) {
        databaseProvider.requireOpen().openHelper.readableDatabase
            .query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
        Unit
    }

    /** Claims a fresh token pair for the recovered account, installs it, and — only once this
     * device has proven end to end that it works — tells the backend the transfer is complete,
     * which is what finally lets the old phone wipe itself. [completeTransfer] is retried a few
     * times on failure but never blocks success here: by this point the account data is already
     * safely imported on this device regardless of whether the old phone gets the wipe signal. */
    suspend fun claimAndComplete(transferId: String, pullToken: String): ClaimTransferResponse =
        withContext(Dispatchers.IO) {
            val claim = backendClient.claimTransfer(transferId, pullToken).getOrThrow()
            backendClient.installTransferredSession(claim.userId, claim.accessToken, claim.refreshToken)

            repeat(COMPLETE_RETRY_ATTEMPTS) { attempt ->
                val result = backendClient.completeTransfer(transferId, pullToken)
                if (result.isSuccess) return@withContext claim
                if (attempt < COMPLETE_RETRY_ATTEMPTS - 1) delay(POLL_INTERVAL_MS)
            }
            claim
        }
}
