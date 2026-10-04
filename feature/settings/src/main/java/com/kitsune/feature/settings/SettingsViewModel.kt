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
import com.kitsune.core.network.provider.ProviderStore
import com.kitsune.core.transfer.BackupFormat
import com.kitsune.core.transfer.ExportBackupUseCase
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SECONDS_PER_MINUTE = 60
private const val MIN_PIN_LENGTH = 4
private const val BUG_REPORT_MIN_DIALOG_MS = 4000L

sealed interface SecurityActionResult {
    data object Success : SecurityActionResult
    data class Error(val message: String) : SecurityActionResult
}

sealed interface BackupExportState {
    data object Exporting : BackupExportState
    data object Done : BackupExportState
    data class Error(val message: String) : BackupExportState
}

sealed interface MarketplaceAccountState {
    data object Idle : MarketplaceAccountState
    data object Deleting : MarketplaceAccountState
    data object Deleted : MarketplaceAccountState
    data class Error(val message: String) : MarketplaceAccountState
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
    private val exportBackupUseCase: ExportBackupUseCase,
    private val providerStore: ProviderStore,
    private val userMessageManager: UserMessageManager,
    private val creatorFollowsManager: CreatorFollowsManager,
    private val breakReminder: com.kitsune.core.security.wellbeing.BreakReminder
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

    /** The user's AI providers, for the summary row that opens the providers screen. */
    val providers = providerStore.providers

    /** Marketplace switch and server address — see [KitsuneBackendClient]. */
    val marketplaceEnabled: StateFlow<Boolean> = backendClient.enabled
    val marketplaceServerUrl: StateFlow<String> = backendClient.serverUrl
    val marketplaceAuthState = backendClient.authState

    private val _marketplaceAccountState = MutableStateFlow<MarketplaceAccountState>(MarketplaceAccountState.Idle)
    val marketplaceAccountState: StateFlow<MarketplaceAccountState> = _marketplaceAccountState.asStateFlow()

    private val _backupExportState = MutableStateFlow<BackupExportState?>(null)
    val backupExportState: StateFlow<BackupExportState?> = _backupExportState.asStateFlow()

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

    init {
        viewModelScope.launch { _securityMode.value = vaultKeyProvider.getSecurityMode() }
        // Only an existing marketplace session is queried: opening Settings must never be what
        // registers an account on a server.
        if (backendClient.isEnabled() && backendClient.isAuthenticated()) {
            viewModelScope.launch {
                backendClient.getUserProfile().onSuccess { profile ->
                    _state.value = _state.value.copy(username = profile.username)
                }
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

    /** Builds a redacted bug report and opens the share sheet with it (FEATURES.md section 9).
     * [subject]/[description] are the user's own account of the problem. Keeps [_bugReportSending]
     * visible for at least [BUG_REPORT_MIN_DIALOG_MS] so the privacy reassurance dialog is read. */
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
            breakReminderMinutes = breakReminder.intervalMinutes(),
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

    fun setBreakReminderMinutes(minutes: Int) {
        breakReminder.setIntervalMinutes(minutes)
        _state.value = _state.value.copy(breakReminderMinutes = minutes)
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

    /** Closes and deletes the local encrypted database + every encrypted image. The marketplace
     * session is forgotten too, but the account itself is left on its server — deleting it is a
     * separate action ([deleteMarketplaceAccount]), since it needs the network and this must not. */
    private suspend fun wipeLocalData() {
        databaseProvider.close()
        appContext.deleteDatabase(KitsuneDatabase.DATABASE_NAME)
        decoyNotesDatabaseProvider.close()
        appContext.deleteDatabase(DecoyNotesDatabase.DATABASE_NAME)
        encryptedImageStore.deleteAll()
        backendClient.logout()
    }

    /**
     * Wipes every story, persona, universe and image on this device. The vault PIN is kept, so the
     * caller routes back through the unlock screen, which recreates an empty database. AI providers
     * and settings are kept as well: they are not stories, and losing an API key to a "delete my
     * data" button would be a nasty surprise.
     */
    fun deleteAccountAndAllData() {
        if (_deleteAccountState.value == DeleteAccountState.InProgress) return
        viewModelScope.launch {
            _deleteAccountState.value = DeleteAccountState.InProgress
            runCatching { wipeLocalData() }
                .onSuccess { _deleteAccountState.value = DeleteAccountState.Success }
                .onFailure { e -> _deleteAccountState.value = DeleteAccountState.Error(e.message ?: "La suppression a échoué.") }
        }
    }

    fun setMarketplaceEnabled(enabled: Boolean) = backendClient.setEnabled(enabled)

    /** Returns false for an unusable address, so the field can say so. */
    fun setMarketplaceServerUrl(url: String): Boolean {
        val ok = backendClient.setServerUrl(url)
        if (ok) _state.value = _state.value.copy(username = null)
        return ok
    }

    fun resetMarketplaceServerUrl() {
        backendClient.resetServerUrl()
        _state.value = _state.value.copy(username = null)
    }

    fun isDefaultMarketplaceServer(): Boolean = backendClient.isDefaultServer()

    /** Deletes the account on the current marketplace server: listings, reviews, follows. Local data
     * is untouched — it never lived there. */
    fun deleteMarketplaceAccount() {
        if (_marketplaceAccountState.value == MarketplaceAccountState.Deleting) return
        viewModelScope.launch {
            _marketplaceAccountState.value = MarketplaceAccountState.Deleting
            _marketplaceAccountState.value = backendClient.deleteAccount().fold(
                onSuccess = {
                    _state.value = _state.value.copy(username = null)
                    MarketplaceAccountState.Deleted
                },
                onFailure = { MarketplaceAccountState.Error(it.message ?: "La suppression a échoué.") }
            )
        }
    }

    fun dismissMarketplaceAccountState() {
        _marketplaceAccountState.value = MarketplaceAccountState.Idle
    }

    /** Validates the passphrase before the file picker opens, so the user never creates an empty
     * file for nothing. Returns an error message, or null when the passphrase is acceptable. */
    fun validateBackupPassphrase(passphrase: String, confirm: String): String? = when {
        passphrase.length < BackupFormat.MIN_PASSPHRASE_LENGTH ->
            "La phrase secrète doit contenir au moins ${BackupFormat.MIN_PASSPHRASE_LENGTH} caractères."
        passphrase != confirm -> "Les deux phrases ne correspondent pas."
        else -> null
    }

    /** Writes an encrypted backup of everything local to [uri] (FEATURES.md section 2). */
    fun exportBackup(passphrase: CharArray, uri: Uri) {
        if (_backupExportState.value == BackupExportState.Exporting) return
        viewModelScope.launch {
            _backupExportState.value = BackupExportState.Exporting
            _backupExportState.value = runCatching {
                val output = appContext.contentResolver.openOutputStream(uri) ?: error("Impossible d’écrire ce fichier.")
                output.use { exportBackupUseCase(passphrase, it) }
            }.fold(
                onSuccess = { BackupExportState.Done },
                onFailure = { BackupExportState.Error(it.message ?: "L’export a échoué.") }
            )
            passphrase.fill(' ')
        }
    }

    fun dismissBackupExport() {
        _backupExportState.value = null
    }
}
