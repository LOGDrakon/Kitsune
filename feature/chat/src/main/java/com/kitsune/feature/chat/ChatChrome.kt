package com.kitsune.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import com.kitsune.core.designsystem.component.KitsuneQuietButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.LocalDiscreetMode
import com.kitsune.core.designsystem.component.AvatarSize
import com.kitsune.core.designsystem.component.KitsuneAvatar
import com.kitsune.core.designsystem.component.KitsuneIconButton
import com.kitsune.core.designsystem.discreetBlur

/**
 * The chat's chrome: the bar above the story, and the mode pill inside it.
 *
 * Two things changed from v1, both about getting out of the way of the prose:
 *
 * 1. **No mode switch.** v1 gave a Standard/Pro switch a whole row under the app bar; the fork has
 *    no paid tier, and memory/length are a setting (Réglages → Mémoire et longueur), not a toggle.
 * 2. **One overflow affordance**, opening a bottom sheet rather than a 15-item dropdown pinned to the
 *    top-right corner (see `ChatScreen`'s tools sheet).
 */
@Composable
internal fun ChatTopBar(
    isEnsemble: Boolean,
    title: String,
    subtitle: String,
    avatarBytes: ByteArray?,
    onBack: () -> Unit,
    onOpenTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KitsuneTheme.colors
    val isDiscreet = LocalDiscreetMode.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = KitsuneTheme.spacing.sm)
        ) {
            KitsuneIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.content_desc_back),
                onClick = onBack,
                tint = colors.text
            )
            KitsuneAvatar(
                name = title.ifBlank { "?" },
                imageBytes = avatarBytes,
                size = AvatarSize.Small,
                // Rounded square for an ensemble scene, circle for a single character. The story list
                // uses the same distinction, so "this is a scene with a cast" is readable at a glance
                // in both places — v1 rendered the two modes identically.
                shape = if (isEnsemble) KitsuneTheme.shape.sm else CircleShape,
                // Discreet mode blurs portraits so a glance from the next seat shows nothing readable;
                // the blur belongs on the image, not on the whole bar, so the user can still navigate.
                modifier = if (isDiscreet) Modifier.discreetBlur(10.dp) else Modifier
            )
            Column(
                Modifier
                    .padding(start = KitsuneTheme.spacing.md)
                    .weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = KitsuneTheme.type.meta,
                    color = colors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            KitsuneIconButton(
                icon = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.content_desc_more_options),
                onClick = onOpenTools,
                tint = colors.text
            )
        }
    }
    // Deliberately no divider under the bar: the message list scrolls against the same ground, so a
    // rule there would read as a seam in the page rather than as structure.
}

/**
 * The composer.
 *
 * v1 put five controls in one row — an asterisk button, a "suggestions" *text* button, the field, a
 * cancel button while editing, and send — so the field itself was squeezed to roughly half the screen
 * width on a phone, and the two auxiliary actions were as visually loud as sending. That is backwards:
 * writing is the primary act here, and the field should be the biggest thing in the row.
 *
 * v2 gives the field the whole width inside one rounded surface, demotes the two helpers to small
 * glyphs beside it, and makes send the only filled control on screen. The field grows to five lines
 * before it scrolls, because roleplay input is frequently a paragraph rather than a line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatComposer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isEditing: Boolean,
    isSending: Boolean,
    suggestions: List<String>,
    isLoadingSuggestions: Boolean,
    onRequestSuggestions: () -> Unit,
    onUseSuggestion: (String) -> Unit,
    onInsertAsterisk: () -> Unit,
    onCancelEdit: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KitsuneTheme.colors
    val spacing = KitsuneTheme.spacing
    val canSend = value.text.isNotBlank() && !isSending

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.background)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = spacing.md, vertical = spacing.sm)
    ) {
        // Reply suggestions are offers, not chrome: they occupy no space until the user asks for them,
        // and they sit above the field as quiet chips rather than competing with the send button.
        if (!isEditing && (isLoadingSuggestions || suggestions.isNotEmpty())) {
            if (isLoadingSuggestions) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = spacing.sm, start = spacing.sm)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(spacing.sm))
                    Text(
                        text = stringResource(R.string.chat_next_reply_suggestions_loading),
                        style = KitsuneTheme.type.meta,
                        color = colors.textDim
                    )
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.sm)
                ) {
                    suggestions.forEach { suggestion ->
                        Surface(
                            onClick = { onUseSuggestion(suggestion) },
                            shape = KitsuneTheme.shape.md,
                            color = colors.surfaceVariant,
                            modifier = Modifier.border(1.dp, colors.outline, KitsuneTheme.shape.md)
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)
                            )
                        }
                    }
                }
            }
        }

        if (isEditing) {
            // Editing rewrites history, so the composer says so outright instead of relying on a
            // changed placeholder the user has to notice. Cancel lives here rather than as a fifth
            // control wedged into the input row.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.sm, start = spacing.sm)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = stringResource(R.string.chat_input_placeholder_editing),
                    style = KitsuneTheme.type.meta,
                    color = colors.accent,
                    modifier = Modifier.weight(1f)
                )
                KitsuneQuietButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancelEdit,
                    enabled = !isSending
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surfaceVariant, KitsuneTheme.shape.lg)
                    .border(1.dp, colors.outline, KitsuneTheme.shape.lg)
                    .padding(horizontal = spacing.sm)
            ) {
                // Asterisks wrap narration, the roleplay convention. An icon would be a worse label
                // than the character itself, so the glyph is the label.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(spacing.touchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onInsertAsterisk)
                ) {
                    Text(
                        text = "*",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textSecondary
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    maxLines = 5,
                    textStyle = KitsuneTheme.type.message.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    decorationBox = { inner ->
                        Box {
                            if (value.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.chat_input_placeholder_default),
                                    style = KitsuneTheme.type.message,
                                    color = colors.textFaint
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = spacing.md)
                )
                if (!isEditing) {
                    KitsuneIconButton(
                        icon = Icons.Outlined.AutoAwesome,
                        contentDescription = stringResource(R.string.chat_next_reply_suggestions_button),
                        onClick = onRequestSuggestions,
                        enabled = !isSending && !isLoadingSuggestions
                    )
                }
            }
            Spacer(Modifier.width(spacing.sm))
            Surface(
                onClick = onSend,
                enabled = canSend,
                shape = CircleShape,
                color = if (canSend) colors.accent else colors.surfaceVariant,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isEditing) Icons.Filled.Check else Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(
                            if (isEditing) R.string.action_save else R.string.chat_send_content_description
                        ),
                        tint = if (canSend) colors.onAccent else colors.textFaint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
