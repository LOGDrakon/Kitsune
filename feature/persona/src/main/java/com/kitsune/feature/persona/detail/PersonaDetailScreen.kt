package com.kitsune.feature.persona.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.data.local.entities.MaturityTag
import com.kitsune.core.data.local.entities.ToneCardEntity
import com.kitsune.core.data.local.entities.EntrySceneEntity
import com.kitsune.core.designsystem.AiQuickGenerateSection
import com.kitsune.core.designsystem.DecryptedImage
import com.kitsune.core.designsystem.ImageCropper
import androidx.compose.ui.focus.onFocusChanged
import com.kitsune.core.designsystem.TagsEditor
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.core.network.persona.SceneDraft
import com.kitsune.feature.persona.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaDetailScreen(
    onBack: () -> Unit,
    onOpenChat: (chatId: String) -> Unit,
    onOpenImageGeneration: (personaId: String) -> Unit,
    viewModel: PersonaDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val avatarBytes by viewModel.avatarBytes.collectAsStateWithLifecycle()
    val galleryBytesById by viewModel.galleryBytesById.collectAsStateWithLifecycle()
    val toneCards by viewModel.toneCards.collectAsStateWithLifecycle()

    var showCreateSceneDialog by remember { mutableStateOf(false) }
    var editingToneCard by remember { mutableStateOf<ToneCardEdit?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAvatarChooser by remember { mutableStateOf(false) }
    var isPublic by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var croppingImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val avatarPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) viewModel.setAvatar(bytes)
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshState() }

    LaunchedEffect(uiState) {
        val ready = uiState as? PersonaDetailUiState.Ready
        if (ready != null) {
            isPublic = viewModel.checkIfPublished(ready.persona)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startChatEvent.collect { event ->
            if (event != null) {
                onOpenChat(event.chatId)
                viewModel.consumeStartChatEvent()
            }
        }
    }

    error?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text(stringResource(R.string.label_error)) },
            text = { Text(it) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text(stringResource(R.string.action_ok)) }
            }
        )
    }

    editingToneCard?.let { editing ->
        val existing = (editing as? ToneCardEdit.Existing)?.card
        ToneCardDialog(
            initial = existing,
            // Same maturity gate as everywhere else: a tone card must not be able to hand a
            // plainly-tagged persona a register the experience dialog would refuse it.
            allowMatureModes = (uiState as? PersonaDetailUiState.Ready)?.persona?.maturityTags.orEmpty()
                .let { MaturityTag.NSFW in it || MaturityTag.DARK in it },
            onDismiss = { editingToneCard = null },
            onSave = { draft ->
                viewModel.saveToneCard(existing?.id, draft)
                editingToneCard = null
            }
        )
    }

    if (showCreateSceneDialog) {
        CreateSceneDialog(
            onDismiss = { showCreateSceneDialog = false },
            onGenerate = { description -> viewModel.generateEntryScene(description) },
            onCreate = { title, scenario, firstMessage ->
                viewModel.createEntryScene(title, scenario, firstMessage)
                showCreateSceneDialog = false
            }
        )
    }

if (showDeleteConfirm) {
        val state = uiState as? PersonaDetailUiState.Ready
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.persona_detail_delete_confirm_title, state?.persona?.name ?: "")) },
            text = { Text(stringResource(R.string.persona_detail_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePersona(onBack)
                    showDeleteConfirm = false
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showAvatarChooser) {
        AlertDialog(
            onDismissRequest = { showAvatarChooser = false },
            title = { Text(stringResource(R.string.persona_detail_avatar_dialog_title)) },
            text = { Text(stringResource(R.string.persona_detail_avatar_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showAvatarChooser = false
                    (uiState as? PersonaDetailUiState.Ready)?.persona?.id?.let { onOpenImageGeneration(it) }
                }) { Text(stringResource(R.string.persona_detail_generate_with_ai_button)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAvatarChooser = false
                    avatarPickerLauncher.launch("image/*")
                }) { Text(stringResource(R.string.persona_detail_choose_from_gallery_button)) }
            }
        )
    }

    val cropBytes = croppingImageBytes
    if (cropBytes != null) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.95f)) {
            ImageCropper(
                bytes = cropBytes,
                onCrop = { cropped ->
                    viewModel.setAvatar(cropped)
                    croppingImageBytes = null
                }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val state = uiState as? PersonaDetailUiState.Ready
                    Text(state?.persona?.name ?: "")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val readyState = uiState as? PersonaDetailUiState.Ready
                    // Public/Private switch — no avatar required, the server never enforces one.
                    // Hidden entirely for personas downloaded from the marketplace
                    // (sourceListingId set) — republishing someone else's downloaded creation as
                    // your own listing is not allowed (ViewModel enforces this too, as a backstop).
                    if (readyState != null && readyState.persona.sourceListingId == null) {
                        Text(stringResource(R.string.persona_detail_public_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(
                            checked = isPublic,
                            enabled = !isPublishing,
                            onCheckedChange = { newValue ->
                                if (newValue) {
                                    // Publish to marketplace
                                    readyState?.persona?.let { persona ->
                                        isPublishing = true
                                        scope.launch {
                                            try {
                                                viewModel.publishToMarketplace(persona, persona.shortDescription)
                                                isPublic = true
                                            } catch (e: Exception) {
                                                viewModel.setError(context.getString(R.string.persona_detail_error_publish_format, e.message ?: ""))
                                            }
                                            isPublishing = false
                                        }
                                    }
                                } else {
                                    // Remove from marketplace
                                    readyState?.persona?.let { persona ->
                                        isPublishing = true
                                        scope.launch {
                                            try {
                                                viewModel.unpublishFromMarketplace(persona.id)
                                                isPublic = false
                                            } catch (e: Exception) {
                                                viewModel.setError(context.getString(R.string.persona_detail_error_unpublish_format, e.message ?: ""))
                                            }
                                            isPublishing = false
                                        }
                                    }
                                }
                            }
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is PersonaDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is PersonaDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is PersonaDetailUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    PersonaHeader(
                        persona = state.persona,
                        avatarBytes = avatarBytes,
                        onAvatarClick = { showAvatarChooser = true },
                        onAddTag = viewModel::addTag,
                        onRemoveTag = viewModel::removeTag
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    InterioritySection(
                        persona = state.persona,
                        onTraitChange = viewModel::updateInteriority
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ToneCardsSection(
                        toneCards = toneCards,
                        onCreate = { editingToneCard = ToneCardEdit.New },
                        onEdit = { editingToneCard = ToneCardEdit.Existing(it) },
                        onDelete = viewModel::deleteToneCard
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    EntryScenesSection(
                        scenes = state.entryScenes,
                        selectedSceneId = state.selectedSceneId,
                        onSelectScene = viewModel::selectScene,
                        onCreateScene = { showCreateSceneDialog = true },
                        onDeleteScene = viewModel::deleteEntryScene
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    GallerySection(
                        images = state.galleryImageEntities.map { entity ->
                            GalleryImage(entity, galleryBytesById[entity.imageStoreId])
                        },
                        onDeleteImage = viewModel::deleteGalleryImage,
                        onGenerateImage = { onOpenImageGeneration(state.persona.id) },
                        onImageClick = { bytes -> croppingImageBytes = bytes }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = viewModel::startChat,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.persona_detail_start_chat_button))
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Édition des cinq ressorts intimes (2026-08-22).
 *
 * Sauvegarde **à la perte du focus** plutôt qu'à chaque frappe : ces champs sont écrits d'un trait,
 * et un `upsert` par caractère provoquerait une écriture chiffrée par frappe pour rien. Le texte en
 * cours vit donc dans un état local, réinitialisé quand le persona change ou quand la valeur en base
 * bouge sous nos pieds (régénération, import).
 */
@Composable
private fun InterioritySection(
    persona: com.kitsune.core.data.local.entities.PersonaEntity,
    onTraitChange: (InteriorityTrait, String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.persona_interiority_section),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            stringResource(R.string.persona_interiority_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        InteriorityTrait.entries.forEach { trait ->
            val stored = trait.read(persona)
            var draft by remember(persona.id, trait, stored) { mutableStateOf(stored) }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(trait.label)) },
                placeholder = { Text(stringResource(trait.hint)) },
                singleLine = false,
                minLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .onFocusChanged { focus ->
                        if (!focus.isFocused && draft != stored) onTraitChange(trait, draft)
                    }
            )
        }
    }
}

@Composable
private fun PersonaHeader(
    persona: com.kitsune.core.data.local.entities.PersonaEntity,
    avatarBytes: ByteArray?,
    onAvatarClick: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAvatarClick),
                contentAlignment = Alignment.Center
            ) {
                if (avatarBytes != null) {
                    DecryptedImage(
                        bytes = avatarBytes,
                        contentDescription = stringResource(R.string.persona_detail_avatar_content_description, persona.name),
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(persona.name, style = MaterialTheme.typography.headlineSmall)
                var descriptionExpanded by remember(persona.id) { mutableStateOf(false) }
                Text(
                    persona.shortDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { descriptionExpanded = !descriptionExpanded },
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (persona.personality.isNotBlank()) {
            var personalityExpanded by remember(persona.id) { mutableStateOf(false) }
            Text(
                persona.personality,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { personalityExpanded = !personalityExpanded },
                maxLines = if (personalityExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        TagsEditor(
            tags = persona.tags,
            onAddTag = onAddTag,
            onRemoveTag = onRemoveTag,
            label = stringResource(R.string.persona_review_tags_label),
            placeholder = stringResource(R.string.tags_add_placeholder),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryScenesSection(
    scenes: List<EntrySceneEntity>,
    selectedSceneId: String?,
    onSelectScene: (String?) -> Unit,
    onCreateScene: () -> Unit,
    onDeleteScene: (EntrySceneEntity) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.persona_detail_entry_scenes_title), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onCreateScene) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.persona_detail_create_scene_content_description))
            }
        }

        if (scenes.isEmpty()) {
            Text(
                stringResource(R.string.persona_detail_no_scenes_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = selectedSceneId == null,
                    onClick = { onSelectScene(null) },
                    label = { Text(stringResource(R.string.persona_detail_default_scene_label)) }
                )
                scenes.forEach { scene ->
                    var showDelete by remember { mutableStateOf(false) }
                    if (showDelete) {
                        AlertDialog(
                            onDismissRequest = { showDelete = false },
                            title = { Text(stringResource(R.string.persona_detail_delete_scene_title, scene.title)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    onDeleteScene(scene)
                                    showDelete = false
                                }) { Text(stringResource(R.string.action_delete)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) }
                            }
                        )
                    }
                    FilterChip(
                        selected = selectedSceneId == scene.id,
                        onClick = { onSelectScene(scene.id) },
                        label = { Text(scene.title) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showDelete = true }
                            )
                        }
                    )
                }
            }

            val selectedScene = scenes.find { it.id == selectedSceneId }
            if (selectedScene != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(selectedScene.title, style = MaterialTheme.typography.titleSmall)
                        if (selectedScene.scenario.isNotBlank()) {
                            Text(
                                selectedScene.scenario,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        if (selectedScene.firstMessage.isNotBlank()) {
                            Text(
                                stringResource(R.string.persona_detail_quoted_text, selectedScene.firstMessage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GallerySection(
    images: List<GalleryImage>,
    onDeleteImage: (GalleryImage) -> Unit,
    onGenerateImage: () -> Unit,
    onImageClick: (ByteArray) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.persona_detail_gallery_title), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onGenerateImage) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = stringResource(R.string.persona_detail_generate_image_label))
            }
        }

        if (images.isEmpty()) {
            Text(
                stringResource(R.string.persona_detail_no_gallery_images_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(images, key = { it.entity.id }) { img ->
                    var showDelete by remember { mutableStateOf(false) }
                    if (showDelete) {
                        AlertDialog(
                            onDismissRequest = { showDelete = false },
                            title = { Text(stringResource(R.string.persona_detail_delete_image_title)) },
                            text = { Text(stringResource(R.string.persona_detail_delete_image_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    onDeleteImage(img)
                                    showDelete = false
                                }) { Text(stringResource(R.string.action_delete)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) }
                            }
                        )
                    }
                    Box(modifier = Modifier.size(100.dp)) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .combinedClickable(
                                    onClick = { img.bytes?.let { onImageClick(it) } },
                                    onLongClick = { showDelete = true }
                                )
                        ) {
                            if (img.bytes != null) {
                                DecryptedImage(
                                    bytes = img.bytes,
                                    contentDescription = img.entity.description,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateSceneDialog(
    onDismiss: () -> Unit,
    onGenerate: suspend (String) -> Result<SceneDraft>,
    onCreate: (title: String, scenario: String, firstMessage: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("") }
    var firstMessage by remember { mutableStateOf("") }
    var aiDescription by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.persona_detail_new_scene_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                AiQuickGenerateSection(
                    description = aiDescription,
                    onDescriptionChange = { aiDescription = it },
                    isGenerating = isGenerating,
                    onGenerate = {
                        scope.launch {
                            isGenerating = true
                            generationError = null
                            onGenerate(aiDescription)
                                .onSuccess { draft ->
                                    title = draft.title
                                    scenario = draft.scenario
                                    firstMessage = draft.firstMessage
                                }
                                .onFailure { e -> generationError = NetworkErrorMessages.forUser(e) }
                            isGenerating = false
                        }
                    }
                )
                generationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.persona_detail_scene_title_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scenario,
                    onValueChange = { scenario = it },
                    label = { Text(stringResource(R.string.persona_detail_scene_scenario_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = firstMessage,
                    onValueChange = { firstMessage = it },
                    label = { Text(stringResource(R.string.persona_detail_scene_first_message_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title, scenario, firstMessage) },
                enabled = title.isNotBlank() && !isGenerating
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Which tone card the dialog is open for: a brand-new one, or an existing card being edited. */
private sealed interface ToneCardEdit {
    data object New : ToneCardEdit
    data class Existing(val card: ToneCardEntity) : ToneCardEdit
}

/**
 * The persona's saved ways of telling stories (2026-08-24).
 *
 * Sits next to the entry scenes on purpose: a scene says *where a story starts*, a tone card says
 * *how it is told*, and both are per-character authored content that ships with the persona. Tapping
 * one opens it for editing; the trash icon deletes it after confirmation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToneCardsSection(
    toneCards: List<ToneCardEntity>,
    onCreate: () -> Unit,
    onEdit: (ToneCardEntity) -> Unit,
    onDelete: (ToneCardEntity) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.tone_cards_section_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tone_cards_add))
            }
        }
        Text(
            stringResource(R.string.tone_cards_section_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (toneCards.isEmpty()) {
            Text(
                stringResource(R.string.tone_cards_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            return@Column
        }
        FlowRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            toneCards.forEach { card ->
                var confirmDelete by remember(card.id) { mutableStateOf(false) }
                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text(stringResource(R.string.tone_card_delete_confirm)) },
                        text = { Text(card.name) },
                        confirmButton = {
                            TextButton(onClick = {
                                onDelete(card)
                                confirmDelete = false
                            }) { Text(stringResource(R.string.action_delete)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { onEdit(card) },
                    label = { Text(card.name) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { confirmDelete = true }
                        )
                    }
                )
            }
        }
    }
}
