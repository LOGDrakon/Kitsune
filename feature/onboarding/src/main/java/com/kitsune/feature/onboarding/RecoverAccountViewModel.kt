package com.kitsune.feature.onboarding

import android.content.Context
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.network.provider.ProviderStore
import com.kitsune.core.security.transfer.TransferDecryptionException
import com.kitsune.core.security.vault.VaultKeyProvider
import com.kitsune.core.security.vault.VaultUnlockResult
import com.kitsune.core.transfer.ArchiveIntegrityException
import com.kitsune.core.transfer.ImportBackupUseCase
import com.kitsune.core.transfer.NotABackupFileException
import com.kitsune.core.transfer.TransferArchive
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface RecoverAccountState {
    data object PickingFile : RecoverAccountState
    data object Reading : RecoverAccountState
    data class AwaitingPassphrase(val error: String? = null) : RecoverAccountState
    data object Decrypting : RecoverAccountState
    data object AwaitingVaultPin : RecoverAccountState
    data object Importing : RecoverAccountState
    data object Completed : RecoverAccountState
    data class Error(val message: String) : RecoverAccountState
}

/**
 * Restores an encrypted backup file on a fresh install (FEATURES.md section 2). Drives
 * [ImportBackupUseCase]'s steps, pausing at [RecoverAccountState.AwaitingVaultPin] to let the
 * screen collect THIS device's own vault PIN via [VaultKeyProvider.initialize], which needs the
 * `FragmentActivity` only the screen has.
 *
 * Known limitation: if the import fails AFTER [VaultKeyProvider.initialize] has succeeded, there is
 * no supported "undo" — [VaultKeyProvider] only exposes first-run setup. The error then tells the
 * user to restart the app, which starts a fresh process with no PIN configured yet.
 */
@HiltViewModel
class RecoverAccountViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importBackupUseCase: ImportBackupUseCase,
    private val vaultKeyProvider: VaultKeyProvider,
    private val providerStore: ProviderStore,
    private val backendClient: KitsuneBackendClient
) : ViewModel() {

    private val _state = MutableStateFlow<RecoverAccountState>(RecoverAccountState.PickingFile)
    val state: StateFlow<RecoverAccountState> = _state.asStateFlow()

    private var fileBytes: ByteArray? = null
    private var decryptedArchive: TransferArchive.UnpackedArchive? = null

    fun onFilePicked(uri: Uri) {
        _state.value = RecoverAccountState.Reading
        viewModelScope.launch {
            fileBytes = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
            }.getOrNull()
            _state.value = if (fileBytes == null) {
                RecoverAccountState.Error("Impossible de lire ce fichier.")
            } else {
                RecoverAccountState.AwaitingPassphrase()
            }
        }
    }

    fun submitPassphrase(passphrase: CharArray) {
        val bytes = fileBytes ?: return
        viewModelScope.launch {
            _state.value = RecoverAccountState.Decrypting
            try {
                decryptedArchive = importBackupUseCase.decryptAndUnpack(bytes, passphrase)
                _state.value = RecoverAccountState.AwaitingVaultPin
            } catch (e: NotABackupFileException) {
                _state.value = RecoverAccountState.Error(e.message ?: "Ce fichier n’est pas une sauvegarde Kitsune.")
            } catch (e: TransferDecryptionException) {
                _state.value = RecoverAccountState.AwaitingPassphrase(error = "Phrase secrète incorrecte, ou fichier endommagé.")
            } catch (e: ArchiveIntegrityException) {
                _state.value = RecoverAccountState.Error(e.message ?: "La sauvegarde semble corrompue.")
            } catch (e: Exception) {
                _state.value = RecoverAccountState.Error(e.message ?: "Erreur inattendue.")
            }
        }
    }

    fun restart() {
        fileBytes = null
        decryptedArchive = null
        _state.value = RecoverAccountState.PickingFile
    }

    fun submitVaultPin(activity: FragmentActivity, pin: CharArray) {
        val archive = decryptedArchive ?: return
        viewModelScope.launch {
            _state.value = RecoverAccountState.Importing
            when (val result = vaultKeyProvider.initialize(activity, pin)) {
                is VaultUnlockResult.Unlocked -> {
                    try {
                        importBackupUseCase.importDatabaseAndImages(archive, result.passphrase)
                        importBackupUseCase.verifyImportedDatabase()
                        // The restored settings were written straight to storage; the in-memory
                        // holders of providers and marketplace settings must pick them up.
                        providerStore.reload()
                        backendClient.reloadSettings()
                        _state.value = RecoverAccountState.Completed
                    } catch (e: Exception) {
                        _state.value = RecoverAccountState.Error(
                            (e.message ?: "Échec de l’import") + " Redémarrez l’application et réessayez."
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
