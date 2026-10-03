package com.kitsune.feature.onboarding

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.security.transfer.TransferDecryptionException
import com.kitsune.core.security.vault.VaultKeyProvider
import com.kitsune.core.security.vault.VaultUnlockResult
import com.kitsune.core.transfer.ArchiveIntegrityException
import com.kitsune.core.transfer.ParsedTransferQr
import com.kitsune.core.transfer.TransferArchive
import com.kitsune.core.transfer.TransferInUseCase
import com.kitsune.core.transfer.TransferPairing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RecoverAccountState {
    data object Scanning : RecoverAccountState
    data object Downloading : RecoverAccountState
    data class AwaitingTransferPin(val error: String? = null) : RecoverAccountState
    data object Decrypting : RecoverAccountState
    data object AwaitingVaultPin : RecoverAccountState
    data object Importing : RecoverAccountState
    data object Completed : RecoverAccountState
    data class Error(val message: String) : RecoverAccountState
}

/**
 * New-phone side of account transfer (FEATURES.md section 2). Drives [TransferInUseCase]'s
 * discrete steps, pausing at [RecoverAccountState.AwaitingVaultPin] to let the screen collect
 * THIS device's own new vault PIN (unrelated to the transfer PIN) via [VaultKeyProvider.initialize],
 * which needs the `FragmentActivity` only the screen has access to.
 *
 * Known limitation: if the import/verify/claim sequence fails AFTER [VaultKeyProvider.initialize]
 * has already succeeded (i.e. this device's vault PIN is set but nothing was imported into it
 * yet), there's no supported "undo" — [VaultKeyProvider] only exposes first-run setup, not a
 * reset. The [RecoverAccountState.Error] shown in that case tells the user to restart the app and
 * try again, which starts a fresh process with no PIN configured yet.
 */
@HiltViewModel
class RecoverAccountViewModel @Inject constructor(
    private val transferInUseCase: TransferInUseCase,
    private val vaultKeyProvider: VaultKeyProvider
) : ViewModel() {

    private val _state = MutableStateFlow<RecoverAccountState>(RecoverAccountState.Scanning)
    val state: StateFlow<RecoverAccountState> = _state.asStateFlow()

    private var parsedQr: ParsedTransferQr? = null
    private var encryptedEnvelope: ByteArray? = null
    private var decryptedArchive: TransferArchive.UnpackedArchive? = null

    fun onQrScanned(raw: String) {
        if (_state.value != RecoverAccountState.Scanning) return
        val parsed = TransferPairing.parseQrPayload(raw)
        if (parsed == null) {
            _state.value = RecoverAccountState.Error("Ce code QR ne provient pas de Kitsune.")
            return
        }
        parsedQr = parsed
        _state.value = RecoverAccountState.Downloading
        viewModelScope.launch {
            try {
                encryptedEnvelope = transferInUseCase.downloadBlob(parsed.transferId, parsed.pullToken)
                _state.value = RecoverAccountState.AwaitingTransferPin()
            } catch (e: Exception) {
                _state.value = RecoverAccountState.Error(e.message ?: "Échec du téléchargement — vérifiez votre connexion.")
            }
        }
    }

    fun submitTransferPin(pin: CharArray) {
        val envelope = encryptedEnvelope ?: return
        val transferKey = parsedQr?.transferKey ?: return
        viewModelScope.launch {
            _state.value = RecoverAccountState.Decrypting
            try {
                decryptedArchive = transferInUseCase.decryptAndUnpack(envelope, pin, transferKey)
                _state.value = RecoverAccountState.AwaitingVaultPin
            } catch (e: TransferDecryptionException) {
                _state.value = RecoverAccountState.AwaitingTransferPin(error = "Code PIN incorrect.")
            } catch (e: ArchiveIntegrityException) {
                _state.value = RecoverAccountState.Error(e.message ?: "Les données transférées semblent corrompues.")
            } catch (e: Exception) {
                _state.value = RecoverAccountState.Error(e.message ?: "Erreur inattendue.")
            }
        }
    }

    fun submitVaultPin(activity: FragmentActivity, pin: CharArray) {
        val archive = decryptedArchive ?: return
        val parsed = parsedQr ?: return
        viewModelScope.launch {
            _state.value = RecoverAccountState.Importing
            when (val result = vaultKeyProvider.initialize(activity, pin)) {
                is VaultUnlockResult.Unlocked -> {
                    try {
                        transferInUseCase.importDatabaseAndImages(archive, result.passphrase)
                        transferInUseCase.verifyImportedDatabase()
                        transferInUseCase.claimAndComplete(parsed.transferId, parsed.pullToken)
                        _state.value = RecoverAccountState.Completed
                    } catch (e: Exception) {
                        _state.value = RecoverAccountState.Error(
                            (e.message ?: "Échec de l'import") + " Redémarrez l'application et réessayez."
                        )
                    }
                }
                is VaultUnlockResult.BiometricFailed -> _state.value = RecoverAccountState.Error(result.message)
                VaultUnlockResult.BiometricCancelled -> _state.value = RecoverAccountState.AwaitingVaultPin
                VaultUnlockResult.WrongPin, VaultUnlockResult.NotInitialized ->
                    _state.value = RecoverAccountState.Error("Erreur inattendue lors de la configuration.")
            }
        }
    }
}
