package com.kitsune.core.transfer

import android.content.Context
import android.util.Base64
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.security.storage.EncryptedImageStore
import com.kitsune.core.security.storage.SecureStorage
import com.kitsune.core.security.transfer.TransferCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val POLL_INTERVAL_MS = 2000L

sealed interface TransferOutState {
    data object Packaging : TransferOutState
    data object Uploading : TransferOutState
    data class WaitingForScan(val qrPayload: String) : TransferOutState
    data object NewDeviceImporting : TransferOutState
    data object Completed : TransferOutState
    data class Error(val message: String) : TransferOutState
}

/**
 * Old-phone side of account transfer (FEATURES.md section 2): exports the local database
 * (plaintext temp file, deleted immediately after use), decrypts every image locally and bundles
 * everything into a [TransferArchive], encrypts it with a key derived from both [pin] and a
 * freshly generated random transfer key ([TransferCrypto]) and uploads the opaque envelope to the
 * backend relay. The random key never leaves this device except inside the QR code the caller
 * displays — see [TransferCrypto]'s doc for why the PIN alone would be brute-forceable. The caller collects the
 * returned [Flow] on its own [kotlinx.coroutines.CoroutineScope] — cancelling that scope (e.g. the
 * user leaving the screen) stops the upload/poll loop naturally, no separate cancel API needed.
 * The caller is responsible for wiping local data once [TransferOutState.Completed] is reached —
 * this use case only proves the new device has a working copy, it never deletes anything itself.
 */
class TransferOutUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: KitsuneDatabaseProvider,
    private val encryptedImageStore: EncryptedImageStore,
    private val transferCrypto: TransferCrypto,
    private val backendClient: KitsuneBackendClient,
    private val secureStorage: SecureStorage
) {
    fun run(pin: CharArray): Flow<TransferOutState> = flow {
        emit(TransferOutState.Packaging)

        val tempDbFile = File(context.cacheDir, "transfer_export_${System.currentTimeMillis()}.db")
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
        // Random half of the transfer key — never uploaded, only embedded in the QR code below
        // (see TransferCrypto's doc for why the PIN alone isn't enough entropy).
        val transferKey = transferCrypto.generateTransferKey()
        val envelope = transferCrypto.encrypt(archive, pin, transferKey)
        val envelopeBase64 = Base64.encodeToString(envelope, Base64.NO_WRAP)

        emit(TransferOutState.Uploading)

        val created = backendClient.createTransfer().getOrElse {
            emit(TransferOutState.Error(it.message ?: "Impossible de créer la session de transfert"))
            return@flow
        }
        backendClient.uploadTransferBlob(created.transferId, created.uploadToken, envelopeBase64).getOrElse {
            emit(TransferOutState.Error(it.message ?: "Échec de l'envoi des données"))
            return@flow
        }

        val qrPayload = TransferPairing.buildQrPayload(created.transferId, created.pullToken, transferKey)
        emit(TransferOutState.WaitingForScan(qrPayload))

        var lastStatus = "UPLOADED"
        while (lastStatus != "COMPLETED") {
            delay(POLL_INTERVAL_MS)
            val statusResult = backendClient.getTransferStatus(created.transferId, created.uploadToken)
            val status = statusResult.getOrElse {
                emit(TransferOutState.Error(it.message ?: "La session de transfert a expiré"))
                return@flow
            }.status

            if (status != lastStatus) {
                lastStatus = status
                when (status) {
                    "CLAIMED" -> emit(TransferOutState.NewDeviceImporting)
                    "COMPLETED" -> emit(TransferOutState.Completed)
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
