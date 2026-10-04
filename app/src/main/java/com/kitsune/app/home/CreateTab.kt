package com.kitsune.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.data.local.entities.GenerationJobEntity
import com.kitsune.core.data.local.entities.GenerationJobState
import com.kitsune.core.data.local.entities.PersonaEntity
import com.kitsune.core.data.local.entities.UniverseEntity
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.AvatarSize
import com.kitsune.core.designsystem.component.KitsuneAvatar
import com.kitsune.core.designsystem.component.KitsuneEmptyState
import com.kitsune.core.designsystem.component.KitsuneFab
import com.kitsune.core.designsystem.component.KitsuneIconButton
import com.kitsune.core.designsystem.component.KitsunePage
import com.kitsune.core.designsystem.component.KitsuneRow
import com.kitsune.core.designsystem.component.KitsuneSegmented
import com.kitsune.core.designsystem.component.PageTitle
import com.kitsune.core.designsystem.component.SectionHeader
import com.kitsune.core.designsystem.component.rememberCondensedTitle
import com.kitsune.feature.persona.list.PersonaListViewModel
import com.kitsune.feature.universe.list.UniverseListViewModel

private const val SEGMENT_CAST = 0
private const val SEGMENT_WORLDS = 1

/**
 * Tab 2 — authoring.
 *
 * v1 gave personas and universes a home tab each, which forced a decision on the user before they
 * had a thought: "am I making a character or a world today?" They are the same activity, and one
 * usually leads to the other — a universe exists to hold a cast, a persona usually belongs to a
 * world. So they share a tab, split by a segmented control, with one creation affordance that
 * follows the segment.
 *
 * Generation jobs (personas and universes are generated in the background by WorkManager, and can
 * outlive the app being closed) surface **here**, at the top, in both segments. In v1 they were
 * rendered inline in whichever list they belonged to and were easy to miss entirely — so a user who
 * closed the app during a generation had no obvious place to come back to.
 */
@Composable
fun CreateTab(
    onCreatePersona: () -> Unit,
    onCreateUniverse: () -> Unit,
    onOpenPersona: (String) -> Unit,
    onOpenUniverse: (String) -> Unit,
    onOpenPersonaDraft: (String) -> Unit,
    onOpenUniverseDraft: (String) -> Unit,
    onOpenInspiration: (String) -> Unit,
    onOpenToneLibrary: () -> Unit,
    personaViewModel: PersonaListViewModel = hiltViewModel(),
    universeViewModel: UniverseListViewModel = hiltViewModel()
) {
    var segment by rememberSaveable { mutableIntStateOf(SEGMENT_CAST) }

    val personas by personaViewModel.personas.collectAsStateWithLifecycle()
    val avatars by personaViewModel.avatarBytesById.collectAsStateWithLifecycle()
    val personaJobs by personaViewModel.generationJobs.collectAsStateWithLifecycle()
    val universes by universeViewModel.universes.collectAsStateWithLifecycle()
    val universeJobs by universeViewModel.generationJobs.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(personas) {
        personaViewModel.ensureAvatarsLoaded(personas)
    }

    val listState = rememberLazyListState()
    val condensed = rememberCondensedTitle(listState)
    val onCast = segment == SEGMENT_CAST

    KitsunePage(
        title = "Créer",
        condensedTitle = condensed,
        actions = {
            KitsuneIconButton(
                icon = Icons.Filled.Tune,
                contentDescription = "Bibliothèque de tons",
                onClick = onOpenToneLibrary
            )
        },
        floatingAction = {
            KitsuneFab(
                text = if (onCast) "Personnage" else "Univers",
                icon = Icons.Filled.Add,
                onClick = if (onCast) onCreatePersona else onCreateUniverse
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = KitsuneTheme.spacing.gutter,
                end = KitsuneTheme.spacing.gutter,
                bottom = KitsuneTheme.spacing.scrollBottom
            ),
            verticalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.md),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item("title") {
                PageTitle(
                    text = "Créer",
                    subtitle = createSubtitle(personas.size, universes.size)
                )
            }
            item("segment") {
                KitsuneSegmented(
                    options = listOf("Personnages", "Univers"),
                    selectedIndex = segment,
                    onSelect = { segment = it },
                    modifier = Modifier.padding(bottom = KitsuneTheme.spacing.lg)
                )
            }

            // Every row in this flow is still relevant: `GenerationJobRepository` drops a job once
            // its draft has been reviewed, retried or dismissed, so there is nothing to filter out
            // here — a SUCCEEDED job is one waiting for the user to pick a proposal.
            val jobs = if (onCast) personaJobs else universeJobs
            if (jobs.isNotEmpty()) {
                item("jobs-header") {
                    SectionHeader(title = "En cours de génération")
                }
                items(jobs, key = { "job-${it.id}" }) { job ->
                    GenerationJobRow(
                        job = job,
                        onOpen = { if (onCast) onOpenPersonaDraft(job.id) else onOpenUniverseDraft(job.id) },
                        onRetry = {
                            if (onCast) personaViewModel.retryFailedJob(job)
                            else universeViewModel.retryFailedJob(job)
                        },
                        onDismiss = {
                            if (onCast) personaViewModel.dismissFailedJob(job)
                            else universeViewModel.dismissFailedJob(job)
                        }
                    )
                }
                item("jobs-gap") { Box(Modifier.height(KitsuneTheme.spacing.lg)) }
            }

            if (onCast) {
                if (personas.isEmpty()) {
                    item("empty-cast") {
                        CastEmptyState(
                            onCreate = onCreatePersona,
                            onInspiration = { onOpenInspiration("PERSONA") }
                        )
                    }
                } else {
                    item("cast-header") { SectionHeader(title = "Votre distribution") }
                    items(personas, key = { it.id }) { persona ->
                        PersonaRow(
                            persona = persona,
                            avatarBytes = persona.avatarImageId?.let(avatars::get),
                            onClick = { onOpenPersona(persona.id) }
                        )
                    }
                }
            } else {
                if (universes.isEmpty()) {
                    item("empty-worlds") {
                        WorldsEmptyState(
                            onCreate = onCreateUniverse,
                            onInspiration = { onOpenInspiration("UNIVERSE") }
                        )
                    }
                } else {
                    item("worlds-header") { SectionHeader(title = "Vos mondes") }
                    items(universes, key = { it.id }) { universe ->
                        UniverseRow(universe = universe, onClick = { onOpenUniverse(universe.id) })
                    }
                }
            }

            // The tone library used to be reachable only through an unlabelled icon in the bar. It is
            // where the user's own story registers live, and where preset packs are shared from.
            item("tones-gap") { Box(Modifier.height(KitsuneTheme.spacing.lg)) }
            item("tones-header") { SectionHeader(title = "Vos façons de raconter") }
            item("tones-row") {
                com.kitsune.core.designsystem.component.KitsuneRow(
                    title = "Bibliothèque de tons",
                    meta = "Vos registres préférés, proposés au début de chaque histoire · partageables en packs",
                    leading = { androidx.compose.material3.Icon(Icons.Filled.Tune, contentDescription = null, tint = KitsuneTheme.colors.textSecondary) },
                    onClick = onOpenToneLibrary
                )
            }
        }
    }
}

@Composable
private fun PersonaRow(
    persona: PersonaEntity,
    avatarBytes: ByteArray?,
    onClick: () -> Unit
) {
    KitsuneRow(
        title = persona.name,
        subtitle = persona.shortDescription.takeIf { it.isNotBlank() },
        meta = persona.tags.take(3).joinToString(" · ").takeIf { it.isNotBlank() },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leading = {
            KitsuneAvatar(
                name = persona.name,
                imageBytes = avatarBytes,
                size = AvatarSize.Medium
            )
        },
        trailing = { ChevronHint() }
    )
}

@Composable
private fun UniverseRow(universe: UniverseEntity, onClick: () -> Unit) {
    KitsuneRow(
        title = universe.name,
        subtitle = universe.description.takeIf { it.isNotBlank() },
        meta = listOfNotNull(
            universe.genre.takeIf { it.isNotBlank() },
            universe.tags.take(2).joinToString(" · ").takeIf { it.isNotBlank() }
        ).joinToString(" · ").takeIf { it.isNotBlank() },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leading = {
            KitsuneAvatar(
                name = universe.name,
                size = AvatarSize.Medium,
                shape = KitsuneTheme.shape.sm
            )
        },
        trailing = { ChevronHint() }
    )
}

@Composable
private fun ChevronHint() {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = KitsuneTheme.colors.textFaint,
        modifier = Modifier.padding(end = 4.dp)
    )
}

/**
 * A background generation, as one row.
 *
 * Three states share the row so the strip never changes shape under the user: running shows a
 * spinner, succeeded shows a chevron into the draft review, failed shows the reason plus retry and
 * dismiss. v1 spread these across an inline card, a dialog and a notification.
 */
@Composable
private fun GenerationJobRow(
    job: GenerationJobEntity,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = KitsuneTheme.colors
    val failed = job.state == GenerationJobState.FAILED
    KitsuneRow(
        title = job.description.take(60).ifBlank { "Génération" },
        meta = when (job.state) {
            GenerationJobState.PENDING -> "En attente"
            GenerationJobState.RUNNING -> "Génération en cours…"
            GenerationJobState.SUCCEEDED -> "Prêt à relire"
            GenerationJobState.FAILED -> job.errorMessage ?: "Échec"
        },
        titleColor = if (failed) colors.error else null,
        onClick = if (job.state == GenerationJobState.SUCCEEDED) onOpen else null,
        modifier = Modifier.fillMaxWidth(),
        leading = {
            when {
                failed -> Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.padding(8.dp)
                )

                job.state == GenerationJobState.SUCCEEDED -> Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.padding(8.dp)
                )

                else -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = colors.accent,
                    modifier = Modifier.padding(8.dp).height(20.dp)
                )
            }
        },
        trailing = {
            if (failed) {
                KitsuneIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Réessayer",
                    onClick = onRetry
                )
                KitsuneIconButton(
                    icon = Icons.Filled.ErrorOutline,
                    contentDescription = "Ignorer",
                    onClick = onDismiss
                )
            } else if (job.state == GenerationJobState.SUCCEEDED) {
                ChevronHint()
            }
        }
    )
}

@Composable
private fun CastEmptyState(onCreate: () -> Unit, onInspiration: () -> Unit) {
    KitsuneEmptyState(
        icon = Icons.Outlined.Groups,
        title = "Aucun personnage",
        body = "Décrivez qui vous voulez rencontrer — une phrase suffit, le reste peut être généré.",
        actionLabel = "Créer un personnage",
        onAction = onCreate,
        // The inspiration Q&A is free (CostCalculator.FREE_OPERATION_TYPES) and exists precisely for
        // the user standing on this screen with no idea. Offering it here, rather than burying it in
        // the creation form, is the difference between it being used and not existing.
        secondaryLabel = "Je ne sais pas quoi créer",
        onSecondary = onInspiration
    )
}

@Composable
private fun WorldsEmptyState(onCreate: () -> Unit, onInspiration: () -> Unit) {
    KitsuneEmptyState(
        icon = Icons.Outlined.Public,
        title = "Aucun univers",
        body = "Un univers donne un décor, des factions et des lieux partagés à plusieurs personnages.",
        actionLabel = "Créer un univers",
        onAction = onCreate,
        secondaryLabel = "Je ne sais pas quoi créer",
        onSecondary = onInspiration
    )
}

private fun createSubtitle(personas: Int, universes: Int): String {
    val cast = when (personas) {
        0 -> null
        1 -> "1 personnage"
        else -> "$personas personnages"
    }
    val worlds = when (universes) {
        0 -> null
        1 -> "1 univers"
        else -> "$universes univers"
    }
    return listOfNotNull(cast, worlds).joinToString(" · ").ifBlank { "Rien encore" }
}
