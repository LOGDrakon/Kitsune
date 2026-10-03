package com.kitsune.feature.chat

import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.KitsuneActionSheet
import com.kitsune.core.designsystem.component.SheetAction
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.MessageEntity
import com.kitsune.core.data.local.entities.MessageRole
import com.kitsune.core.data.local.entities.ParticipantType
import com.kitsune.feature.chat.experiencemode.ExperienceModeDialog
import com.kitsune.core.designsystem.AiQuickGenerateSection
import com.kitsune.core.designsystem.DecryptedImage
import com.kitsune.core.designsystem.LocalDiscreetMode
import com.kitsune.core.designsystem.BugReportPrivacyDialog
import com.kitsune.core.designsystem.discreetBlur
import com.kitsune.feature.chat.director.DirectorToolsDialog
import com.kitsune.feature.chat.imagegen.GalleryTarget
import com.kitsune.feature.chat.lorebriefing.ChatLoreBriefingDialog
import com.kitsune.feature.chat.storycard.StoryCardSheet
import com.kitsune.feature.chat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MESSAGE_ANIM_DURATION_MS = 250

private sealed interface PendingMessageAction {
    data class Delete(val message: MessageEntity) : PendingMessageAction
    data class Rewind(val message: MessageEntity) : PendingMessageAction
}

/** Which report flow the shared bug-report dialog is serving — see its usage in [ChatScreen]. */
private sealed interface ReportTarget {
    data object Generic : ReportTarget
    data class Message(val messageId: String) : ReportTarget
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenMemory: (chatId: String) -> Unit,
    onOpenNovelMode: (chatId: String) -> Unit,
    onOpenTimeline: (chatId: String) -> Unit,
    onOpenBranchTree: (chatId: String) -> Unit,
    onOpenImageGeneration: (chatId: String) -> Unit,
    onForkChat: (chatId: String) -> Unit,
    onCreatePersonaForChat: (universeId: String, chatId: String) -> Unit,
    onCreatePersonaFromNpc: (universeId: String, chatId: String, npcId: String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val persona by viewModel.persona.collectAsStateWithLifecycle()
    val castMembers by viewModel.castMembers.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isProMode by viewModel.isProMode.collectAsStateWithLifecycle()
    val input by viewModel.inputText.collectAsStateWithLifecycle()
    val avatarBytes by viewModel.avatarBytes.collectAsStateWithLifecycle()
    val backgroundBytes by viewModel.backgroundBytes.collectAsStateWithLifecycle()
    val bugReportSending by viewModel.bugReportSending.collectAsStateWithLifecycle()
    val messageImageBytesById by viewModel.messageImageBytesById.collectAsStateWithLifecycle()
    val galleryTargets by viewModel.galleryTargets.collectAsStateWithLifecycle()
    val editInPlace by viewModel.editInPlace.collectAsStateWithLifecycle()
    val backgroundPickerSections by viewModel.backgroundPickerSections.collectAsStateWithLifecycle()
    val galleryImageBytesById by viewModel.galleryImageBytesById.collectAsStateWithLifecycle()
    val availableCastCandidates by viewModel.availableCastCandidates.collectAsStateWithLifecycle()
    val isGeneratingCastMember by viewModel.isGeneratingCastMember.collectAsStateWithLifecycle()
    val pendingRetry by viewModel.pendingRetry.collectAsStateWithLifecycle()
    val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()
    val briefingLoreEntries by viewModel.briefingLoreEntries.collectAsStateWithLifecycle()
    val nextReplySuggestions by viewModel.nextReplySuggestions.collectAsStateWithLifecycle()
    val isLoadingNextReplySuggestions by viewModel.isLoadingNextReplySuggestions.collectAsStateWithLifecycle()
    var showToolsSheet by remember { mutableStateOf(false) }
    var showBackgroundPickerDialog by remember { mutableStateOf(false) }
    var showAddCastMemberDialog by remember { mutableStateOf(false) }
    var showDirectorToolsDialog by remember { mutableStateOf(false) }
    var showExperienceModeDialog by remember { mutableStateOf(false) }
    var showBriefingDialog by remember { mutableStateOf(false) }
    // Which report flow the shared bug-report dialog below is currently serving — Generic is the
    // existing free-standing "signaler un problème" flow (full-conversation checkbox available);
    // Message/ContentPolicy are the new targeted flows (demande explicite), which auto-attach a
    // focused "Contenu d'intérêt" excerpt instead and hide that checkbox.
    var reportTarget by remember { mutableStateOf<ReportTarget?>(null) }
    var pendingMessageAction by remember { mutableStateOf<PendingMessageAction?>(null) }

    val chatId = chat?.id
    // The briefing dialog was never actually shown: this effect flipped `hasSeenBriefing` to true on
    // open without displaying anything, so the first-time universe briefing could only ever be
    // reached afterwards through ⋮ → "Relire le briefing". Now it opens, and is marked seen when the
    // user closes it.
    LaunchedEffect(chatId) {
        if (chat?.hasSeenBriefing == false && chat?.personaId == null) showBriefingDialog = true
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showScrollToBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    // Snapchat-style history reveal (requested by the user): only the latest message shows by
    // default, letting the background show through; scrolling away from the resting position
    // reveals earlier ones, and scrolling all the way back to it re-hides them — a real toggle,
    // not a one-way reveal. Purely a UI reveal — no data is hidden or deleted — and also resets to
    // "only latest visible" whenever this screen is recomposed fresh (e.g. leaving and reopening
    // the conversation).
    //
    // With few messages revealed, the LazyColumn's content is often shorter than the viewport, so
    // there is nothing to physically scroll and a drag gesture cannot change firstVisibleItemIndex
    // at all — a scroll-position-based trigger alone can leave the user stuck unable to reveal
    // anything. The hint item below is therefore the primary, always-working mechanism (tap reveals
    // the rest); the scroll-position trigger is a bonus for when there's already enough content to
    // make dragging meaningful.
    var revealedCount by remember { mutableIntStateOf(1) }
    val reversedMessages = messages.asReversed()
    val visibleMessages = remember(reversedMessages, revealedCount) { reversedMessages.take(revealedCount) }
    val hasMoreToReveal = visibleMessages.size < reversedMessages.size
    // Compared against the LazyColumn's own item count (not visibleMessages.size), since the
    // typing indicator and the reveal hint below are extra items sharing the same index space.
    val revealBoundaryIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val totalItemCount by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    LaunchedEffect(revealBoundaryIndex, totalItemCount, reversedMessages.size) {
        if (revealedCount < reversedMessages.size && totalItemCount > 0 && revealBoundaryIndex >= totalItemCount - 2) {
            revealedCount = (revealedCount + 1).coerceAtMost(reversedMessages.size)
        }
    }
    // Scrolled all the way back to the resting position (the newest message, right where the
    // conversation opens) — re-hide everything so the background shows through again.
    val isBackAtRest by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    LaunchedEffect(isBackAtRest) {
        if (isBackAtRest && revealedCount > 1) {
            revealedCount = 1
        }
    }

    LaunchedEffect(messages) { viewModel.ensureMessageImagesLoaded(messages) }

    if (bugReportSending) {
        BugReportPrivacyDialog()
    }

    reportTarget?.let { target ->
        var subject by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var attachConversation by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text(stringResource(R.string.chat_bug_report_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text(stringResource(R.string.chat_bug_report_subject_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.chat_bug_report_description_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (target == ReportTarget.Generic) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = attachConversation, onCheckedChange = { attachConversation = it })
                            Text(stringResource(R.string.chat_bug_report_attach_conversation_label))
                        }
                    } else {
                        Text(
                            stringResource(R.string.chat_bug_report_targeted_excerpt_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        reportTarget = null
                        when (target) {
                            is ReportTarget.Message -> viewModel.reportMessage(target.messageId, subject, description)
                            ReportTarget.Generic -> viewModel.generateBugReport(subject, description, attachConversation)
                        }
                    },
                    enabled = subject.isNotBlank() && description.isNotBlank()
                ) { Text(stringResource(R.string.chat_bug_report_send_button)) }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showDirectorToolsDialog) {
        DirectorToolsDialog(
            onDismiss = { showDirectorToolsDialog = false },
            onToolSelected = { kind ->
                showDirectorToolsDialog = false
                viewModel.injectDirectorBeat(kind)
            }
        )
    }

    val currentChat = chat
    // Loaded lazily and only while the dialog is open: it hits the backend cosmetic catalogue, and
    // most users own no pack at all, in which case the section is hidden entirely.
    var ownedStylePacks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(showExperienceModeDialog) {
        if (showExperienceModeDialog) ownedStylePacks = viewModel.availableStylePacks()
    }

    if (showExperienceModeDialog && currentChat != null) {
        val allowMatureModes = persona?.let {
            MaturityTag.NSFW in it.maturityTags || MaturityTag.DARK in it.maturityTags
        } ?: true
        ExperienceModeDialog(
            storyPaceMode = currentChat.storyPaceMode,
            toneMode = currentChat.toneMode,
            involvementMode = currentChat.involvementMode,
            narrativeRhythmMode = currentChat.narrativeRhythmMode,
            universeMode = currentChat.universeMode,
            intensityMode = currentChat.intensityMode,
            replyLength = currentChat.replyLength,
            narrationBalance = currentChat.narrationBalance,
            voiceMode = currentChat.voiceMode,
            stylePacks = ownedStylePacks,
            selectedStylePackId = currentChat.stylePackId,
            allowMatureModes = allowMatureModes,
            storyTimeAnchor = currentChat.storyTimeAnchor,
            customDirective = currentChat.customExperienceDirective,
            onStoryPaceModeSelected = viewModel::setStoryPaceMode,
            onToneModeSelected = viewModel::setToneMode,
            onInvolvementModeSelected = viewModel::setInvolvementMode,
            onNarrativeRhythmModeSelected = viewModel::setNarrativeRhythmMode,
            onUniverseModeSelected = viewModel::setUniverseExperienceMode,
            onIntensityModeSelected = viewModel::setIntensityMode,
            onReplyLengthSelected = viewModel::setReplyLength,
            onNarrationBalanceSelected = viewModel::setNarrationBalance,
            onVoiceModeSelected = viewModel::setVoiceMode,
            onStylePackSelected = viewModel::setStylePack,
            onStoryTimeAnchorChanged = viewModel::setStoryTimeAnchor,
            onCustomDirectiveChanged = viewModel::setCustomExperienceDirective,
            // The style-pivot marker is emitted from the diff taken between these two callbacks,
            // never from the individual setters above — see ChatViewModel.onExperienceModeDialogOpened.
            onDismiss = {
                showExperienceModeDialog = false
                viewModel.onExperienceModeDialogClosed()
            }
        )
    }

    if (showBriefingDialog) {
        ChatLoreBriefingDialog(
            loreEntries = briefingLoreEntries,
            onDismiss = {
                showBriefingDialog = false
                viewModel.markBriefingSeen()
            },
            onStart = {
                showBriefingDialog = false
                viewModel.markBriefingSeen()
            }
        )
    }

    val context = LocalContext.current
    val backgroundPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) viewModel.setBackground(bytes)
            }
        }
    }

    if (showBackgroundPickerDialog) {
        BackgroundPickerDialog(
            sections = backgroundPickerSections,
            imageBytesById = galleryImageBytesById,
            onPickFromStorage = {
                showBackgroundPickerDialog = false
                backgroundPickerLauncher.launch("image/*")
            },
            onPickFromGallery = { imageStoreId ->
                viewModel.setBackgroundFromGallery(imageStoreId)
                showBackgroundPickerDialog = false
            },
            onDismiss = { showBackgroundPickerDialog = false }
        )
    }

    if (showAddCastMemberDialog) {
        AddCastMemberDialog(
            candidates = availableCastCandidates,
            currentNpcCastMembers = castMembers.filter { it.participantType == ParticipantType.NPC },
            isGenerating = isGeneratingCastMember,
            onAddExisting = { candidate, framing -> viewModel.addCastMember(candidate.type, candidate.id, framing) },
            onGenerateNewNpc = { description, framing -> viewModel.generateAndAddNpc(description, framing) },
            onCreateNewPersona = {
                val universeId = chat?.universeId
                val currentChatId = chat?.id
                if (universeId != null && currentChatId != null) {
                    showAddCastMemberDialog = false
                    onCreatePersonaForChat(universeId, currentChatId)
                }
            },
            onConvertNpcToPersona = { npcId ->
                val universeId = chat?.universeId
                val currentChatId = chat?.id
                if (universeId != null && currentChatId != null) {
                    showAddCastMemberDialog = false
                    onCreatePersonaFromNpc(universeId, currentChatId, npcId)
                }
            },
            onDismiss = { showAddCastMemberDialog = false }
        )
    }

    pendingMessageAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingMessageAction = null },
            title = { Text(if (action is PendingMessageAction.Rewind) stringResource(R.string.chat_rewind_confirm_title) else stringResource(R.string.chat_delete_message_confirm_title)) },
            text = {
                Text(
                    if (action is PendingMessageAction.Rewind) {
                        stringResource(R.string.chat_rewind_confirm_text)
                    } else {
                        stringResource(R.string.chat_delete_message_confirm_text)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        is PendingMessageAction.Delete -> viewModel.deleteMessage(action.message)
                        is PendingMessageAction.Rewind -> viewModel.rewindTo(action.message)
                    }
                    pendingMessageAction = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingMessageAction = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    // The framing question replaces the conversation until it is answered, rather than floating over
    // it: the answer changes how the very first message is written, so there is nothing meaningful to
    // show behind it yet. `needsStoryCard` is false as soon as the story has any message, so this can
    // never interrupt a conversation already under way — including the ones that predate the card.
    val needsStoryCard by viewModel.needsStoryCard.collectAsStateWithLifecycle()
    val storyCardGenerating by viewModel.storyCardGenerating.collectAsStateWithLifecycle()
    if (needsStoryCard) {
        // Keyed on the persona so the mature presets appear once its tags have loaded, rather than
        // being decided against a null persona on first composition.
        val presets = remember(persona?.id, persona?.maturityTags) { viewModel.offeredPresets() }
        StoryCardSheet(
            presets = presets,
            isGenerating = storyCardGenerating,
            onChoosePreset = { viewModel.applyStoryCard(it) },
            onDescribe = viewModel::generateStoryCard,
            onSkip = viewModel::skipStoryCard
        )
        return
    }

    // The story-tools sheet, replacing v1's 15-entry `DropdownMenu`.
    //
    // Two problems with the dropdown, beyond the length. It anchored to the top-right corner of a tall
    // phone, so every entry was out of thumb reach; and its three groups were separated only by
    // dividers, so "Mode roman" sat visually next to "Signaler un problème" with nothing saying they
    // were different kinds of thing. A bottom sheet fixes the reach, and named sections restore the
    // grouping.
    if (showToolsSheet) {
        val isEnsemble = chat != null && chat?.personaId == null
        val chatId = chat?.id
        KitsuneActionSheet(
            title = stringResource(R.string.chat_tools_sheet_title),
            onDismiss = { showToolsSheet = false },
            actions = buildList {
                val sceneSection = stringResource(R.string.chat_tools_section_scene)
                add(
                    SheetAction(
                        label = stringResource(R.string.image_gen_title),
                        icon = Icons.Outlined.Image,
                        section = sceneSection,
                        enabled = chatId != null,
                        onClick = { chatId?.let(onOpenImageGeneration) }
                    )
                )
                add(
                    SheetAction(
                        label = stringResource(R.string.chat_menu_background),
                        icon = Icons.Outlined.Wallpaper,
                        section = sceneSection,
                        onClick = {
                            viewModel.loadGalleriesForBackgroundPicker()
                            showBackgroundPickerDialog = true
                        }
                    )
                )
                if (isEnsemble) {
                    add(
                        SheetAction(
                            label = stringResource(R.string.chat_menu_add_character),
                            icon = Icons.Outlined.GroupAdd,
                            section = sceneSection,
                            onClick = {
                                viewModel.loadAvailableCastCandidates()
                                showAddCastMemberDialog = true
                            }
                        )
                    )
                }
                add(
                    SheetAction(
                        label = stringResource(R.string.scene_tools_label),
                        icon = Icons.Outlined.Movie,
                        section = sceneSection,
                        onClick = { showDirectorToolsDialog = true }
                    )
                )
                add(
                    SheetAction(
                        label = stringResource(R.string.experience_modes_menu_label),
                        icon = Icons.Outlined.Tune,
                        section = sceneSection,
                        onClick = {
                            viewModel.onExperienceModeDialogOpened()
                            showExperienceModeDialog = true
                        }
                    )
                )

                val readSection = stringResource(R.string.chat_tools_section_read)
                add(
                    SheetAction(
                        label = stringResource(R.string.chat_menu_novel_mode),
                        icon = Icons.Outlined.MenuBook,
                        section = readSection,
                        enabled = chatId != null,
                        onClick = { chatId?.let(onOpenNovelMode) }
                    )
                )
                add(
                    SheetAction(
                        label = stringResource(R.string.story_memory_title),
                        icon = Icons.Outlined.Psychology,
                        section = readSection,
                        enabled = chatId != null,
                        onClick = { chatId?.let(onOpenMemory) }
                    )
                )
                add(
                    SheetAction(
                        label = stringResource(R.string.timeline_title),
                        icon = Icons.Outlined.Timeline,
                        section = readSection,
                        enabled = chatId != null,
                        onClick = { chatId?.let(onOpenTimeline) }
                    )
                )
                add(
                    SheetAction(
                        label = stringResource(R.string.chat_menu_branch_tree),
                        icon = Icons.Outlined.AccountTree,
                        section = readSection,
                        enabled = chatId != null,
                        onClick = { chatId?.let(onOpenBranchTree) }
                    )
                )
                if (isEnsemble && chat?.universeId != null) {
                    add(
                        SheetAction(
                            label = stringResource(R.string.chat_menu_replay_briefing),
                            icon = Icons.Outlined.Replay,
                            section = readSection,
                            onClick = { showBriefingDialog = true }
                        )
                    )
                }

                add(
                    SheetAction(
                        label = stringResource(R.string.chat_menu_report_issue),
                        icon = Icons.Outlined.BugReport,
                        section = stringResource(R.string.chat_tools_section_help),
                        onClick = { reportTarget = ReportTarget.Generic }
                    )
                )
            }
        )
    }

    Scaffold(
        containerColor = KitsuneTheme.colors.background,
        topBar = {
            ChatTopBar(
                isEnsemble = chat != null && chat?.personaId == null,
                title = if (chat?.personaId == null) chat?.title.orEmpty() else persona?.name.orEmpty(),
                subtitle = if (chat?.personaId == null) {
                    val count = castMembers.size
                    if (count > 1) {
                        stringResource(R.string.chat_ensemble_subtitle_plural, count)
                    } else {
                        stringResource(R.string.chat_ensemble_subtitle_singular, count)
                    }
                } else {
                    stringResource(R.string.chat_persona_main_subtitle)
                },
                avatarBytes = avatarBytes,
                isPro = isProMode,
                onTogglePro = viewModel::setProMode,
                onBack = onBack,
                onOpenTools = { showToolsSheet = true }
            )
        },
        bottomBar = {
            ChatComposer(
                value = input,
                onValueChange = viewModel::updateInputText,
                isEditing = editingMessage != null,
                isSending = isSending,
                suggestions = nextReplySuggestions,
                isLoadingSuggestions = isLoadingNextReplySuggestions,
                onRequestSuggestions = viewModel::loadNextReplySuggestions,
                onUseSuggestion = viewModel::useNextReplySuggestion,
                onInsertAsterisk = viewModel::insertAsterisk,
                onCancelEdit = viewModel::cancelEdit,
                onSend = when {
                    editingMessage == null -> viewModel::sendMessage
                    // An assistant reply, or a user message that is no longer the last one: the text
                    // changes, the story keeps its shape.
                    editInPlace -> { -> viewModel.saveEditedMessageInPlace(input.text) }
                    else -> viewModel::updateEditedMessage
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isDiscreet = LocalDiscreetMode.current
            if (backgroundBytes != null && !isDiscreet) {
                DecryptedImage(
                    bytes = backgroundBytes,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            val isEmptyEnsembleChat = chat != null && chat?.personaId == null && messages.isEmpty()
            if (isEmptyEnsembleChat) {
                // Nothing to show in the list yet, so the LazyColumn below is skipped entirely
                // rather than left empty behind this — an empty LazyColumn still installs a
                // fillMaxSize scrollable surface and, being declared after (so drawn on top of)
                // this Column, would silently swallow taps on the button before they ever reached
                // it (reported bug: tapping "Générer une scène d'ouverture" did nothing at all).
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.chat_empty_scene_message, castMembers.joinToString(", ") { it.name }),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = viewModel::generateOpeningScene,
                        enabled = !isSending,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.chat_generate_opening_scene_button))
                        }
                    }
                }
            } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true
            ) {
                if (isSending) {
                    item(key = "typing_indicator") {
                        TypingIndicator()
                    }
                }
                items(visibleMessages, key = MessageEntity::id) { message ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(MESSAGE_ANIM_DURATION_MS)) +
                            slideInVertically(
                                animationSpec = tween(MESSAGE_ANIM_DURATION_MS),
                                initialOffsetY = { it / 4 }
                            ),
                        modifier = Modifier.animateItem()
                    ) {
                        MessageBubble(
                            message,
                            imageBytes = message.imageAttachmentPath?.let { messageImageBytesById[it] },
                            translucent = backgroundBytes != null,
                            galleryTargets = galleryTargets,
                            onRegenerate = viewModel::regenerateLastResponse,
                            onContinue = viewModel::continueLastResponse,
                            onEdit = { viewModel.startEditingMessage(message) },
                            onFork = { messageId -> viewModel.forkChat(messageId, onForkChat) },
                            onDelete = { pendingMessageAction = PendingMessageAction.Delete(message) },
                            onRewind = { pendingMessageAction = PendingMessageAction.Rewind(message) },
                            onReport = { reportTarget = ReportTarget.Message(message.id) },
                            onSaveImageToGallery = { targetPersonaId ->
                                message.imageAttachmentPath?.let {
                                    viewModel.saveMessageImageToGallery(it, targetPersonaId, message.content)
                                }
                            }
                        )
                    }
                }
                if (hasMoreToReveal) {
                    item(key = "reveal_hint") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { revealedCount = reversedMessages.size }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.chat_reveal_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
            }
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) {
                    listState.animateScrollToItem(0)
                }
            }
            AnimatedVisibility(
                visible = showScrollToBottom,
                enter = fadeIn(animationSpec = tween(200)),
                exit = androidx.compose.animation.fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.content_desc_scroll_to_bottom))
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = error != null,
                enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(200)),
                exit = androidx.compose.animation.fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                error?.let { errorMsg ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    errorMsg,
                                    modifier = Modifier.weight(1f, fill = false),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (pendingRetry != null) {
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(
                                        onClick = viewModel::retrySameModel,
                                        enabled = !isSending
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.action_retry))
                                    }
                                }
                            }
                            TextButton(
                                onClick = viewModel::dismissError
                            ) {
                                Text(stringResource(R.string.action_close))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundPickerDialog(
    sections: List<BackgroundPickerSection>,
    imageBytesById: Map<String, ByteArray>,
    onPickFromStorage: () -> Unit,
    onPickFromGallery: (imageStoreId: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_background_picker_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.chat_background_picker_description),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(88.dp).clickable(onClick = onPickFromStorage)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                    Text(
                                        stringResource(R.string.chat_background_picker_storage_option),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                // One labelled row per gallery — the conversation's own gallery, the universe's
                // (ensemble chats only), then one row per persona, sorted by name.
                sections.forEach { section ->
                    Text(
                        section.title,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(section.imageIds, key = { it }) { imageId ->
                            GalleryImageCell(imageId, imageBytesById[imageId], onPickFromGallery)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun GalleryImageCell(
    imageId: String,
    bytes: ByteArray?,
    onPick: (imageStoreId: String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .size(88.dp)
            .clickable(enabled = bytes != null) { onPick(imageId) }
    ) {
        if (bytes != null) {
            DecryptedImage(
                bytes = bytes,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun AddCastMemberDialog(
    candidates: List<CastCandidateOption>,
    currentNpcCastMembers: List<CastMember>,
    isGenerating: Boolean,
    onAddExisting: (CastCandidateOption, ParticipantJoinFraming) -> Unit,
    onGenerateNewNpc: (description: String, framing: ParticipantJoinFraming) -> Unit,
    onCreateNewPersona: () -> Unit,
    onConvertNpcToPersona: (npcId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var framing by remember { mutableStateOf(ParticipantJoinFraming.RETROACTIVE) }
    var newNpcDescription by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_add_cast_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.chat_add_cast_framing_question),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = framing == ParticipantJoinFraming.RETROACTIVE,
                        onClick = { framing = ParticipantJoinFraming.RETROACTIVE },
                        label = { Text(stringResource(R.string.chat_framing_retroactive_label)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = framing == ParticipantJoinFraming.ARRIVAL,
                        onClick = { framing = ParticipantJoinFraming.ARRIVAL },
                        label = { Text(stringResource(R.string.chat_framing_arrival_label)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    if (framing == ParticipantJoinFraming.RETROACTIVE) {
                        stringResource(R.string.chat_framing_retroactive_description)
                    } else {
                        stringResource(R.string.chat_framing_arrival_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                if (candidates.isNotEmpty()) {
                    Text(
                        stringResource(R.string.chat_existing_universe_characters_label),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    candidates.forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAddExisting(candidate, framing) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (candidate.type == ParticipantType.PERSONA) Icons.Default.Person else Icons.Default.Groups,
                                contentDescription = null
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(candidate.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (candidate.type == ParticipantType.PERSONA) stringResource(R.string.chat_candidate_type_persona) else stringResource(R.string.chat_candidate_type_npc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                }

                Text(
                    stringResource(R.string.chat_create_new_npc_label),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                AiQuickGenerateSection(
                    description = newNpcDescription,
                    onDescriptionChange = { newNpcDescription = it },
                    isGenerating = isGenerating,
                    onGenerate = { onGenerateNewNpc(newNpcDescription, framing) }
                )

                Text(
                    stringResource(R.string.chat_create_new_persona_label),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                Text(
                    stringResource(R.string.chat_create_new_persona_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedButton(onClick = onCreateNewPersona, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.chat_new_persona_button))
                }

                if (currentNpcCastMembers.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 16.dp))
                    Text(
                        stringResource(R.string.chat_convert_npc_section_label),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    currentNpcCastMembers.forEach { npc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConvertNpcToPersona(npc.participantId) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Groups, contentDescription = null)
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(npc.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.chat_convert_to_persona_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .padding(horizontal = 2.dp)
                            .clip(CircleShape)
                    ) {
                        androidx.compose.animation.core.LinearEasing
                        CircularProgressIndicator(
                            modifier = Modifier.size(6.dp),
                            strokeWidth = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index < 2) {
                        Box(modifier = Modifier.size(4.dp))
                    }
                }
            }
        }
    }
}

private const val BUBBLE_ALPHA_TRANSLUCENT = 0.78f
private const val BUBBLE_MAX_WIDTH_FRACTION = 0.8f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageEntity,
    imageBytes: ByteArray? = null,
    translucent: Boolean = false,
    galleryTargets: List<GalleryTarget> = emptyList(),
    onRegenerate: () -> Unit = {},
    onContinue: () -> Unit = {},
    onEdit: () -> Unit = {},
    onFork: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onRewind: () -> Unit = {},
    onReport: () -> Unit = {},
    onSaveImageToGallery: (targetPersonaId: String) -> Unit = {}
) {
    val colors = KitsuneTheme.colors
    if (message.role == MessageRole.SYSTEM) {
        // A system line is a stage direction, not a message: centred, small, dim, no container. v1
        // rendered it in the same `bodySmall`/`onSurfaceVariant` as ordinary metadata, so a "chapter
        // closed" marker looked like a timestamp.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = KitsuneTheme.spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.content,
                style = KitsuneTheme.type.meta,
                color = colors.textDim,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val isUser = message.role == MessageRole.USER
    var showMenu by remember { mutableStateOf(false) }
    var showGalleryTargetPicker by remember { mutableStateOf(false) }
    val maxBubbleWidth = LocalConfiguration.current.screenWidthDp.dp * BUBBLE_MAX_WIDTH_FRACTION
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = KitsuneTheme.spacing.md, vertical = KitsuneTheme.spacing.xs),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // The AI's bubble is the app's main reading surface, so it gets the neutral raised surface and
        // full-strength text; the user's own turn is a warm tint rather than a saturated accent block,
        // because a page of alternating solid-accent slabs is unreadable over a long session.
        val baseColor = if (isUser) colors.bubbleOutgoing else colors.bubbleIncoming
        val onBubble = if (isUser) colors.onBubbleOutgoing else colors.onBubbleIncoming
        Surface(
            color = if (translucent) baseColor.copy(alpha = BUBBLE_ALPHA_TRANSLUCENT) else baseColor,
            // Asymmetric corners: the bubble is square on the side it grows from, so who is speaking
            // is readable from the silhouette alone without relying on alignment or colour.
            shape = if (isUser) KitsuneTheme.shape.bubbleOutgoing else KitsuneTheme.shape.bubbleIncoming,
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showMenu = true }
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = KitsuneTheme.spacing.lg,
                    vertical = KitsuneTheme.spacing.md
                )
            ) {
                if (!isUser) {
                    val speakerName = remember(message.content) { parseSpeakerName(message.content) }
                    if (speakerName != null) {
                        Text(
                            text = speakerName.uppercase(),
                            style = KitsuneTheme.type.eyebrow,
                            color = colors.accent,
                            modifier = Modifier.padding(bottom = KitsuneTheme.spacing.sm)
                        )
                    }
                }
                if (imageBytes != null) {
                    val isDiscreet = LocalDiscreetMode.current
                    DecryptedImage(
                        bytes = imageBytes,
                        contentDescription = message.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(220.dp)
                            .clip(KitsuneTheme.shape.sm)
                            .then(if (isDiscreet) Modifier.discreetBlur(16.dp) else Modifier),
                        contentScale = ContentScale.Crop
                    )
                }
                if (message.content.isNotBlank()) {
                    // Narration (*she turns away*) is dimmed rather than coloured, and asides take the
                    // accent — the same two-step contrast the rest of the app uses for "secondary" and
                    // "this one thing".
                    val actionColor = onBubble.copy(alpha = 0.66f)
                    val asideColor = colors.accent
                    Text(
                        text = formatRoleplayText(message.content, actionColor, asideColor),
                        style = KitsuneTheme.type.message,
                        color = onBubble,
                        modifier = if (imageBytes != null) {
                            Modifier.padding(top = KitsuneTheme.spacing.md)
                        } else {
                            Modifier
                        }
                    )
                }
                Row(
                    modifier = Modifier.padding(top = KitsuneTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMessageTime(message.createdAt, context),
                        style = KitsuneTheme.type.meta,
                        color = onBubble.copy(alpha = 0.38f)
                    )
                    if (message.isEdited) {
                        Text(
                            text = stringResource(R.string.chat_message_edited_suffix),
                            style = KitsuneTheme.type.meta,
                            color = onBubble.copy(alpha = 0.38f),
                            modifier = Modifier.padding(start = KitsuneTheme.spacing.xs)
                        )
                    }
                }
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            if (!isUser) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_regenerate_action)) },
                    onClick = {
                        showMenu = false
                        onRegenerate()
                    }
                )
                // A reply cut off at the token ceiling is the one case where regenerating is the
                // wrong answer: the user liked what they got, there just was not enough of it.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_continue_action)) },
                    onClick = {
                        showMenu = false
                        onContinue()
                    }
                )
            }
            // Any message, either role (2026-08-25). Editing an assistant reply no longer replays the
            // story from that point — see ChatViewModel.saveEditedMessageInPlace — so there is nothing
            // left to protect against here.
            run {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_edit)) },
                    onClick = {
                        showMenu = false
                        onEdit()
                    }
                )
            }
            if (imageBytes != null && galleryTargets.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_add_to_gallery_action)) },
                    onClick = {
                        showMenu = false
                        if (galleryTargets.size == 1) {
                            onSaveImageToGallery(galleryTargets.first().personaId)
                        } else {
                            showGalleryTargetPicker = true
                        }
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_fork_action)) },
                onClick = {
                    showMenu = false
                    onFork(message.id)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_rewind_action)) },
                onClick = {
                    showMenu = false
                    onRewind()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_delete_message_action)) },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_report_message_action)) },
                onClick = {
                    showMenu = false
                    onReport()
                }
            )
        }
        if (showGalleryTargetPicker) {
            AlertDialog(
                onDismissRequest = { showGalleryTargetPicker = false },
                title = { Text(stringResource(R.string.chat_gallery_target_picker_title)) },
                text = {
                    Column {
                        galleryTargets.forEach { target ->
                            TextButton(
                                onClick = {
                                    onSaveImageToGallery(target.personaId)
                                    showGalleryTargetPicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(target.personaName, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showGalleryTargetPicker = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            )
        }
    }
}

private fun formatMessageTime(millis: Long, context: Context): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000 -> context.getString(R.string.time_just_now)
        diff < 3_600_000 -> context.getString(R.string.time_minutes_ago, diff / 60_000)
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }
}

private val SpeakerNameRegex = Regex("""^\*\*([^*\n]+):\*\*""")

/** Tries to extract a "**Name:**" speaker prefix from an assistant message so the UI can show
 *  who is talking in ensemble / NPC-heavy scenes.
 */
private fun parseSpeakerName(content: String): String? {
    return SpeakerNameRegex.find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
}
