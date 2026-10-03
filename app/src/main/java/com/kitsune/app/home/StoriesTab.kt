package com.kitsune.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.component.AvatarSize
import com.kitsune.core.designsystem.component.KitsuneAvatar
import com.kitsune.core.designsystem.component.KitsuneEmptyState
import com.kitsune.core.designsystem.component.KitsuneFab
import com.kitsune.core.designsystem.component.KitsunePage
import com.kitsune.core.designsystem.component.KitsuneRow
import com.kitsune.core.designsystem.component.KitsuneSkeletonList
import com.kitsune.core.designsystem.component.PageTitle
import com.kitsune.core.designsystem.component.rememberCondensedTitle
import com.kitsune.feature.chat.storyshelf.ShelfStory
import com.kitsune.feature.chat.storyshelf.StoryShelfViewModel

/**
 * Tab 1 — the shelf of stories in progress.
 *
 * This is the screen the app opens on, so it carries the whole first impression, and v1's version of
 * it was a `TabRow` tab showing a flat `ListItem` per chat with the persona's name and the raw last
 * message. v2 shows *stories*: a cover or portrait, the story's title (chapter title, then character
 * name, then a fallback — never "Chat 4"), how far along it is, and a single accent dot when something
 * moved in the background while the user was away.
 *
 * Ordering is [com.kitsune.feature.chat.storyshelf.shelfOrder]'s: what developed without you first,
 * then most recently touched. That is already unit-tested in `feature:chat`, so this screen does not
 * re-derive it.
 */
@Composable
fun StoriesTab(
    onOpenChat: (String) -> Unit,
    onGoToCreate: () -> Unit,
    viewModel: StoryShelfViewModel = hiltViewModel()
) {
    // Set before the first collection: `core:data` types must not reach for Android resources, so
    // the ViewModel takes its fallback title from the UI layer instead.
    remember { viewModel.fallbackTitle = "Histoire sans titre" }
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val images by viewModel.imageBytesById.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val condensed = rememberCondensedTitle(listState)

    // `stories` starts empty while the flow spins up, which is indistinguishable from "no stories".
    // A short skeleton window avoids flashing the empty state at a user who has twenty of them.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(stories) {
        if (stories.isNotEmpty()) settled = true
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        settled = true
    }

    LaunchedEffect(stories) {
        viewModel.ensureChaptersLoaded(stories.map { it.chatId })
        viewModel.ensureImagesLoaded(stories)
    }

    KitsunePage(
        title = "Vos histoires",
        condensedTitle = condensed,
        floatingAction = {
            if (stories.isNotEmpty()) {
                KitsuneFab(
                    text = "Nouvelle histoire",
                    icon = Icons.Filled.Add,
                    onClick = onGoToCreate
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !settled && stories.isEmpty() -> {
                    KitsuneSkeletonList(
                        modifier = Modifier.padding(horizontal = KitsuneTheme.spacing.gutter),
                        count = 4
                    )
                }

                stories.isEmpty() -> {
                    KitsuneEmptyState(
                        modifier = Modifier.fillMaxSize(),
                        icon = Icons.Outlined.AutoStories,
                        title = "Rien de commencé",
                        body = "Une histoire naît d'un personnage. Créez-en un, puis lancez votre " +
                            "première scène avec lui.",
                        actionLabel = "Créer un personnage",
                        onAction = onGoToCreate
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = KitsuneTheme.spacing.gutter,
                            end = KitsuneTheme.spacing.gutter,
                            bottom = KitsuneTheme.spacing.scrollBottom
                        ),
                        verticalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.md),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item("title") {
                            PageTitle(
                                text = "Vos histoires",
                                subtitle = storiesSubtitle(stories)
                            )
                        }
                        items(stories, key = { it.chatId }) { story ->
                            StoryRow(
                                story = story,
                                imageBytes = story.coverImageId?.let(images::get)
                                    ?: story.avatarImageId?.let(images::get),
                                onClick = { onOpenChat(story.chatId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryRow(
    story: ShelfStory,
    imageBytes: ByteArray?,
    onClick: () -> Unit
) {
    KitsuneRow(
        title = story.title,
        meta = storyMeta(story),
        highlighted = story.hasUnseenDevelopment,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leading = {
            KitsuneAvatar(
                name = story.title,
                imageBytes = imageBytes,
                size = AvatarSize.Medium,
                highlighted = story.hasUnseenDevelopment
            )
        }
    )
}

/**
 * "3 en cours · 1 a avancé". The second clause only appears when it is true — a subtitle that always
 * reads the same is noise the eye learns to skip.
 */
private fun storiesSubtitle(stories: List<ShelfStory>): String {
    val moved = stories.count { it.hasUnseenDevelopment }
    val base = if (stories.size == 1) "1 en cours" else "${stories.size} en cours"
    return if (moved > 0) "$base · $moved a avancé" else base
}

private fun storyMeta(story: ShelfStory): String {
    val chapters = when {
        story.chapterCount <= 0 -> null
        story.chapterCount == 1 -> "1 chapitre"
        else -> "${story.chapterCount} chapitres"
    }
    val kind = if (story.isEnsemble) "Scène d'ensemble" else null
    return listOfNotNull(kind, chapters, relativeTime(story.updatedAt)).joinToString(" · ")
}

/**
 * Relative time, French, in the four steps that actually matter to a reader. Deliberately stops at
 * weeks: past that, "il y a 9 semaines" tells a user less than nothing, so it becomes a date.
 */
internal fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val delta = now - timestamp
    val minutes = delta / 60_000
    val hours = delta / 3_600_000
    val days = delta / 86_400_000
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        hours < 24 -> if (hours == 1L) "il y a 1 h" else "il y a $hours h"
        days < 7 -> if (days == 1L) "hier" else "il y a $days jours"
        days < 28 -> "il y a ${days / 7} sem."
        else -> {
            val formatter = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
            formatter.format(java.util.Date(timestamp))
        }
    }
}
