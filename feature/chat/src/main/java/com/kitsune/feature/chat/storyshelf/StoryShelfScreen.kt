package com.kitsune.feature.chat.storyshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kitsune.core.designsystem.DecryptedImage
import com.kitsune.feature.chat.R

/**
 * The app's front door: a shelf of stories, not a list of conversations (2026-08-25).
 *
 * ## Why this replaces the tabs
 *
 * The home screen used to be `Chats / Personas / Univers` — an organisation by **entities the
 * database stores**, which is a developer's mental model, not a reader's. Nobody arranges their
 * bookshelf by "authors" and "publishing houses". A shelf says what the app is for in one glance,
 * and it is the only surface where the three things Kitsune actually has — a private vault, a real
 * narrative engine, and stories that become objects — become visible at the same time.
 *
 * Covers are the payoff of persisting `ChatEntity.coverImageId`: until now the app drew a book cover
 * for every exported story and immediately threw it away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryShelfScreen(
    onOpenStory: (chatId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StoryShelfViewModel = hiltViewModel()
) {
    viewModel.fallbackTitle = stringResource(R.string.shelf_untitled_story)
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val imageBytesById by viewModel.imageBytesById.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ShelfStory?>(null) }

    LaunchedEffect(stories) {
        viewModel.ensureChaptersLoaded(stories.map { it.chatId })
        viewModel.ensureImagesLoaded(stories)
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.shelf_delete_title, target.title)) },
            text = { Text(stringResource(R.string.shelf_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteStory(target.chatId)
                    pendingDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (stories.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.shelf_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.shelf_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(stories, key = { it.chatId }) { story ->
            StorySpine(
                story = story,
                coverBytes = story.coverImageId?.let(imageBytesById::get),
                portraitBytes = story.avatarImageId?.let(imageBytesById::get),
                onOpen = { onOpenStory(story.chatId) },
                onLongPress = { pendingDelete = story }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorySpine(
    story: ShelfStory,
    coverBytes: ByteArray?,
    portraitBytes: ByteArray?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Book proportions rather than a square thumbnail: the shape alone says "this is a
                // story you are accumulating", before a single word is read.
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            when {
                coverBytes != null -> DecryptedImage(
                    bytes = coverBytes,
                    contentDescription = story.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // No cover yet — the story has not closed a chapter. The character's portrait is a
                // better placeholder than an empty rectangle, and the spine still reads as a book.
                portraitBytes != null -> DecryptedImage(
                    bytes = portraitBytes,
                    contentDescription = story.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                else -> Text(
                    story.title.take(1).uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (story.hasUnseenDevelopment) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Text(
            story.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (story.chapterCount > 0) {
                    stringResource(R.string.shelf_chapter_count, story.chapterCount)
                } else {
                    stringResource(R.string.shelf_not_started)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (story.hasUnseenDevelopment) {
                Text(
                    stringResource(R.string.shelf_something_happened),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }
}
