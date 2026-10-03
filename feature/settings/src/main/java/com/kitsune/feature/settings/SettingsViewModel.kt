package com.kitsune.feature.settings

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsune.core.backend.AccountLockReason
import com.kitsune.core.backend.CreatorFollowsManager
import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.backend.UserMessageManager
import com.kitsune.core.data.local.database.DecoyNotesDatabase
import com.kitsune.core.data.local.database.DecoyNotesDatabaseProvider
import com.kitsune.core.data.local.database.KitsuneDatabase
import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.diagnostics.SubmitBugReportUseCase
import com.kitsune.core.moderation.safeword.SafeWordManager
import com.kitsune.core.network.preferences.NetworkPreferences
import com.kitsune.core.security.locale.AppLanguage
import com.kitsune.core.security.locale.AppLanguageManager
import com.kitsune.core.security.lock.AutoLockManager
import com.kitsune.core.security.model.VaultSecurityMode
import com.kitsune.core.security.profile.UserProfile
import com.kitsune.core.security.profile.UserProfileStore
import com.kitsune.core.security.storage.EncryptedImageStore
import com.kitsune.core.security.storage.SecureStorage
import com.kitsune.core.security.taste.StoryTasteStore
import com.kitsune.core.security.vault.VaultKeyProvider
import com.kitsune.core.security.vault.VaultModeChangeResult
import com.kitsune.core.transfer.TransferOutState
import com.kitsune.core.transfer.TransferOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SECONDS_PER_MINUTE = 60
private const val MIN_PIN_LENGTH = 4
private const val MIN_TRANSFER_PIN_LENGTH = 6
private const val BUG_REPORT_MIN_DIALOG_MS = 4000L

sealed interface SecurityActionResult {
    data object Success : SecurityActionResult
    data class Error(val message: String) : SecurityActionResult
}

sealed interface DeleteAccountState {
    data object Idle : DeleteAccountState
    data object InProgress : DeleteAccountState
    data object Success : DeleteAccountState
    data class Error(val message: String) : DeleteAccountState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val networkPreferences: NetworkPreferences,
    private val autoLockManager: AutoLockManager,
    private val secureStorage: SecureStorage,
    private val storyTasteStore: StoryTasteStore,
    private val safeWordManager: SafeWordManager,
    private val userProfileStore: UserProfileStore,
    private val submitBugReportUseCase: SubmitBugReportUseCase,
    private val vaultKeyProvider: VaultKeyProvider,
    private val databaseProvider: KitsuneDatabaseProvider,
    private val decoyNotesDatabaseProvider: DecoyNotesDatabaseProvider,
    private val encryptedImageStore: EncryptedImageStore,
    private val backendClient: KitsuneBackendClient,
    private val appLanguageManager: AppLanguageManager,
    private val transferOutUseCase: TransferOutUseCase,
    private val userMessageManager: UserMessageManager,
    private val creatorFollowsManager: CreatorFollowsManager
) : ViewModel() {

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** Unread count for the "Mes messages" entry — see [UserMessageManager]. */
    val unreadMessageCount: StateFlow<Int> = userMessageManager.unreadCount

    /** New-listings badge for the "Mes abonnements" entry — see [CreatorFollowsManager]. */
    val newFollowsCount: StateFlow<Int> = creatorFollowsManager.newListingsCount

    /** Non-null (BANNED or FROZEN) shows a persistent notice here — a banned account keeps normal
     * settings access (this screen) but content generation is blocked elsewhere; see BUGS.md. */
    val accountLockReason: StateFlow<AccountLockReason?> = backendClient.accountLockReason
    val banReason: StateFlow<String?> = backendClient.banReason

    init {
        viewModelScope.launch { userMessageManager.refresh() }
        viewModelScope.launch { creatorFollowsManager.refresh() }
    }

    /** True while a bug report is being redacted, encrypted and sent — see [generateBugReport]. */
    private val _bugReportSending = MutableStateFlow(false)
    val bugReportSending: StateFlow<Boolean> = _bugReportSending.asStateFlow()

    private val _securityMode = MutableStateFlow(VaultSecurityMode.ALL)
    val securityMode: StateFlow<VaultSecurityMode> = _securityMode.asStateFlow()

    private val _isChangingSecurityMode = MutableStateFlow(false)
    val isChangingSecurityMode: StateFlow<Boolean> = _isChangingSecurityMode.asStateFlow()

    private val _securityActionResult = MutableStateFlow<SecurityActionResult?>(null)
    val securityActionResult: StateFlow<SecurityActionResult?> = _securityActionResult.asStateFlow()

    private val _panicPinResult = MutableStateFlow<SecurityActionResult?>(null)
    val panicPinResult: StateFlow<SecurityActionResult?> = _panicPinResult.asStateFlow()

    private val _usernameAvailable = MutableStateFlow<Boolean?>(null)
    val usernameAvailable: StateFlow<Boolean?> = _usernameAvailable.asStateFlow()
    private val _isSettingUsername = MutableStateFlow(false)
    val isSettingUsername: StateFlow<Boolean> = _isSettingUsername.asStateFlow()

    private val _deleteAccountState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteAccountState: StateFlow<DeleteAccountState> = _deleteAccountState.asStateFlow()

    private val _transferOutState = MutableStateFlow<TransferOutState?>(null)
    val transferOutState: StateFlow<TransferOutState?> = _transferOutState.asStateFlow()

    init {
        viewModelScope.launch { _securityMode.value = vaultKeyProvider.getSecurityMode() }
        viewModelScope.launch {
            backendClient.creditBalance.collect { balance ->
                _state.value = _state.value.copy(creditBalance = balance)
            }
        }
        viewModelScope.launch {
            backendClient.refreshBalance()
            backendClient.getUserProfile().onSuccess { profile ->
                _state.value = _state.value.copy(username = profile.username)
            }
        }
    }

    fun checkUsername(username: String) {
        if (username.length < 3) { _usernameAvailable.value = false; return }
        viewModelScope.launch {
            backendClient.checkUsername(username).onSuccess { response ->
                _usernameAvailable.value = response.available
            }.onFailure { _usernameAvailable.value = null }
        }
    }

    fun setUsername(username: String) {
        _isSettingUsername.value = true
        viewModelScope.launch {
            backendClient.setUsername(username).onSuccess {
                _state.value = _state.value.copy(username = username)
            }
            _isSettingUsername.value = false
        }
    }

    /** Redacts, encrypts and sends a bug report automatically (FEATURES.md section 9). [subject]/
     * [description] are the user's own account of the problem — replaces the old "traçage avancé"
     * system, which returned almost no useful data in practice. Keeps [_bugReportSending] visible
     * for at least [BUG_REPORT_MIN_DIALOG_MS] regardless of how fast the network call completes,
     * so the privacy reassurance dialog actually gets read. */
    fun generateBugReport(subject: String, description: String) {
        if (_bugReportSending.value) return
        viewModelScope.launch {
            _bugReportSending.value = true
            val minDisplay = launch { delay(BUG_REPORT_MIN_DIALOG_MS) }
            submitBugReportUseCase(subject, description, chatId = null)
            minDisplay.join()
            _bugReportSending.value = false
        }
    }
    fun dismissSecurityActionResult() { _securityActionResult.value = null }
    fun dismissPanicPinResult() { _panicPinResult.value = null }

    fun disableBiometric(activity: FragmentActivity, currentPin: CharArray) =
        changeSecurityMode(activity, VaultSecurityMode.PIN_ONLY, currentPin = currentPin)
    fun disablePin(activity: FragmentActivity, currentPin: CharArray) =
        changeSecurityMode(activity, VaultSecurityMode.BIOMETRIC_ONLY, currentPin = currentPin)
    fun enableBiometric(activity: FragmentActivity, currentPin: CharArray) =
        changeSecurityMode(activity, VaultSecurityMode.ALL, currentPin = currentPin)
    fun enablePin(activity: FragmentActivity, newPin: CharArray) =
        changeSecurityMode(activity, VaultSecurityMode.ALL, newPin = newPin)

    private fun changeSecurityMode(
        activity: FragmentActivity,
        targetMode: VaultSecurityMode,
        currentPin: CharArray? = null,
        newPin: CharArray? = null
    ) {
        if (_isChangingSecurityMode.value) return
        viewModelScope.launch {
            _isChangingSecurityMode.value = true
            when (val result = vaultKeyProvider.prepareSecurityModeChange(activity, targetMode, currentPin, newPin)) {
                is VaultModeChangeResult.Prepared -> {
                    try {
                        databaseProvider.rekey(result.newPassphrase)
                        vaultKeyProvider.commitSecurityMode(targetMode)
                        _securityMode.value = targetMode
                        _securityActionResult.value = SecurityActionResult.Success
                    } catch (e: Exception) {
                        _securityActionResult.value = SecurityActionResult.Error(
                            "Le changement a échoué (${e.message}) — le mode de sécurité actuel n'a pas été modifié."
                        )
                    }
                }
                VaultModeChangeResult.WrongPin -> _securityActionResult.value = SecurityActionResult.Error("Code PIN incorrect.")
                VaultModeChangeResult.MissingNewPin -> _securityActionResult.value = SecurityActionResult.Error("Erreur interne : nouveau code manquant.")
                is VaultModeChangeResult.BiometricFailed -> _securityActionResult.value = SecurityActionResult.Error(result.message)
                VaultModeChangeResult.BiometricCancelled -> Unit
            }
            _isChangingSecurityMode.value = false
        }
    }

    fun setPanicPin(pin: CharArray, confirmPin: CharArray) {
        if (pin.size < MIN_PIN_LENGTH) {
            _panicPinResult.value = SecurityActionResult.Error("Le code doit contenir au moins $MIN_PIN_LENGTH chiffres.")
            return
        }
        if (!pin.contentEquals(confirmPin)) {
            _panicPinResult.value = SecurityActionResult.Error("Les deux codes ne correspondent pas.")
            return
        }
        viewModelScope.launch {
            try {
                val result = vaultKeyProvider.setPanicPin(pin)
                val oldPassphrase = result.oldPassphrase
                val decoyDbExists = appContext.getDatabasePath(DecoyNotesDatabase.DATABASE_NAME).exists()
                if (oldPassphrase != null && decoyDbExists) {
                    // The panic PIN is being replaced (not first-time setup) and the decoy notes
                    // database has actually been created before — rekey it so existing decoy notes
                    // stay readable under the new PIN, exactly like a VaultSecurityMode change.
                    if (decoyNotesDatabaseProvider.isOpen()) decoyNotesDatabaseProvider.close()
                    decoyNotesDatabaseProvider.open(oldPassphrase)
                    decoyNotesDatabaseProvider.rekey(result.newPassphrase)
                    decoyNotesDatabaseProvider.close()
                }
                _panicPinResult.value = SecurityActionResult.Success
            } catch (e: Exception) {
                _panicPinResult.value = SecurityActionResult.Error(e.message ?: "Erreur")
            }
        }
    }

    private fun loadState(): SettingsUiState {
        val userProfile = userProfileStore.get()
        return SettingsUiState(
            creditBalance = backendClient.creditBalance.value,
            backendUserId = backendClient.getUserId().orEmpty(),
            autoLockMinutes = (autoLockManager.timeoutSeconds / SECONDS_PER_MINUTE).coerceAtLeast(1),
            flagSecureEnabled = secureStorage.getInt(SecureStorage.KEY_FLAG_SECURE_ENABLED, 1) == 1,
            discreetModeEnabled = secureStorage.getInt(SecureStorage.KEY_DISCREET_MODE_ENABLED, 0) == 1,
            temperature = networkPreferences.getDefaultTemperature().toFloat(),
            safeWord = safeWordManager.getSafeWord().orEmpty(),
            userFirstName = userProfile.firstName,
            userLastName = userProfile.lastName,
            userPronoun = userProfile.pronoun,
            userAge = userProfile.age,
            userPhysicalDescription = userProfile.physicalDescription,
            userSexualOrientation = userProfile.sexualOrientation,
            language = appLanguageManager.getSelectedLanguage(),
            autoRecapEnabled = secureStorage.getInt(SecureStorage.KEY_AUTO_RECAP_ENABLED, 1) == 1,
            customStylePrompt = secureStorage.getString(SecureStorage.KEY_CUSTOM_STYLE_PROMPT) ?: "",
            neverWrite = storyTasteStore.get().neverWrite
        )
    }

    fun setLanguage(language: AppLanguage) {
        appLanguageManager.setSelectedLanguage(language)
        _state.value = _state.value.copy(language = language)
    }

    private fun updateUserProfile(transform: (UserProfile) -> UserProfile) {
        val current = UserProfile(
            firstName = _state.value.userFirstName,
            lastName = _state.value.userLastName,
            pronoun = _state.value.userPronoun,
            age = _state.value.userAge,
            physicalDescription = _state.value.userPhysicalDescription,
            sexualOrientation = _state.value.userSexualOrientation
        )
        val updated = transform(current)
        userProfileStore.save(updated)
        _state.value = _state.value.copy(
            userFirstName = updated.firstName,
            userLastName = updated.lastName,
            userPronoun = updated.pronoun,
            userAge = updated.age,
            userPhysicalDescription = updated.physicalDescription,
            userSexualOrientation = updated.sexualOrientation
        )
    }

    fun setUserFirstName(value: String) = updateUserProfile { it.copy(firstName = value) }
    fun setUserLastName(value: String) = updateUserProfile { it.copy(lastName = value) }
    fun setUserPronoun(value: String) = updateUserProfile { it.copy(pronoun = value) }
    fun setUserAge(value: String) = updateUserProfile { it.copy(age = value) }
    fun setUserPhysicalDescription(value: String) = updateUserProfile { it.copy(physicalDescription = value) }
    fun setUserSexualOrientation(value: String) = updateUserProfile { it.copy(sexualOrientation = value) }

    fun setAutoLockMinutes(minutes: Int) {
        val clamped = minutes.coerceAtLeast(1)
        autoLockManager.timeoutSeconds = clamped * SECONDS_PER_MINUTE
        _state.value = _state.value.copy(autoLockMinutes = clamped)
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        secureStorage.putInt(SecureStorage.KEY_FLAG_SECURE_ENABLED, if (enabled) 1 else 0)
        _state.value = _state.value.copy(flagSecureEnabled = enabled)
    }

    fun setDiscreetModeEnabled(enabled: Boolean) {
        secureStorage.putInt(SecureStorage.KEY_DISCREET_MODE_ENABLED, if (enabled) 1 else 0)
        _state.value = _state.value.copy(discreetModeEnabled = enabled)
    }

    fun setTemperature(value: Float) {
        networkPreferences.setDefaultTemperature(value.toDouble())
        _state.value = _state.value.copy(temperature = value)
    }

    fun setSafeWord(value: String) {
        if (value.isBlank()) safeWordManager.clearSafeWord() else safeWordManager.setSafeWord(value)
        _state.value = _state.value.copy(safeWord = value)
    }

    fun setAutoRecapEnabled(enabled: Boolean) {
        secureStorage.putInt(SecureStorage.KEY_AUTO_RECAP_ENABLED, if (enabled) 1 else 0)
        _state.value = _state.value.copy(autoRecapEnabled = enabled)
    }

    fun setCustomStylePrompt(value: String) {
        if (value.isBlank()) secureStorage.remove(SecureStorage.KEY_CUSTOM_STYLE_PROMPT)
        else secureStorage.putString(SecureStorage.KEY_CUSTOM_STYLE_PROMPT, value)
        _state.value = _state.value.copy(customStylePrompt = value)
    }

    /**
     * The one thing the app lets a player say about their own taste rather than their identity.
     *
     * Persisted on every keystroke like the other free-text settings; it reaches the model inside the
     * style contract, placed after the presets and phrased as an override so no experience mode can
     * be read as licence to ignore it.
     */
    fun setNeverWrite(value: String) {
        storyTasteStore.save(storyTasteStore.get().copy(neverWrite = value))
        _state.value = _state.value.copy(neverWrite = value)
    }

    fun dismissDeleteAccountResult() {
        _deleteAccountState.value = DeleteAccountState.Idle
    }

    /** Closes and deletes the local encrypted database + every encrypted image, then logs out —
     * shared by [deleteAccountAndAllData] (which also re-registers a fresh account afterward) and
     * the account-transfer flow's completion handler (which deliberately does NOT — the account
     * now lives on the new device). */
    private suspend fun wipeLocalDataAndLogout() {
        databaseProvider.close()
        appContext.deleteDatabase(KitsuneDatabase.DATABASE_NAME)
        decoyNotesDatabaseProvider.close()
        appContext.deleteDatabase(DecoyNotesDatabase.DATABASE_NAME)
        encryptedImageStore.deleteAll()
        backendClient.logout()
    }

    /**
     * Additionally resets the vault (PIN + wrapped database key + panic PIN + security mode) and
     * the age-verification flag — used ONLY after a successful account transfer, never after
     * [deleteAccountAndAllData] (which deliberately keeps the PIN/vault so the existing unlock
     * flow can reopen the freshly-recreated empty database).
     *
     * Without this, [com.kitsune.core.security.vault.VaultKeyProvider.isInitialized] would still
     * report true after a transfer (the PIN/wrapped key are untouched by [wipeLocalDataAndLogout]
     * alone) — `AppEntryViewModel`'s cold-start routing checks exactly that flag, so if the app
     * process were killed right after a transfer and relaunched, it would route to the normal LOCK
     * screen instead of onboarding; unlocking with the old PIN there would reopen the now-empty
     * database with no backend session, a confusing dead end. [VaultKeyProvider.initialize] safely
     * overwrites all of this unconditionally (verified in VaultKeyProviderImpl — no "already
     * initialized" special case), so there is nothing destructive about resetting it here.
     */
    private fun resetVaultAndOnboardingState() {
        secureStorage.remove(SecureStorage.KEY_WRAPPED_DB_KEY)
        secureStorage.remove(SecureStorage.KEY_DB_KEY_SOFTWARE)
        secureStorage.remove(SecureStorage.KEY_VAULT_SECURITY_MODE)
        secureStorage.remove(SecureStorage.KEY_PIN_SALT_REAL)
        secureStorage.remove(SecureStorage.KEY_PIN_HASH_REAL)
        secureStorage.remove(SecureStorage.KEY_PIN_SALT_PANIC)
        secureStorage.remove(SecureStorage.KEY_PIN_HASH_PANIC)
        secureStorage.remove(SecureStorage.KEY_DECOY_NOTES_DB_KEY)
        secureStorage.remove(SecureStorage.KEY_AGE_VERIFIED_ADULT)
    }

    /**
     * Deletes the server-side account (credits, purchases, marketplace listings — everything) and
     * then wipes all local data (encrypted Room database, encrypted images). Server deletion runs
     * first and local data is only wiped once it succeeds, so a network failure never leaves the
     * user with a deleted account but no way to prove/undo it, nor a wiped device with an account
     * still alive server-side. On success a fresh anonymous account is registered immediately (the
     * user already passed age verification this session, no need to redo that screen) so the app
     * has valid auth again — the caller is expected to then route back through [com.kitsune.core.security.model.VaultSecurityMode]'s
     * unlock screen, which reopens (recreates) the now-empty database.
     */
    fun deleteAccountAndAllData() {
        if (_deleteAccountState.value == DeleteAccountState.InProgress) return
        viewModelScope.launch {
            _deleteAccountState.value = DeleteAccountState.InProgress
            backendClient.deleteAccount()
                .onSuccess {
                    wipeLocalDataAndLogout()
                    backendClient.registerAnonymous()
                    _deleteAccountState.value = DeleteAccountState.Success
                }
                .onFailure { e ->
                    _deleteAccountState.value = DeleteAccountState.Error(
                        e.message ?: "La suppression a échoué — vérifiez votre connexion et réessayez."
                    )
                }
        }
    }

    private var transferOutJob: Job? = null

    /** Cancels any in-flight transfer (upload/poll loop) and hides the dialog — safe to call at
     * any point the UI actually exposes a cancel/dismiss button, since [TransferOutState.Completed]
     * is deliberately never shown with one (the wipe it triggers must not be interruptible). */
    fun dismissTransferOut() {
        transferOutJob?.cancel()
        transferOutJob = null
        _transferOutState.value = null
    }

    /**
     * Starts the old-phone side of an account transfer (FEATURES.md section 2): packages+encrypts
     * local data with [pin] and uploads it, exposing progress via [transferOutState] up to a QR
     * code the new phone scans. Once the new phone has proven the data actually works there
     * (decrypted, CRC-verified, database re-opened) and claimed a fresh session, [TransferOutState.Completed]
     * fires and this device wipes its own local data — same effect as [deleteAccountAndAllData]'s
     * wipe, but WITHOUT deleting the server-side account or registering a new one: the account now
     * lives on the new device, this one goes back to a blank slate.
     */
    fun startAccountTransfer(pin: CharArray) {
        if (pin.size < MIN_TRANSFER_PIN_LENGTH) {
            _transferOutState.value = TransferOutState.Error("Le code doit contenir au moins $MIN_TRANSFER_PIN_LENGTH chiffres.")
            return
        }
        transferOutJob = viewModelScope.launch {
            transferOutUseCase.run(pin).collect { state ->
                // Wipe BEFORE publishing Completed — the UI navigates away as soon as it observes
                // Completed, which (via NavBackStackEntry scoping) can cancel this ViewModel's
                // scope; the wipe must be finished by then, not still running underneath it.
                if (state is TransferOutState.Completed) {
                    wipeLocalDataAndLogout()
                    resetVaultAndOnboardingState()
                }
                _transferOutState.value = state
            }
        }
    }
}