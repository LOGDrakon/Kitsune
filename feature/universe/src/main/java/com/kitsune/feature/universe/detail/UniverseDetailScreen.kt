package com.kitsune.feature.universe.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Forum
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.FactionEntity
import com.kitsune.core.data.local.entities.LocationEntity
import com.kitsune.core.data.local.entities.NpcEntity
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.UniverseEntity
import com.kitsune.core.data.local.entities.UniverseImageEntity
import com.kitsune.core.designsystem.DecryptedImage
import com.kitsune.core.designsystem.TagsEditor
import com.kitsune.core.network.error.NetworkErrorMessages
import com.kitsune.core.designsystem.AiQuickGenerateSection
import com.kitsune.core.network.repository.FactionDraft
import com.kitsune.core.network.repository.LocationDraft
import com.kitsune.core.network.repository.NpcDraft
import com.kitsune.feature.universe.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniverseDetailScreen(
    universeId: String,
    onBack: () -> Unit,
    onOpenPersona: (personaId: String) -> Unit,
    onCreatePersona: (universeId: String) -> Unit,
    onOpenChat: (chatId: String) -> Unit,
    onCreateEnsembleChat: (universeId: String) -> Unit,
    viewModel: UniverseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showAddFactionDialog by remember { mutableStateOf(false) }
    var showAddNpcDialog by remember { mutableStateOf(false) }
    var showIncludePersonasDialog by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf(false) }
    var showAvatarChooser by remember { mutableStateOf(false) }
    var showGenerateAvatarDialog by remember { mutableStateOf(false) }
    var showGalleryPicker by remember { mutableStateOf(false) }
    var portraitTargetNpc by remember { mutableStateOf<NpcEntity?>(null) }
    var portraitTargetLocation by remember { mutableStateOf<LocationEntity?>(null) }
    var isPublic by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val avatarBytes by viewModel.avatarBytes.collectAsStateWithLifecycle()
    val galleryImages by viewModel.galleryImages.collectAsStateWithLifecycle()
    val isGeneratingAvatar by viewModel.isGeneratingAvatar.collectAsStateWithLifecycle()
    val npcAvatarBytes by viewModel.npcAvatarBytes.collectAsStateWithLifecycle()
    val locationAvatarBytes by viewModel.locationAvatarBytes.collectAsStateWithLifecycle()
    val generatingPortraitFor by viewModel.generatingPortraitFor.collectAsStateWithLifecycle()
    val includablePersonas by viewModel.includablePersonas.collectAsStateWithLifecycle()

    val devicePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) viewModel.setAvatarFromDevice(bytes)
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(uiState) {
        val ready = uiState as? UniverseDetailUiState.Ready
        if (ready != null) {
            isPublic = viewModel.checkIfPublished(ready.universe)
        }
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { actionError = null },
            title = { Text(stringResource(R.string.universe_detail_error_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { actionError = null }) { Text(stringResource(R.string.universe_detail_error_ok)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val state = uiState as? UniverseDetailUiState.Ready
                    Text(state?.universe?.name ?: stringResource(R.string.universe_detail_fallback_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    val readyState = uiState as? UniverseDetailUiState.Ready
                    // Hidden entirely for universes downloaded from the marketplace
                    // (sourceListingId set) — republishing someone else's downloaded creation as
                    // your own listing is not allowed (ViewModel enforces this too, as a backstop).
                    if (readyState != null && readyState.universe.sourceListingId == null) {
                    Text(stringResource(R.string.universe_detail_public_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(
                        checked = isPublic,
                        enabled = !isPublishing,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                showPublishDialog = true
                            } else {
                                readyState?.universe?.let { universe ->
                                    isPublishing = true
                                    scope.launch {
                                        try {
                                            viewModel.unpublishFromMarketplace(universe.id)
                                            isPublic = false
                                        } catch (e: Exception) {
                                            actionError = context.getString(R.string.universe_detail_publish_error_format, e.message ?: "")
                                        }
                                        isPublishing = false
                                    }
                                }
                            }
                        }
                    )
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is UniverseDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UniverseDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UniverseDetailUiState.Ready -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .clickable { showAvatarChooser = true },
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarBytes != null) {
                                    DecryptedImage(
                                        bytes = avatarBytes,
                                        contentDescription = stringResource(R.string.universe_detail_avatar_content_description, state.universe.name),
                                        modifier = Modifier.size(64.dp)
                                    )
                                } else {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (isGeneratingAvatar) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            } else {
                                                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(32.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    state.universe.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (state.universe.genre.isNotBlank()) {
                                    Text(
                                        stringResource(R.string.universe_detail_genre_format, state.universe.genre),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        TagsEditor(
                            tags = state.universe.tags,
                            onAddTag = viewModel::addTag,
                            onRemoveTag = viewModel::removeTag,
                            label = stringResource(R.string.universe_create_tags_label),
                            placeholder = stringResource(R.string.universe_tags_add_placeholder),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.universe_detail_locations_header_format, state.locations.size),
                            icon = Icons.Default.LocationCity,
                            onAdd = { showAddLocationDialog = true }
                        )
                    }

                    items(state.locations) { location ->
                        LocationCard(
                            location = location,
                            avatarBytes = locationAvatarBytes[location.id],
                            isGeneratingPortrait = generatingPortraitFor == location.id,
                            onDelete = { viewModel.deleteLocation(location) },
                            onGeneratePortrait = { portraitTargetLocation = location }
                        )
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.universe_detail_factions_header_format, state.factions.size),
                            icon = Icons.Default.Groups,
                            onAdd = { showAddFactionDialog = true }
                        )
                    }

                    items(state.factions) { faction ->
                        FactionCard(
                            faction = faction,
                            onDelete = { viewModel.deleteFaction(faction) }
                        )
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.universe_detail_npcs_header_format, state.npcs.size),
                            icon = Icons.Default.Person,
                            onAdd = { showAddNpcDialog = true }
                        )
                    }

                    items(state.npcs) { npc ->
                        NpcCard(
                            npc = npc,
                            avatarBytes = npcAvatarBytes[npc.id],
                            isGeneratingPortrait = generatingPortraitFor == npc.id,
                            onDelete = { viewModel.deleteNpc(npc) },
                            onGeneratePortrait = { portraitTargetNpc = npc }
                        )
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.universe_detail_personas_header_format, state.personas.size),
                            icon = Icons.Default.AccountCircle,
                            onAdd = { onCreatePersona(universeId) }
                        )
                        TextButton(onClick = {
                            viewModel.loadIncludablePersonas()
                            showIncludePersonasDialog = true
                        }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.universe_detail_include_persona_button))
                        }
                    }

                    items(state.personas, key = { it.id }) { persona ->
                        PersonaCard(
                            persona = persona,
                            onClick = { onOpenPersona(persona.id) }
                        )
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.universe_detail_ensemble_chats_header_format, state.ensembleChats.size),
                            icon = Icons.Default.Forum,
                            onAdd = { onCreateEnsembleChat(universeId) }
                        )
                        Text(
                            stringResource(R.string.universe_detail_ensemble_chat_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(state.ensembleChats, key = { it.id }) { chat ->
                        Card(modifier = Modifier.fillMaxWidth().clickable { onOpenChat(chat.id) }) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(chat.title, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }

        if (showAddLocationDialog) {
            AddLocationDialog(
                onGenerate = viewModel::generateLocation,
                onDismiss = { showAddLocationDialog = false },
                onAdd = { name, description, type ->
                    viewModel.addLocation(name, description, type)
                    showAddLocationDialog = false
                }
            )
        }

        if (showAddFactionDialog) {
            AddFactionDialog(
                onGenerate = viewModel::generateFaction,
                onDismiss = { showAddFactionDialog = false },
                onAdd = { name, description, type, alignment ->
                    viewModel.addFaction(name, description, type, alignment)
                    showAddFactionDialog = false
                }
            )
        }

        if (showAddNpcDialog) {
            AddNpcDialog(
                factions = (uiState as? UniverseDetailUiState.Ready)?.factions ?: emptyList(),
                locations = (uiState as? UniverseDetailUiState.Ready)?.locations ?: emptyList(),
                onGenerate = viewModel::generateNpc,
                onDismiss = { showAddNpcDialog = false },
                onAdd = { name, description, personality, role, factionId, locationId, age, physicalDescription ->
                    viewModel.addNpc(name, description, personality, role, factionId, locationId, age, physicalDescription)
                    showAddNpcDialog = false
                }
            )
        }

        if (showIncludePersonasDialog) {
            IncludePersonasDialog(
                candidates = includablePersonas,
                onDismiss = { showIncludePersonasDialog = false },
                onConfirm = { selectedIds ->
                    viewModel.includePersonas(selectedIds)
                    showIncludePersonasDialog = false
                }
            )
        }

        if (showPublishDialog) {
            val state = uiState as? UniverseDetailUiState.Ready
            var publishDescription by remember { mutableStateOf("") }
            var isDialogPublishing by remember { mutableStateOf(false) }
            var publishResult by remember { mutableStateOf<String?>(null) }
            var publishError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { if (!isDialogPublishing) showPublishDialog = false },
                title = { Text(stringResource(R.string.universe_detail_publish_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.universe_detail_publish_description_format, state?.universe?.name ?: ""),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = publishDescription,
                            onValueChange = { publishDescription = it },
                            label = { Text(stringResource(R.string.universe_detail_publish_description_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.universe_detail_publish_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        publishResult?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (publishError)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (state != null) {
                                isDialogPublishing = true
                                scope.launch {
                                    try {
                                        viewModel.publishToMarketplace(
                                            universe = state.universe,
                                            factions = state.factions,
                                            locations = state.locations,
                                            npcs = state.npcs,
                                            personas = state.personas,
                                            description = publishDescription.ifBlank { state.universe.description }
                                        )
                                        publishResult = context.getString(R.string.universe_detail_publish_success)
                                        publishError = false
                                        isPublic = true
                                    } catch (e: Exception) {
                                        publishResult = context.getString(R.string.universe_detail_publish_error_format, e.message ?: "")
                                        publishError = true
                                    }
                                    isDialogPublishing = false
                                }
                            }
                        },
                        enabled = !isDialogPublishing && state != null
                    ) {
                        if (isDialogPublishing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text(stringResource(R.string.universe_detail_publish_confirm))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPublishDialog = false }, enabled = !isDialogPublishing) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            )
        }

        if (showAvatarChooser) {
            AlertDialog(
                onDismissRequest = { showAvatarChooser = false },
                title = { Text(stringResource(R.string.universe_detail_avatar_dialog_title)) },
                text = { Text(stringResource(R.string.universe_detail_avatar_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showAvatarChooser = false
                        showGenerateAvatarDialog = true
                    }) { Text(stringResource(R.string.universe_detail_generate_with_ai_button)) }
                },
                dismissButton = {
                    Row {
                        if (galleryImages.isNotEmpty()) {
                            TextButton(onClick = {
                                showAvatarChooser = false
                                showGalleryPicker = true
                            }) { Text(stringResource(R.string.universe_detail_choose_from_universe_gallery_button)) }
                        }
                        TextButton(onClick = {
                            showAvatarChooser = false
                            devicePickerLauncher.launch("image/*")
                        }) { Text(stringResource(R.string.universe_detail_choose_from_device_button)) }
                    }
                }
            )
        }

        if (showGenerateAvatarDialog) {
            GenerateImageDescriptionDialog(
                title = stringResource(R.string.universe_detail_generate_avatar_title),
                onDismiss = { showGenerateAvatarDialog = false },
                onGenerate = { description ->
                    viewModel.generateUniverseAvatar(description)
                    showGenerateAvatarDialog = false
                }
            )
        }

        if (showGalleryPicker) {
            val galleryBytesById by viewModel.galleryBytesById.collectAsStateWithLifecycle()
            AlertDialog(
                onDismissRequest = { showGalleryPicker = false },
                title = { Text(stringResource(R.string.universe_detail_choose_from_universe_gallery_button)) },
                text = {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(galleryImages, key = { it.id }) { image ->
                            GalleryThumbnail(
                                bytes = galleryBytesById[image.imageStoreId],
                                onClick = {
                                    viewModel.setAvatarFromGallery(image)
                                    showGalleryPicker = false
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGalleryPicker = false }) { Text(stringResource(R.string.common_close)) }
                }
            )
        }

        portraitTargetNpc?.let { npc ->
            GenerateImageDescriptionDialog(
                title = stringResource(R.string.universe_detail_generate_npc_portrait_title, npc.name),
                onDismiss = { portraitTargetNpc = null },
                onGenerate = { description ->
                    viewModel.generateNpcImage(npc, description)
                    portraitTargetNpc = null
                }
            )
        }

        portraitTargetLocation?.let { location ->
            GenerateImageDescriptionDialog(
                title = stringResource(R.string.universe_detail_generate_location_image_title, location.name),
                onDismiss = { portraitTargetLocation = null },
                onGenerate = { description ->
                    viewModel.generateLocationImage(location, description)
                    portraitTargetLocation = null
                }
            )
        }
    }
}

@Composable
private fun GalleryThumbnail(bytes: ByteArray?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        bytes?.let {
            DecryptedImage(bytes = it, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun GenerateImageDescriptionDialog(
    title: String,
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text(stringResource(R.string.universe_detail_generate_image_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onGenerate(description) }) { Text(stringResource(R.string.universe_detail_action_generate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_add))
        }
    }
}

@Composable
private fun LocationCard(
    location: LocationEntity,
    avatarBytes: ByteArray?,
    isGeneratingPortrait: Boolean,
    onDelete: () -> Unit,
    onGeneratePortrait: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            WorldElementThumbnail(
                bytes = avatarBytes,
                isGenerating = isGeneratingPortrait,
                onGenerateClick = onGeneratePortrait
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(location.name, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    location.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    stringResource(R.string.universe_detail_type_format, location.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** Small square avatar/portrait shown on NPC/location cards — tap generates or regenerates it. */
@Composable
private fun WorldElementThumbnail(
    bytes: ByteArray?,
    isGenerating: Boolean,
    onGenerateClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !isGenerating, onClick = onGenerateClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            isGenerating -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
            bytes != null -> DecryptedImage(bytes = bytes, contentDescription = null, modifier = Modifier.fillMaxSize())
            else -> Icon(Icons.Default.AutoAwesome, contentDescription = stringResource(R.string.universe_detail_generate_portrait_content_description), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FactionCard(
    faction: FactionEntity,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(faction.name, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), modifier = Modifier.size(16.dp))
                }
            }
            Text(
                faction.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                faction.alignment?.let { stringResource(R.string.universe_detail_type_alignment_format, faction.type, it) } ?: stringResource(R.string.universe_detail_type_format, faction.type),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun NpcCard(
    npc: NpcEntity,
    avatarBytes: ByteArray?,
    isGeneratingPortrait: Boolean,
    onDelete: () -> Unit,
    onGeneratePortrait: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp)) {
            WorldElementThumbnail(
                bytes = avatarBytes,
                isGenerating = isGeneratingPortrait,
                onGenerateClick = onGeneratePortrait
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(npc.name, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    npc.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    npc.age?.let { stringResource(R.string.universe_detail_role_age_format, npc.role, it) } ?: stringResource(R.string.universe_detail_role_format, npc.role),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                npc.physicalDescription?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonaCard(
    persona: PersonaEntity,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(persona.name, style = MaterialTheme.typography.titleSmall)
            Text(
                persona.shortDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun AddLocationDialog(
    onGenerate: suspend (String) -> Result<LocationDraft>,
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var aiDescription by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.universe_detail_add_location_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
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
                                    name = draft.name
                                    description = draft.description
                                    type = draft.type
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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.field_description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(stringResource(R.string.universe_detail_location_type_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, description, type) },
                enabled = name.isNotBlank() && description.isNotBlank() && type.isNotBlank() && !isGenerating
            ) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun AddFactionDialog(
    onGenerate: suspend (String) -> Result<FactionDraft>,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var alignment by remember { mutableStateOf("") }
    var aiDescription by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.universe_detail_add_faction_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
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
                                    name = draft.name
                                    description = draft.description
                                    type = draft.type
                                    alignment = draft.alignment
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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.field_description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(stringResource(R.string.universe_detail_faction_type_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = alignment,
                    onValueChange = { alignment = it },
                    label = { Text(stringResource(R.string.universe_detail_faction_alignment_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, description, type, alignment.takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank() && description.isNotBlank() && type.isNotBlank() && !isGenerating
            ) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddNpcDialog(
    factions: List<FactionEntity>,
    locations: List<LocationEntity>,
    onGenerate: suspend (String) -> Result<NpcDraft>,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String?, String?, Int?, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var physicalDescription by remember { mutableStateOf("") }
    var selectedFactionId by remember { mutableStateOf<String?>(null) }
    var selectedLocationId by remember { mutableStateOf<String?>(null) }
    var ageText by remember { mutableStateOf("") }
    var aiDescription by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.universe_detail_add_npc_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
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
                                    name = draft.name
                                    description = draft.description
                                    personality = draft.personality
                                    role = draft.role
                                    physicalDescription = draft.physicalDescription
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
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.field_description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = personality,
                    onValueChange = { personality = it },
                    label = { Text(stringResource(R.string.universe_detail_npc_personality_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text(stringResource(R.string.universe_detail_npc_role_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = physicalDescription,
                    onValueChange = { physicalDescription = it },
                    label = { Text(stringResource(R.string.universe_detail_npc_physical_description_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it },
                    label = { Text(stringResource(R.string.universe_detail_npc_age_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (factions.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = factions.find { it.id == selectedFactionId }?.name ?: stringResource(R.string.universe_detail_no_faction),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.universe_detail_faction_label)) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.universe_detail_no_faction)) },
                                onClick = {
                                    selectedFactionId = null
                                    expanded = false
                                }
                            )
                            factions.forEach { faction ->
                                DropdownMenuItem(
                                    text = { Text(faction.name) },
                                    onClick = {
                                        selectedFactionId = faction.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                if (locations.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = locations.find { it.id == selectedLocationId }?.name ?: stringResource(R.string.universe_detail_no_location),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.universe_detail_location_label)) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.universe_detail_no_location)) },
                                onClick = {
                                    selectedLocationId = null
                                    expanded = false
                                }
                            )
                            locations.forEach { location ->
                                DropdownMenuItem(
                                    text = { Text(location.name) },
                                    onClick = {
                                        selectedLocationId = location.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val age = ageText.toIntOrNull()
                    onAdd(name, description, personality, role, selectedFactionId, selectedLocationId, age, physicalDescription.takeIf { it.isNotBlank() })
                },
                enabled = name.isNotBlank() && description.isNotBlank() && personality.isNotBlank() && role.isNotBlank() && !isGenerating
            ) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/** Lets the user tie one or several existing standalone personas (not yet part of any universe)
 * to this one — the counterpart to [onCreatePersona]'s "brand-new persona" flow, for a persona
 * that already exists but wasn't created from inside a universe. */
@Composable
private fun IncludePersonasDialog(
    candidates: List<PersonaEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.universe_detail_include_persona_dialog_title)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.universe_detail_include_persona_empty))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    candidates.forEach { persona ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (persona.id in selectedIds) {
                                        selectedIds - persona.id
                                    } else {
                                        selectedIds + persona.id
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = persona.id in selectedIds,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + persona.id else selectedIds - persona.id
                                }
                            )
                            Text(persona.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds) },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text(stringResource(R.string.universe_detail_include_persona_confirm, selectedIds.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
