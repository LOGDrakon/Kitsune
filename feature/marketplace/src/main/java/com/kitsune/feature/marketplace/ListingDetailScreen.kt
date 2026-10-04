package com.kitsune.feature.marketplace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import com.kitsune.core.backend.model.ListingDetail
import com.kitsune.core.backend.model.ListingSummary
import com.kitsune.core.backend.model.MarketplaceFactionData
import com.kitsune.core.backend.model.MarketplaceLocationData
import com.kitsune.core.backend.model.MarketplaceNpcData
import com.kitsune.core.backend.model.MarketplacePersonaData
import com.kitsune.core.designsystem.ImageViewerDialog
import com.kitsune.core.designsystem.KitsuneTheme

// Compact, portrait-oriented card for a 2-column grid — enough of these fit on one screen at once
// (demande explicite : "des fiches verticales et plus nombreuses sur un seul écran (4)") that
// browsing the marketplace no longer means scrolling past one wide row at a time. The long
// description is dropped from the card itself (no room in this format) — full details are one tap
// away on the listing's own screen.
@Composable
internal fun ListingCard(
    listing: ListingSummary,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
    onOpenCreator: ((String) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Portrait preview image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (listing.previewImageUrl != null) {
                    AsyncImage(
                        model = listing.previewImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Gradient overlay for better text readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.75f)
                                    ),
                                    startY = 60f
                                )
                            )
                    )
                }

                // Follow toggle — top-left, mirroring the type badge's top-right position.
                IconButton(
                    onClick = onToggleFollow,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        if (listing.isFollowingCreator) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(if (listing.isFollowingCreator) R.string.unfollow_creator_button else R.string.follow_creator_button),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Type badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(
                            when (listing.type) {
                                "PERSONA" -> R.string.listing_type_persona_badge
                                "PRESET_PACK" -> R.string.listing_type_pack_badge
                                else -> R.string.listing_type_universe_badge
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Title and creator at bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        listing.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        stringResource(R.string.listing_by_creator_format, listing.creatorName ?: stringResource(R.string.anonymous_creator_name)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        modifier = if (onOpenCreator != null) {
                            Modifier.clickable { onOpenCreator(listing.creatorId) }
                        } else {
                            Modifier
                        }
                    )
                }
            }

            // Compact stats row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (listing.genre.isNotBlank()) {
                    Text(
                        listing.genre,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (listing.reviewCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Star,
                                null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                String.format("%.1f", listing.averageRating),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${listing.downloadCount}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    listingId: String,
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit = {},
    viewModel: MarketplaceViewModel = hiltViewModel()
) {
    val listing by viewModel.selectedListing.collectAsState()
    var downloadOutcome by remember { mutableStateOf<DownloadOutcome?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var isSubmittingReport by remember { mutableStateOf(false) }
    var reportOutcome by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(listingId) { viewModel.loadListingDetail(listingId) }

    if (showReportDialog) {
        ReportListingDialog(
            isSubmitting = isSubmittingReport,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, description ->
                isSubmittingReport = true
                viewModel.reportListing(listingId, reason, description) { success ->
                    isSubmittingReport = false
                    showReportDialog = false
                    reportOutcome = success
                }
            }
        )
    }

    reportOutcome?.let { success ->
        AlertDialog(
            onDismissRequest = { reportOutcome = null },
            title = { Text(stringResource(if (success) R.string.report_success_title else R.string.report_error_title)) },
            text = { Text(stringResource(if (success) R.string.report_success_message else R.string.report_error_message)) },
            confirmButton = { TextButton(onClick = { reportOutcome = null }) { Text(stringResource(R.string.action_ok)) } }
        )
    }

    val translateError by viewModel.translateError.collectAsState()
    translateError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissTranslateError() },
            title = { Text(stringResource(R.string.listing_detail_error_translation_fallback)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.dismissTranslateError() }) { Text(stringResource(R.string.action_ok)) } }
        )
    }

    KitsuneTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.listing_detail_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_desc_back)) } },
                    actions = {
                        if (listing?.personaData != null) {
                            val isTranslating by viewModel.isTranslating.collectAsState()
                            if (isTranslating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = { viewModel.translateListing(listingId) }) {
                                    Text(stringResource(R.string.listing_detail_translate_action))
                                }
                            }
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_more_options))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.report_listing_action)) },
                                onClick = {
                                    showMenu = false
                                    showReportDialog = true
                                }
                            )
                        }
                    }
                )
            }
        ) { padding ->
            listing?.let { detail ->
                ListingDetailContent(
                    listing = detail,
                    isDownloading = isDownloading,
                    downloadOutcome = downloadOutcome,
                    onDownload = {
                        if (!isDownloading) {
                            isDownloading = true
                            viewModel.downloadListing(listingId) { outcome ->
                                isDownloading = false
                                downloadOutcome = outcome
                            }
                        }
                    },
                    onToggleFollow = { creatorId, currentlyFollowing -> viewModel.toggleFollowCreator(creatorId, currentlyFollowing) },
                    onOpenCreator = onOpenCreator,
                    onRate = { stars -> viewModel.rateListing(listingId, stars) },
                    isOwnListing = detail.creatorId == viewModel.ownUserId,
                    modifier = Modifier.padding(padding)
                )
            } ?: Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

private data class ReportReasonOption(val code: String, val labelRes: Int)

private val REPORT_REASON_OPTIONS = listOf(
    ReportReasonOption("INAPPROPRIATE", R.string.report_reason_inappropriate),
    ReportReasonOption("MINOR_CONTENT", R.string.report_reason_minor_content),
    ReportReasonOption("SPAM", R.string.report_reason_spam),
    ReportReasonOption("COPYRIGHT", R.string.report_reason_copyright),
    ReportReasonOption("OTHER", R.string.report_reason_other)
)

@Composable
private fun ReportListingDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, description: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf(REPORT_REASON_OPTIONS.first().code) }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.report_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.report_reason_label), style = MaterialTheme.typography.labelLarge)
                REPORT_REASON_OPTIONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isSubmitting) { selectedReason = option.code },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedReason == option.code, onClick = { selectedReason = option.code }, enabled = !isSubmitting)
                        Text(stringResource(option.labelRes))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.report_description_label)) },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(selectedReason, description) },
                enabled = !isSubmitting
            ) { Text(stringResource(R.string.report_send_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListingDetailContent(
    listing: ListingDetail,
    isDownloading: Boolean,
    downloadOutcome: DownloadOutcome?,
    onDownload: () -> Unit,
    onToggleFollow: (creatorId: String, currentlyFollowing: Boolean) -> Unit,
    onOpenCreator: (String) -> Unit = {},
    onRate: (Int) -> Unit = {},
    isOwnListing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgImageUrl = listing.previewImageUrl ?: listing.imageUrls.firstOrNull()
    val scrollState = rememberScrollState()
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    // Collapsible sections state
    var personaExpanded by remember { mutableStateOf(true) }
    var universeExpanded by remember { mutableStateOf(true) }
    var factionsExpanded by remember { mutableStateOf(true) }
    var locationsExpanded by remember { mutableStateOf(true) }
    var npcsExpanded by remember { mutableStateOf(true) }
    var personasExpanded by remember { mutableStateOf(true) }
    var galleryExpanded by remember { mutableStateOf(true) }

    viewerIndex?.let { index ->
        ImageViewerDialog(
            imageUrls = listing.imageUrls,
            initialIndex = index,
            onDismiss = { viewerIndex = null }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (bgImageUrl != null) {
            AsyncImage(
                model = bgImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable { viewerIndex = 0 }
            )
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), MaterialTheme.colorScheme.surface))))
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp)) {
            Spacer(Modifier.height(48.dp))
            
            // Title section
            Text(listing.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (bgImageUrl != null) Color.White else MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.listing_by_creator_format, listing.creatorName ?: stringResource(R.string.anonymous_creator_name)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (bgImageUrl != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { onOpenCreator(listing.creatorId) }
                )
                TextButton(onClick = { onToggleFollow(listing.creatorId, listing.isFollowingCreator) }) {
                    Text(
                        stringResource(if (listing.isFollowingCreator) R.string.unfollow_creator_button else R.string.follow_creator_button),
                        color = if (bgImageUrl != null) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            
            // Description
            Text(listing.description, style = MaterialTheme.typography.bodyLarge)
            
            Spacer(Modifier.height(16.dp))
            
            // Download button
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.price_free_label), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                Button(onClick = onDownload, enabled = !isDownloading) {
                    if (isDownloading) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) } else { Text(stringResource(R.string.download_button_label)) }
                }
            }
            downloadOutcome?.let { outcome ->
                Spacer(Modifier.height(8.dp))
                val message = when (outcome) {
                    DownloadOutcome.Success -> stringResource(R.string.download_success_message)
                    DownloadOutcome.AlreadyOwned -> stringResource(R.string.download_already_owned_message)
                    DownloadOutcome.Failed -> stringResource(R.string.download_error_message)
                }
                val color = if (outcome == DownloadOutcome.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Text(message, style = MaterialTheme.typography.bodySmall, color = color)
            }

            // Stars only, once downloaded, never one's own listing (PRINCIPLES.md §6: no review text).
            val canRate = !isOwnListing && (listing.isOwned ||
                downloadOutcome == DownloadOutcome.Success || downloadOutcome == DownloadOutcome.AlreadyOwned)
            if (canRate) {
                Spacer(Modifier.height(12.dp))
                RatingRow(current = listing.myRating, onRate = onRate)
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Genre and tags
            if (listing.genre.isNotBlank()) Text(stringResource(R.string.genre_label_format, listing.genre), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            if (listing.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listing.tags.forEach { tag -> SuggestionChip(onClick = {}, label = { Text(tag) }) }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Stats
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (listing.reviewCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.rating_reviews_format, String.format("%.1f", listing.averageRating), listing.reviewCount), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.KeyboardArrowDown, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.download_count_format, listing.downloadCount), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Gallery section
            if (listing.imageUrls.size > 1) {
                CollapsibleSection(
                    title = stringResource(R.string.gallery_section_title_format, listing.imageUrls.size - 1),
                    expanded = galleryExpanded,
                    onToggle = { galleryExpanded = !galleryExpanded }
                ) {
                    listing.imageUrls.drop(1).forEachIndexed { index, url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewerIndex = index + 1 }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            
            // PRESET PACK section: what each preset does, before the user adds them to their library.
            listing.presetData?.let { pack ->
                CollapsibleSection(
                    title = stringResource(R.string.preset_pack_section_title, pack.presets.size),
                    expanded = personaExpanded,
                    onToggle = { personaExpanded = !personaExpanded }
                ) {
                    Text(
                        stringResource(R.string.preset_pack_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    pack.presets.forEach { preset ->
                        Spacer(Modifier.height(8.dp))
                        Text(preset.name, style = MaterialTheme.typography.titleSmall)
                        if (preset.description.isNotBlank()) {
                            Text(preset.description, style = MaterialTheme.typography.bodySmall)
                        }
                        if (preset.directive.isNotBlank()) {
                            Text(preset.directive, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // PERSONA section
            listing.personaData?.let { persona ->
                CollapsibleSection(
                    title = stringResource(R.string.persona_sheet_title),
                    expanded = personaExpanded,
                    onToggle = { personaExpanded = !personaExpanded }
                ) {
                    InfoRow(stringResource(R.string.label_name), persona.name)
                    InfoRow(stringResource(R.string.label_description), persona.shortDescription)
                    InfoRow(stringResource(R.string.label_personality), persona.personality)
                    InfoRow(stringResource(R.string.label_scenario), persona.scenario)
                    if (persona.firstMessage.isNotBlank()) InfoRow(stringResource(R.string.label_first_message), persona.firstMessage)
                    InfoRow(stringResource(R.string.label_age), persona.age.toString())
                    if (persona.maturityTags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.maturity_tags_format, persona.maturityTags.joinToString(", ")), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // UNIVERSE section
            listing.universeData?.let { universe ->
                CollapsibleSection(
                    title = stringResource(R.string.universe_sheet_title),
                    expanded = universeExpanded,
                    onToggle = { universeExpanded = !universeExpanded }
                ) {
                    InfoRow(stringResource(R.string.label_name), universe.name)
                    InfoRow(stringResource(R.string.label_description), universe.description)
                    if (universe.genre.isNotBlank()) InfoRow(stringResource(R.string.label_genre), universe.genre)
                    val vStyle = universe.visualStyle
                    if (!vStyle.isNullOrBlank()) InfoRow(stringResource(R.string.label_visual_style), vStyle)
                }

                // Factions
                if (universe.factions.isNotEmpty()) {
                    CollapsibleSection(
                        title = stringResource(R.string.factions_section_title_format, universe.factions.size),
                        expanded = factionsExpanded,
                        onToggle = { factionsExpanded = !factionsExpanded }
                    ) {
                        universe.factions.forEach { faction ->
                            FactionDetailCard(faction)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // Locations
                if (universe.locations.isNotEmpty()) {
                    CollapsibleSection(
                        title = stringResource(R.string.locations_section_title_format, universe.locations.size),
                        expanded = locationsExpanded,
                        onToggle = { locationsExpanded = !locationsExpanded }
                    ) {
                        universe.locations.forEach { location ->
                            LocationDetailCard(location)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // NPCs
                if (universe.npcs.isNotEmpty()) {
                    CollapsibleSection(
                        title = stringResource(R.string.npcs_section_title_format, universe.npcs.size),
                        expanded = npcsExpanded,
                        onToggle = { npcsExpanded = !npcsExpanded }
                    ) {
                        universe.npcs.forEach { npc ->
                            NpcDetailCard(npc)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // Personas
                if (universe.personas.isNotEmpty()) {
                    CollapsibleSection(
                        title = stringResource(R.string.playable_personas_section_title_format, universe.personas.size),
                        expanded = personasExpanded,
                        onToggle = { personasExpanded = !personasExpanded }
                    ) {
                        universe.personas.forEach { persona ->
                            PersonaSummaryCard(persona)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(8.dp)) }
}

@Composable
private fun FactionDetailCard(faction: MarketplaceFactionData) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(faction.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(faction.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.type_label_format, faction.type) + (faction.alignment?.let { stringResource(R.string.alignment_suffix_format, it) } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LocationDetailCard(location: MarketplaceLocationData) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(location.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(location.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.type_label_format, location.type), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NpcDetailCard(npc: MarketplaceNpcData) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(npc.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(npc.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.role_label_format, npc.role) + (npc.age?.let { stringResource(R.string.age_suffix_format, it.toString()) } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (npc.personality.isNotBlank()) Text(stringResource(R.string.npc_personality_format, npc.personality), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersonaSummaryCard(persona: MarketplacePersonaData) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(persona.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(persona.shortDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.persona_summary_age_tags_format, persona.age, persona.maturityTags.joinToString(", ")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Five tappable stars. Shows the user's current rating; tapping another star replaces it. */
@Composable
private fun RatingRow(current: Int?, onRate: (Int) -> Unit) {
    var shown by remember(current) { mutableStateOf(current ?: 0) }
    Column {
        Text(
            stringResource(if (current == null) R.string.rating_prompt else R.string.rating_yours),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row {
            (1..5).forEach { star ->
                IconButton(onClick = { shown = star; onRate(star) }) {
                    Icon(
                        if (star <= shown) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = stringResource(R.string.rating_star_content_description, star),
                        tint = if (star <= shown) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
