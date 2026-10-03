package com.kitsune.feature.chat.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.data.local.entities.KeyMomentEntity
import com.kitsune.core.data.local.entities.MomentType
import com.kitsune.core.data.local.entities.StoryMood
import com.kitsune.feature.chat.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onBack: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val moments by viewModel.moments.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val isLoadingSuggestions by viewModel.isLoadingSuggestions.collectAsStateWithLifecycle()
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }
    val theme = TimelineThemeId.fromCosmeticId(viewModel.appliedThemeId)

    val topBarColors = when (theme) {
        TimelineThemeId.GOTHIC -> TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF14060A),
            titleContentColor = Color(0xFFF0E6D2),
            navigationIconContentColor = Color(0xFFC9A227)
        )
        TimelineThemeId.MANGA -> TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFFF7F5F0),
            titleContentColor = Color(0xFF1A1A1A),
            navigationIconContentColor = Color(0xFF1A1A1A)
        )
        else -> TopAppBarDefaults.topAppBarColors()
    }
    val fabColors = when (theme) {
        TimelineThemeId.GOTHIC -> Color(0xFFC9A227) to Color(0xFF14060A)
        TimelineThemeId.MANGA -> Color(0xFF1A1A1A) to Color.White
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timeline_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.timeline_back))
                    }
                },
                colors = topBarColors
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.generateDirections() },
                icon = { Icon(Icons.Default.Lightbulb, null) },
                text = { Text(stringResource(R.string.timeline_script_doctor)) },
                containerColor = fabColors?.first ?: FloatingActionButtonDefaults.containerColor,
                contentColor = fabColors?.second ?: MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        val backgroundContent: @Composable (@Composable () -> Unit) -> Unit = when (theme) {
            TimelineThemeId.GOTHIC -> { inner -> GothicBackground(modifier = Modifier.padding(padding)) { inner() } }
            TimelineThemeId.MANGA -> { inner -> MangaBackground(modifier = Modifier.padding(padding)) { inner() } }
            TimelineThemeId.WATERCOLOR -> { inner -> WatercolorBackground(modifier = Modifier.padding(padding)) { inner() } }
            null -> { inner -> Box(modifier = Modifier.fillMaxSize().padding(padding)) { inner() } }
        }

        backgroundContent {
            if (moments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val mutedColor = when (theme) {
                        TimelineThemeId.GOTHIC -> Color(0xFFF0E6D2).copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.timeline_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = mutedColor
                        )
                        Text(
                            stringResource(R.string.timeline_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(moments, key = { it.id }) { moment ->
                        val isExpanded = expandedStates[moment.id] ?: false
                        when (theme) {
                            TimelineThemeId.GOTHIC -> GothicTimelineCard(
                                moment = moment,
                                isExpanded = isExpanded,
                                onToggleExpand = { expandedStates[moment.id] = !isExpanded },
                                onDelete = { viewModel.deleteMoment(moment) }
                            )
                            TimelineThemeId.MANGA -> MangaTimelineCard(
                                moment = moment,
                                isExpanded = isExpanded,
                                onToggleExpand = { expandedStates[moment.id] = !isExpanded },
                                onDelete = { viewModel.deleteMoment(moment) }
                            )
                            TimelineThemeId.WATERCOLOR -> WatercolorTimelineCard(
                                moment = moment,
                                isExpanded = isExpanded,
                                onToggleExpand = { expandedStates[moment.id] = !isExpanded },
                                onDelete = { viewModel.deleteMoment(moment) }
                            )
                            null -> DefaultTimelineCard(
                                moment = moment,
                                isExpanded = isExpanded,
                                onToggleExpand = { expandedStates[moment.id] = !isExpanded },
                                onDelete = { viewModel.deleteMoment(moment) }
                            )
                        }
                    }
                }
            }
        }

        if (isLoadingSuggestions) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        suggestions?.let { dirs ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissSuggestions() },
                title = { Text(stringResource(R.string.timeline_script_doctor)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.timeline_script_doctor_cost),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        dirs.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissSuggestions() }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Default (no theme owned) — unchanged from the original plain Material 3 look.
// ---------------------------------------------------------------------------------------------

@Composable
private fun DefaultTimelineCard(
    moment: KeyMomentEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Text(moodEmoji(moment.mood), style = MaterialTheme.typography.headlineMedium)
                    Column {
                        Text(moment.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            momentTypeLabel(moment.momentType),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TimelineCardActions(onToggleExpand, onDelete)
            }

            Text(moment.summary, style = MaterialTheme.typography.bodyMedium)

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        stringResource(R.string.timeline_snippets),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        moment.snippets,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Gothic Chronicle
// ---------------------------------------------------------------------------------------------

@Composable
private fun GothicTimelineCard(
    moment: KeyMomentEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit
) {
    val ink = Color(0xFFF0E6D2)
    val mutedInk = ink.copy(alpha = 0.65f)
    val gold = Color(0xFFC9A227)
    GothicCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                GothicTitle(moment.title)
                Spacer(Modifier.size(4.dp))
                Text(
                    momentTypeLabel(moment.momentType),
                    style = MaterialTheme.typography.labelSmall,
                    color = gold
                )
            }
            TimelineCardActions(onToggleExpand, onDelete, tint = mutedInk)
        }

        Spacer(Modifier.size(10.dp))
        Text(moment.summary, style = MaterialTheme.typography.bodyMedium, color = ink)

        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = gold.copy(alpha = 0.4f))
                Text(
                    stringResource(R.string.timeline_snippets),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = gold
                )
                Spacer(Modifier.size(4.dp))
                Text(moment.snippets, style = MaterialTheme.typography.bodySmall, color = mutedInk)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Manga Frise
// ---------------------------------------------------------------------------------------------

@Composable
private fun MangaTimelineCard(
    moment: KeyMomentEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit
) {
    val ink = Color(0xFF1A1A1A)
    MangaCard(mood = moment.mood) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(moodEmoji(moment.mood), style = MaterialTheme.typography.headlineMedium)
                Column {
                    Text(moment.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = ink)
                    MangaMoodBadge(moment.mood, momentTypeLabel(moment.momentType))
                }
            }
            TimelineCardActions(onToggleExpand, onDelete, tint = ink)
        }

        Spacer(Modifier.size(10.dp))
        Text(moment.summary, style = MaterialTheme.typography.bodyMedium, color = ink)

        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = ink.copy(alpha = 0.2f))
                Text(
                    stringResource(R.string.timeline_snippets),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ink
                )
                Spacer(Modifier.size(4.dp))
                Text(moment.snippets, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Aquarelle & Souvenirs
// ---------------------------------------------------------------------------------------------

@Composable
private fun WatercolorTimelineCard(
    moment: KeyMomentEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: () -> Unit
) {
    val ink = Color(0xFF3A3A3A)
    WatercolorCard(mood = moment.mood) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WatercolorMoodBlob(moodEmoji(moment.mood), moment.mood)
                Column {
                    Text(moment.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = ink)
                    Text(
                        momentTypeLabel(moment.momentType),
                        style = MaterialTheme.typography.labelSmall,
                        color = ink.copy(alpha = 0.6f)
                    )
                }
            }
            TimelineCardActions(onToggleExpand, onDelete, tint = ink)
        }

        Spacer(Modifier.size(10.dp))
        Text(moment.summary, style = MaterialTheme.typography.bodyMedium, color = ink)

        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = ink.copy(alpha = 0.15f))
                Text(
                    stringResource(R.string.timeline_snippets),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ink
                )
                Spacer(Modifier.size(4.dp))
                Text(moment.snippets, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun TimelineCardActions(onToggleExpand: () -> Unit, onDelete: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggleExpand) {
            Icon(Icons.Default.ExpandMore, stringResource(R.string.timeline_expand), tint = tint, modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, stringResource(R.string.timeline_delete), tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun moodEmoji(mood: StoryMood): String = when (mood) {
    StoryMood.TENDER -> "🤩"
    StoryMood.ROMANTIC -> "❤️"
    StoryMood.DRAMATIC -> "🎥"
    StoryMood.HUMOROUS -> "😄"
    StoryMood.DARK -> "🌑"
    StoryMood.TENSE -> "😰"
    StoryMood.MELANCHOLIC -> "😢"
    StoryMood.EXCITING -> "⚡"
    StoryMood.NEUTRAL -> "📖"
}

@Composable
private fun momentTypeLabel(type: MomentType): String = when (type) {
    MomentType.FIRST_MEETING -> stringResource(R.string.timeline_type_first_meeting)
    MomentType.CONFESSION -> stringResource(R.string.timeline_type_confession)
    MomentType.BREAKUP -> stringResource(R.string.timeline_type_breakup)
    MomentType.NSFW_SCENE -> stringResource(R.string.timeline_type_nsfw_scene)
    MomentType.PLOT_TWIST -> stringResource(R.string.timeline_type_plot_twist)
    MomentType.EMOTIONAL_PEAK -> stringResource(R.string.timeline_type_emotional_peak)
    MomentType.CONFLICT -> stringResource(R.string.timeline_type_conflict)
    MomentType.RECONCILIATION -> stringResource(R.string.timeline_type_reconciliation)
    MomentType.CUSTOM -> stringResource(R.string.timeline_type_custom)
}
