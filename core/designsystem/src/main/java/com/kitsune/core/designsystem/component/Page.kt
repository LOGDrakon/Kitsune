package com.kitsune.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsune.core.designsystem.KitsuneTheme

/**
 * The screen shell. Every full-screen destination in the app is a [KitsunePage].
 *
 * The editorial look hinges on one decision here: the screen's name is **not** in the app bar. A
 * Material `TopAppBar` with a 22sp title pinned to the top of every screen is what makes an app read
 * as a form-filling tool; instead the bar is a thin strip carrying only navigation and actions, and
 * the title lives in the content as a large serif [PageTitle] that scrolls away like a chapter
 * heading. Once it has scrolled out, it fades into the bar — so the screen is never unlabelled, but
 * the label is never in the way.
 *
 * Pass [condensedTitle] = true (from [rememberCondensedTitle]) to drive that crossfade.
 */
@Composable
fun KitsunePage(
    modifier: Modifier = Modifier,
    title: String? = null,
    condensedTitle: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingAction: @Composable () -> Unit = {},
    /** Set false on immersive screens (chat, novel mode) that paint their own background. */
    paintBackground: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = KitsuneTheme.colors
    Scaffold(
        modifier = modifier,
        containerColor = if (paintBackground) colors.background else Color.Transparent,
        contentColor = colors.text,
        topBar = {
            KitsuneTopBar(
                title = title,
                showTitle = condensedTitle,
                onBack = onBack,
                actions = actions
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingAction,
        content = content
    )
}

/**
 * The thin top strip: back affordance, a title that only appears once the big one has scrolled off,
 * and up to three actions. Fixed 56dp tall, no shadow, no surface colour of its own.
 */
@Composable
fun KitsuneTopBar(
    title: String? = null,
    showTitle: Boolean = false,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = KitsuneTheme.colors
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
            if (onBack != null) {
                KitsuneIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    onClick = onBack,
                    tint = colors.text
                )
            } else {
                Spacer(Modifier.width(KitsuneTheme.spacing.md))
            }
            Box(Modifier.weight(1f)) {
                if (title != null) {
                    val alpha by animateFloatAsState(
                        targetValue = if (showTitle) 1f else 0f,
                        label = "condensedTitle"
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(alpha)
                    )
                }
            }
            actions()
        }
    }
}

/**
 * The big serif screen heading, placed as the first item of the page's scrolling content.
 *
 * [subtitle] is where the screen's one line of orientation goes ("3 en cours", "12 personas") —
 * v1 had no slot for it, so counts and status ended up as chips, badges and coloured pills.
 */
@Composable
fun PageTitle(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = KitsuneTheme.colors
    val spacing = KitsuneTheme.spacing
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spacing.xs, bottom = spacing.xl)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.displaySmall,
                color = colors.text
            )
            if (subtitle != null) {
                Spacer(Modifier.height(spacing.xs))
                Text(text = subtitle, style = KitsuneTheme.type.meta, color = colors.textDim)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(spacing.md))
            trailing()
        }
    }
}

/**
 * True once the page's first item has scrolled far enough that [PageTitle] is off screen, so the
 * condensed title in the bar should take over. Threshold is in pixels of the first item's offset —
 * generous enough that a small overscroll bounce does not flicker the title.
 */
@Composable
fun rememberCondensedTitle(state: LazyListState, threshold: Int = 72): Boolean {
    val condensed by remember(state, threshold) {
        derivedStateOf {
            state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > threshold
        }
    }
    return condensed
}

/**
 * The bottom navigation bar.
 *
 * Five destinations, each a glyph plus an 11sp label. The active one is the only accent-coloured thing on the
 * bar and the only thing with a filled container behind it; there is no ripple-filled "pill" sliding
 * around, and no badge counts — a single dot is the entire unread vocabulary.
 */
@Composable
fun KitsuneNavBar(
    items: List<NavBarItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KitsuneTheme.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outline))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
        ) {
            items.forEach { item ->
                NavBarCell(
                    item = item,
                    selected = item.route == selectedRoute,
                    onClick = { onSelect(item.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

data class NavBarItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    /** Draws the single accent dot. No counts — see [KitsuneNavBar]. */
    val hasNews: Boolean = false
)

@Composable
private fun NavBarCell(
    item: NavBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KitsuneTheme.colors
    val tint = if (selected) colors.accent else colors.textDim
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.icon,
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
                if (item.hasNews) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 1.dp)
                            .size(6.dp)
                            .background(colors.accent, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1
            )
        }
    }
}

/**
 * A pinned action bar at the bottom of a form or paywall: the page's ground, a hairline on top, and
 * one or two buttons. Used instead of putting the submit button at the end of a long scroll, where
 * it is easy to miss.
 */
@Composable
fun KitsuneBottomActions(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = KitsuneTheme.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outline))
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = KitsuneTheme.spacing.gutter, vertical = KitsuneTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.sm),
            content = content
        )
    }
}

/** Wraps content that should only exist once something has loaded, with a fade rather than a jump. */
@Composable
fun FadeIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        content = { content() }
    )
}
