package com.kitsune.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kitsune.core.designsystem.KitsuneTheme

/**
 * Modals: one bottom sheet, one dialog, one action list.
 *
 * v1 reached for `AlertDialog` for everything — confirmations, pickers, paywalls, "welcome", the daily
 * bonus, error details — so a dialog could mean anything from "nice, here are 3 free Ofudas" to "this
 * will permanently delete your story", with identical framing. v2 splits it:
 *
 * - [KitsuneSheet] for **choices and detail** — anything the user is browsing or picking from.
 * - [KitsuneConfirmDialog] for **consequences** — and only those. If a dialog appears, something is
 *   about to happen that the user cannot casually undo.
 * - [KitsuneActionSheet] for the overflow menu, replacing the `DropdownMenu` that was unreachable
 *   one-handed on a tall phone.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsuneSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = KitsuneTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceBright,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        shape = KitsuneTheme.shape.sheet,
        dragHandle = { SheetHandle() },
        modifier = modifier
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = KitsuneTheme.spacing.gutter)
                .navigationBarsPadding()
                .padding(bottom = KitsuneTheme.spacing.xl)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.text,
                    modifier = Modifier.padding(bottom = KitsuneTheme.spacing.lg)
                )
            }
            content()
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = KitsuneTheme.spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(KitsuneTheme.colors.outlineStrong, RoundedCornerShape(2.dp))
        )
    }
}

/**
 * Confirmation. Two buttons, the destructive one **second** and never pre-focused, and a body that
 * says what will actually be lost rather than "Êtes-vous sûr ?".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsuneConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Annuler",
    destructive: Boolean = false,
    loading: Boolean = false
) {
    val colors = KitsuneTheme.colors
    val spacing = KitsuneTheme.spacing
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = !loading),
        modifier = modifier
    ) {
        Surface(
            shape = KitsuneTheme.shape.md,
            color = colors.surfaceBright,
            contentColor = colors.text,
            tonalElevation = 0.dp
        ) {
            Column(Modifier.padding(spacing.xl)) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall, color = colors.text)
                Spacer(Modifier.height(spacing.md))
                Text(text = body, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                Spacer(Modifier.height(spacing.xl))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    KitsuneQuietButton(text = dismissLabel, onClick = onDismiss, enabled = !loading)
                    Spacer(Modifier.width(spacing.sm))
                    if (destructive) {
                        KitsuneDangerButton(
                            text = confirmLabel,
                            onClick = onConfirm,
                            loading = loading,
                            compact = true,
                            fillWidth = false
                        )
                    } else {
                        KitsuneButton(
                            text = confirmLabel,
                            onClick = onConfirm,
                            loading = loading,
                            compact = true,
                            fillWidth = false
                        )
                    }
                }
            }
        }
    }
}

/**
 * The overflow menu, as a bottom sheet of labelled rows.
 *
 * Each entry carries an icon and, where relevant, its Ofuda price via [SheetAction.cost] — so the
 * user sees what a chat action will cost *in the menu*, before they open a confirmation, instead of
 * discovering it in a dialog after committing mentally to the action.
 */
@Composable
fun KitsuneActionSheet(
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    title: String? = null
) {
    KitsuneSheet(onDismiss = onDismiss, title = title) {
        // Grouped by [SheetAction.section], in first-appearance order, with the header drawn only when
        // the section actually changes. A long menu of unlabelled rows is what the chat overflow was
        // in v1 (fifteen entries, three invisible groups separated by dividers); naming the groups is
        // what turns it back into something scannable.
        var lastSection: String? = null
        actions.forEachIndexed { index, action ->
            if (action.section != null && action.section != lastSection) {
                if (index > 0) Spacer(Modifier.height(KitsuneTheme.spacing.md))
                SectionHeader(title = action.section)
                Spacer(Modifier.height(KitsuneTheme.spacing.xs))
            }
            lastSection = action.section
            SheetActionRow(action = action, onDismiss = onDismiss)
        }
    }
}

data class SheetAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val detail: String? = null,
    /** Ofuda price, or null when the action is free. 0 renders as an explicit "Gratuit". */
    val cost: Int? = null,
    /** Optional group heading. Consecutive actions sharing one only print it once. */
    val section: String? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true
)

@Composable
private fun SheetActionRow(action: SheetAction, onDismiss: () -> Unit) {
    val colors = KitsuneTheme.colors
    val tint = when {
        !action.enabled -> colors.textFaint
        action.destructive -> colors.error
        else -> colors.text
    }
    Surface(
        onClick = {
            // Dismiss first so the sheet's exit animation is not competing with a navigation push.
            onDismiss()
            action.onClick()
        },
        enabled = action.enabled,
        color = Color.Transparent,
        shape = KitsuneTheme.shape.md,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = KitsuneTheme.spacing.md)
        ) {
            Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(KitsuneTheme.spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(action.label, style = MaterialTheme.typography.bodyLarge, color = tint)
                if (action.detail != null) {
                    Text(action.detail, style = KitsuneTheme.type.meta, color = colors.textDim)
                }
            }
            if (action.cost != null) {
                OfudaCost(cost = action.cost, free = action.cost == 0)
            }
        }
    }
}
