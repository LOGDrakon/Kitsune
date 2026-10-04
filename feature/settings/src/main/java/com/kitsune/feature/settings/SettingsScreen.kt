package com.kitsune.feature.settings

import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.KitsuneCard
import com.kitsune.core.designsystem.component.KitsunePage
import com.kitsune.core.designsystem.component.PageTitle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.backend.AccountLockReason
import com.kitsune.core.designsystem.BugReportPrivacyDialog
import com.kitsune.core.security.locale.AppLanguage
import com.kitsune.core.security.model.VaultSecurityMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kitsune.core.designsystem.component.KitsuneRow
import com.kitsune.core.transfer.BackupFormat
import androidx.compose.material3.Slider
import java.util.Locale
import com.kitsune.feature.settings.R

private const val DATA_TRANSPARENCY_URL = "https://github.com/LOGDrakon/Kitsune/blob/main/PRIVACY.md"
private const val SOURCE_CODE_URL = "https://github.com/LOGDrakon/Kitsune"
private const val ISSUES_URL = "https://github.com/LOGDrakon/Kitsune/issues"
private const val SERVER_SOURCE_URL = "https://github.com/LOGDrakon/Kitsune-Server"
private const val DONATE_URL = "https://opencollective.com/kitsuneapp"

/**
 * Groups related settings under a titled block.
 *
 * Every section of this screen goes through here, which is what made restyling Settings for v2 a
 * change to one function rather than to two thousand lines: it is now a [KitsuneCard] with the design
 * system's eyebrow heading, so a settings section and a story row are visibly the same kind of object.
 *
 * The icon is tinted [KitsuneColors.textSecondary] rather than the accent. Ten sections each with an
 * accent-coloured glyph is ten accents on one screen, which is exactly the rule the v2 palette exists
 * to enforce: the accent marks the one live thing, and on this screen nothing is live.
 */
@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    KitsuneCard(modifier = modifier.fillMaxWidth().padding(top = KitsuneTheme.spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = KitsuneTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(KitsuneTheme.spacing.sm))
            Text(
                text = title.uppercase(),
                style = KitsuneTheme.type.eyebrow,
                color = KitsuneTheme.colors.textDim
            )
        }
        Spacer(modifier = Modifier.height(KitsuneTheme.spacing.lg))
        content()
    }
}

private sealed interface SecurityDialog {
    data object PanicPinSetup : SecurityDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    /** False when Settings is rendered as a shell tab rather than pushed: there is nothing to go
     *  back to, so drawing a back arrow that pops the whole shell would be a trap. */
    showBack: Boolean = true,
    onAccountDeleted: () -> Unit = {},
    onOpenProviders: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as FragmentActivity
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bugReportSending by viewModel.bugReportSending.collectAsStateWithLifecycle()
    val securityMode by viewModel.securityMode.collectAsStateWithLifecycle()
    val isChangingSecurityMode by viewModel.isChangingSecurityMode.collectAsStateWithLifecycle()
    val securityActionResult by viewModel.securityActionResult.collectAsStateWithLifecycle()
    val panicPinResult by viewModel.panicPinResult.collectAsStateWithLifecycle()
    val deleteAccountState by viewModel.deleteAccountState.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val marketplaceEnabled by viewModel.marketplaceEnabled.collectAsStateWithLifecycle()
    val marketplaceServerUrl by viewModel.marketplaceServerUrl.collectAsStateWithLifecycle()
    val marketplaceAccountState by viewModel.marketplaceAccountState.collectAsStateWithLifecycle()
    val backupExportState by viewModel.backupExportState.collectAsStateWithLifecycle()
    val accountLockReason by viewModel.accountLockReason.collectAsStateWithLifecycle()
    val banReason by viewModel.banReason.collectAsStateWithLifecycle()
    var activeSecurityDialog by remember { mutableStateOf<SecurityDialog?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showDeleteMarketplaceDialog by remember { mutableStateOf(false) }
    // Held only between the passphrase dialog and the file picker's answer.
    var pendingBackupPassphrase by remember { mutableStateOf<CharArray?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)
    ) { uri ->
        val passphrase = pendingBackupPassphrase
        pendingBackupPassphrase = null
        if (uri != null && passphrase != null) viewModel.exportBackup(passphrase, uri)
    }
    var showBugReportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleteAccountState) {
        if (deleteAccountState is DeleteAccountState.Success) {
            showDeleteAccountDialog = false
            viewModel.dismissDeleteAccountResult()
            onAccountDeleted()
        }
    }

    LaunchedEffect(securityActionResult) {
        if (securityActionResult != null) activeSecurityDialog = null
    }
    LaunchedEffect(panicPinResult) {
        if (panicPinResult != null) activeSecurityDialog = null
    }

    if (showBugReportDialog) {
        var subject by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBugReportDialog = false },
            title = { Text(stringResource(R.string.bug_report_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text(stringResource(R.string.bug_report_subject_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.bug_report_description_label)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBugReportDialog = false
                        viewModel.generateBugReport(subject, description)
                    },
                    enabled = subject.isNotBlank() && description.isNotBlank()
                ) { Text(stringResource(R.string.bug_report_send_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showBugReportDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (bugReportSending) {
        BugReportPrivacyDialog()
    }

    when (activeSecurityDialog) {
        SecurityDialog.PanicPinSetup -> NewPinDialog(
            title = stringResource(R.string.panic_pin_setup_title),
            description = stringResource(R.string.panic_pin_setup_description),
            confirmLabel = stringResource(R.string.action_save),
            isBusy = false,
            onDismiss = { activeSecurityDialog = null },
            onConfirm = { pin -> viewModel.setPanicPin(pin.toCharArray(), pin.toCharArray()) }
        )
        null -> Unit
    }

    if (showLanguageDialog) {
        LanguageDialog(
            current = state.language,
            onSelect = { viewModel.setLanguage(it); showLanguageDialog = false },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            state = deleteAccountState,
            onDismiss = {
                showDeleteAccountDialog = false
                viewModel.dismissDeleteAccountResult()
            },
            onConfirm = viewModel::deleteAccountAndAllData
        )
    }

    if (showBackupDialog) {
        BackupPassphraseDialog(
            validate = viewModel::validateBackupPassphrase,
            onDismiss = { showBackupDialog = false },
            onConfirm = { passphrase ->
                showBackupDialog = false
                pendingBackupPassphrase = passphrase.toCharArray()
                backupLauncher.launch(BackupFormat.suggestedFileName())
            }
        )
    }

    backupExportState?.let { current ->
        AlertDialog(
            onDismissRequest = { if (current !is BackupExportState.Exporting) viewModel.dismissBackupExport() },
            title = { Text(stringResource(R.string.backup_section_title)) },
            text = {
                when (current) {
                    BackupExportState.Exporting -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.backup_exporting))
                    }
                    BackupExportState.Done -> Text(stringResource(R.string.backup_done))
                    is BackupExportState.Error -> Text(current.message, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                if (current !is BackupExportState.Exporting) {
                    TextButton(onClick = viewModel::dismissBackupExport) { Text(stringResource(R.string.action_ok)) }
                }
            }
        )
    }

    if (showDeleteMarketplaceDialog) {
        val deleting = marketplaceAccountState is MarketplaceAccountState.Deleting
        AlertDialog(
            onDismissRequest = { if (!deleting) { showDeleteMarketplaceDialog = false; viewModel.dismissMarketplaceAccountState() } },
            title = { Text(stringResource(R.string.marketplace_delete_account_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.marketplace_delete_account_body))
                    when (val st = marketplaceAccountState) {
                        is MarketplaceAccountState.Error -> Text(st.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                        MarketplaceAccountState.Deleted -> Text(stringResource(R.string.marketplace_delete_account_done), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        else -> Unit
                    }
                }
            },
            confirmButton = {
                if (marketplaceAccountState is MarketplaceAccountState.Deleted) {
                    TextButton(onClick = { showDeleteMarketplaceDialog = false; viewModel.dismissMarketplaceAccountState() }) {
                        Text(stringResource(R.string.action_ok))
                    }
                } else {
                    TextButton(onClick = viewModel::deleteMarketplaceAccount, enabled = !deleting) {
                        Text(stringResource(R.string.marketplace_delete_account_confirm), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                if (marketplaceAccountState !is MarketplaceAccountState.Deleted) {
                    TextButton(onClick = { showDeleteMarketplaceDialog = false; viewModel.dismissMarketplaceAccountState() }, enabled = !deleting) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        )
    }

    // KitsunePage, not a Material Scaffold with a titled TopAppBar: Settings is one of the shell's five
    // tabs, so it has to wear the same chrome as the other four — a pinned 22sp bar title here while
    // Histoires and Créer carry a large serif heading in the scroll would look like two different apps.
    KitsunePage(
        title = stringResource(R.string.settings_title),
        onBack = if (showBack) onBack else null
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = KitsuneTheme.spacing.gutter)
                .padding(bottom = KitsuneTheme.spacing.scrollBottom)
        ) {
            PageTitle(text = stringResource(R.string.settings_title))
            if (accountLockReason == AccountLockReason.BANNED) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.account_banned_notice_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            banReason?.takeIf { it.isNotBlank() }
                                ?.let { stringResource(R.string.account_banned_notice_body_with_reason, it) }
                                ?: stringResource(R.string.account_banned_notice_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            val uriHandler = LocalUriHandler.current

            SettingsSectionCard(title = stringResource(R.string.ai_section_title), icon = Icons.Filled.AutoAwesome) {
                if (providers.isEmpty()) {
                    Text(
                        stringResource(R.string.ai_section_no_provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                KitsuneRow(
                    title = stringResource(R.string.ai_providers_row),
                    subtitle = if (providers.isEmpty()) stringResource(R.string.ai_providers_none)
                        else providers.joinToString(", ") { it.name },
                    card = false,
                    onClick = onOpenProviders
                )
                KitsuneRow(
                    title = stringResource(R.string.ai_models_row),
                    subtitle = stringResource(R.string.ai_models_row_hint),
                    card = false,
                    onClick = onOpenModels
                )
            }

            SettingsSectionCard(title = stringResource(R.string.account_section_title), icon = Icons.Filled.AccountCircle) {
                // Propositions, messages, abonnements suivis and badges used to live here. They are
                // all facets of "who am I in this community", so v2 shows them in the Profil tab and
                // nowhere else — a thing reachable from two tabs is a thing the user has to search for.

                ListItem(
                    headlineContent = { Text(stringResource(R.string.language_label)) },
                    supportingContent = { Text(state.language.nativeName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageDialog = true }
                )

                // Username (pseudonyme) — a marketplace identity, so only shown while it is on.
                if (marketplaceEnabled) {
                val usernameAvailable by viewModel.usernameAvailable.collectAsStateWithLifecycle()
                val isSettingUsername by viewModel.isSettingUsername.collectAsStateWithLifecycle()
                var showUsernameDialog by remember { mutableStateOf(false) }
                var usernameInput by remember { mutableStateOf("") }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.username_label)) },
                    supportingContent = { Text(state.username ?: stringResource(R.string.username_not_set)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUsernameDialog = true }
                )

                if (showUsernameDialog) {
                    AlertDialog(
                        onDismissRequest = { showUsernameDialog = false },
                        title = { Text(stringResource(R.string.username_dialog_title)) },
                        text = {
                            Column {
                                Text(
                                    stringResource(R.string.username_dialog_description),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = usernameInput,
                                    onValueChange = {
                                        usernameInput = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }
                                        if (it.length >= 3) viewModel.checkUsername(it)
                                    },
                                    label = { Text(stringResource(R.string.username_field_label)) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                when {
                                    isSettingUsername -> Text(stringResource(R.string.username_saving), style = MaterialTheme.typography.bodySmall)
                                    usernameInput.length < 3 -> Text(stringResource(R.string.username_min_length_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    usernameAvailable == true -> Text(stringResource(R.string.username_available), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    usernameAvailable == false -> Text(stringResource(R.string.username_taken), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    else -> {}
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    viewModel.setUsername(usernameInput)
                                    showUsernameDialog = false
                                },
                                enabled = usernameInput.length >= 3 && usernameAvailable == true && !isSettingUsername
                            ) { Text(stringResource(R.string.action_save)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUsernameDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                        }
                    )
                }
                }
                if (marketplaceEnabled && state.backendUserId.isNotBlank()) {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    var copied by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.user_id_label)) },
                        supportingContent = {
                            Text(
                                if (copied) stringResource(R.string.copied_confirmation) else state.backendUserId,
                                color = if (copied) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(state.backendUserId))
                                copied = true
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_id_content_description))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(state.backendUserId))
                                copied = true
                            }
                    )
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.data_transparency_label)) },
                    supportingContent = { Text(stringResource(R.string.data_transparency_description)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri(DATA_TRANSPARENCY_URL) }
                )
            }

            SettingsSectionCard(title = stringResource(R.string.profile_section_title), icon = Icons.Filled.Person) {
                Text(
                    stringResource(R.string.profile_section_description),
                    style = MaterialTheme.typography.bodySmall
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Icon(
                            Icons.Filled.PrivacyTip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, end = 8.dp)
                        )
                        Text(
                            stringResource(R.string.profile_section_confidentiality_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = state.userFirstName,
                        onValueChange = viewModel::setUserFirstName,
                        label = { Text(stringResource(R.string.profile_first_name_label)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = state.userLastName,
                        onValueChange = viewModel::setUserLastName,
                        label = { Text(stringResource(R.string.profile_last_name_label)) },
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = state.userPronoun,
                    onValueChange = viewModel::setUserPronoun,
                    label = { Text(stringResource(R.string.profile_pronoun_label)) },
                    placeholder = { Text(stringResource(R.string.profile_pronoun_placeholder)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = state.userAge,
                    onValueChange = viewModel::setUserAge,
                    label = { Text(stringResource(R.string.profile_age_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = state.userPhysicalDescription,
                    onValueChange = viewModel::setUserPhysicalDescription,
                    label = { Text(stringResource(R.string.profile_physical_description_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = state.userSexualOrientation,
                    onValueChange = viewModel::setUserSexualOrientation,
                    label = { Text(stringResource(R.string.profile_sexual_orientation_label)) },
                    placeholder = { Text(stringResource(R.string.profile_sexual_orientation_placeholder)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            SettingsSectionCard(title = stringResource(R.string.creativity_section_title), icon = Icons.Filled.Palette) {
                // The tone library moved to the Créer tab, next to the personas whose voice it sets.
                // It is an authoring tool, and filing authoring tools under Settings is how v1 ended up
                // with ten screens nobody could find.

                Text(
                    stringResource(R.string.custom_style_label),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    stringResource(R.string.custom_style_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = state.customStylePrompt,
                    onValueChange = viewModel::setCustomStylePrompt,
                    label = { Text(stringResource(R.string.custom_style_placeholder)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2
                )

                Text(
                    stringResource(R.string.never_write_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    stringResource(R.string.never_write_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = state.neverWrite,
                    onValueChange = viewModel::setNeverWrite,
                    label = { Text(stringResource(R.string.never_write_placeholder)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2
                )

                // The slider that was never drawn (2026-08-23). `SettingsUiState.temperature`,
                // `SettingsViewModel.setTemperature` and the stored preference all existed, and
                // FEATURES.md listed the control as shipped — but nothing in this file ever rendered
                // it, so `setTemperature` had no caller and every chat in the app ran at the same
                // fixed 0.9 no matter what the user wanted.
                Text(
                    stringResource(R.string.temperature_label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    stringResource(R.string.temperature_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = state.temperature,
                        onValueChange = viewModel::setTemperature,
                        // Deliberately narrower than the 0.0-2.0 the preference accepts: below ~0.4 a
                        // roleplay model repeats itself into a loop, and above ~1.4 it loses the
                        // thread of the scene. Neither end is a setting anyone wants to land on by
                        // dragging a slider.
                        valueRange = 0.4f..1.4f,
                        steps = 9,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        String.format(Locale.US, "%.1f", state.temperature),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            SettingsSectionCard(title = stringResource(R.string.moderation_section_title), icon = Icons.Filled.Shield) {
                OutlinedTextField(
                    value = state.safeWord,
                    onValueChange = viewModel::setSafeWord,
                    label = { Text(stringResource(R.string.safe_word_label)) },
                    placeholder = { Text(stringResource(R.string.safe_word_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.safe_word_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            SettingsSectionCard(title = stringResource(R.string.privacy_section_title), icon = Icons.Filled.VisibilityOff) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.discreet_mode_label), modifier = Modifier.weight(1f))
                    Switch(checked = state.discreetModeEnabled, onCheckedChange = viewModel::setDiscreetModeEnabled)
                }
                Text(
                    stringResource(R.string.discreet_mode_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.auto_recap_label), modifier = Modifier.weight(1f))
                    Switch(checked = state.autoRecapEnabled, onCheckedChange = viewModel::setAutoRecapEnabled)
                }
                Text(
                    stringResource(R.string.auto_recap_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            SettingsSectionCard(title = stringResource(R.string.security_section_title), icon = Icons.Filled.Security) {
                OutlinedTextField(
                    value = state.autoLockMinutes.toString(),
                    onValueChange = { it.toIntOrNull()?.let(viewModel::setAutoLockMinutes) },
                    label = { Text(stringResource(R.string.auto_lock_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text(stringResource(R.string.screenshot_protection_label), modifier = Modifier.weight(1f))
                    Switch(checked = state.flagSecureEnabled, onCheckedChange = viewModel::setFlagSecureEnabled)
                }
                Text(
                    stringResource(R.string.screenshot_protection_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Text(
                    stringResource(R.string.vault_lock_title),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 20.dp)
                )
                Text(
                    stringResource(R.string.vault_lock_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                securityActionResult?.let { result ->
                    Text(
                        when (result) {
                            SecurityActionResult.Success -> stringResource(R.string.security_mode_updated)
                            is SecurityActionResult.Error -> result.message
                        },
                        color = if (result is SecurityActionResult.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (securityMode != VaultSecurityMode.BIOMETRIC_ONLY) {
                    Text(
                        stringResource(R.string.panic_pin_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Button(
                        onClick = { activeSecurityDialog = SecurityDialog.PanicPinSetup },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.panic_pin_configure_button))
                    }
                    panicPinResult?.let { result ->
                        Text(
                            when (result) {
                                SecurityActionResult.Success -> stringResource(R.string.panic_pin_saved)
                                is SecurityActionResult.Error -> result.message
                            },
                            color = if (result is SecurityActionResult.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.marketplace_section_title), icon = Icons.Filled.Storefront) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.marketplace_enabled_label), modifier = Modifier.weight(1f))
                    Switch(checked = marketplaceEnabled, onCheckedChange = viewModel::setMarketplaceEnabled)
                }
                Text(
                    stringResource(R.string.marketplace_enabled_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (marketplaceEnabled) {
                    var urlInput by remember(marketplaceServerUrl) { mutableStateOf(marketplaceServerUrl) }
                    var urlError by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it; urlError = false },
                        label = { Text(stringResource(R.string.marketplace_server_label)) },
                        isError = urlError,
                        supportingText = {
                            Text(
                                if (urlError) stringResource(R.string.marketplace_server_invalid)
                                else stringResource(R.string.marketplace_server_hint)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { urlError = !viewModel.setMarketplaceServerUrl(urlInput) },
                            enabled = urlInput.trim().trimEnd('/') != marketplaceServerUrl
                        ) { Text(stringResource(R.string.action_save)) }
                        if (!viewModel.isDefaultMarketplaceServer()) {
                            TextButton(onClick = viewModel::resetMarketplaceServerUrl) {
                                Text(stringResource(R.string.marketplace_server_reset))
                            }
                        }
                    }
                    TextButton(onClick = { showDeleteMarketplaceDialog = true }) {
                        Text(stringResource(R.string.marketplace_delete_account_button), color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.backup_section_title), icon = Icons.Filled.Backup) {
                Text(
                    stringResource(R.string.backup_section_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedButton(onClick = { showBackupDialog = true }) {
                    Text(stringResource(R.string.backup_export_button))
                }
            }

            SettingsSectionCard(title = stringResource(R.string.support_section_title), icon = Icons.Filled.SupportAgent) {
                Text(
                    stringResource(R.string.bug_report_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(onClick = { showBugReportDialog = true }) {
                    Text(stringResource(R.string.generate_bug_report_button))
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_issues)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(ISSUES_URL) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_source_code)) },
                    supportingContent = { Text(stringResource(R.string.about_license)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(SOURCE_CODE_URL) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_server_source)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(SERVER_SOURCE_URL) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_donate)) },
                    supportingContent = { Text(stringResource(R.string.about_donate_description)) },
                    trailingContent = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(DONATE_URL) }
                )
            }

            SettingsSectionCard(title = stringResource(R.string.danger_zone_section_title), icon = Icons.Filled.Warning) {
                Text(
                    stringResource(R.string.delete_account_section_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedButton(
                    onClick = { showDeleteAccountDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete_account_button))
                }
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    state: DeleteAccountState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isBusy = state is DeleteAccountState.InProgress

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(stringResource(R.string.delete_account_dialog_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.delete_account_dialog_local_warning),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    stringResource(R.string.delete_account_dialog_server_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    stringResource(R.string.delete_account_dialog_irreversible),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (state is DeleteAccountState.Error) {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isBusy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                }
                Text(stringResource(R.string.delete_account_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun BackupPassphraseDialog(
    validate: (String, String) -> String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_passphrase_title)) },
        text = {
            Column {
                Text(stringResource(R.string.backup_passphrase_description), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it; error = null },
                    label = { Text(stringResource(R.string.backup_passphrase_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; error = null },
                    label = { Text(stringResource(R.string.backup_passphrase_confirm_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = validate(passphrase, confirm)
                if (problem != null) error = problem else onConfirm(passphrase)
            }) { Text(stringResource(R.string.backup_export_button)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_label)) },
        text = {
            Column {
                AppLanguage.entries.forEach { language ->
                    ListItem(
                        headlineContent = { Text(language.nativeName) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) },
                        trailingContent = { if (language == current) Text(stringResource(R.string.language_selected_checkmark)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun PinPromptDialog(
    title: String,
    confirmLabel: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.pin_field_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin) },
                enabled = pin.isNotBlank() && !isBusy
            ) {
                if (isBusy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun NewPinDialog(
    title: String,
    description: String,
    confirmLabel: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (pin: String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val mismatch = confirmPin.isNotEmpty() && pin != confirmPin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.new_pin_field_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.confirm_pin_field_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                if (mismatch) {
                    Text(
                        stringResource(R.string.error_pins_do_not_match),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin) },
                enabled = pin.length >= 4 && pin == confirmPin && !isBusy
            ) {
                if (isBusy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}