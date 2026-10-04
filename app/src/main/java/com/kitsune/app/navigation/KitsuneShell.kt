package com.kitsune.app.navigation

import com.kitsune.app.R
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.filled.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.kitsune.app.home.CreateTab
import com.kitsune.app.home.DiscoverTab
import com.kitsune.app.home.ProfileTab
import com.kitsune.app.home.StoriesTab
import com.kitsune.core.designsystem.KitsuneTheme
import com.kitsune.core.designsystem.welcome.WelcomeDialog
import com.kitsune.core.designsystem.component.KitsuneNavBar
import com.kitsune.core.designsystem.component.KitsuneNotice
import com.kitsune.core.designsystem.component.NavBarItem
import com.kitsune.core.designsystem.component.NoticeTone

/**
 * The tabbed shell — the app's home, and the answer to "why does this feel like a pile of features".
 *
 * v1's home was a four-tab `TabRow` (Chats / Personas / Univers / Marketplace) with a settings gear
 * and a shopping-cart icon in the app bar, and ten further screens reachable only from menus nested
 * inside those. Two things were wrong with it. First, Personas and Univers are not two activities —
 * they are one (authoring what you then play with), so splitting them meant the user had to know
 * which drawer a thing was filed in. Second, a *marketplace* sat as a peer of the user's own private
 * library, which put a shop one tap from the thing the app promises is a private vault.
 *
 * v2's five destinations each answer a different question, and nothing appears in two of them:
 *
 * | tab | the question it answers |
 * |---|---|
 * | Histoires | what am I in the middle of? |
 * | Créer | what have I made, and what do I want to make? |
 * | Découvrir | what has everyone else made? |
 * | Profil | who am I here, and what do I have? |
 * | Réglages | how does this behave? |
 *
 * The store is inside Profil and nowhere else. That single move is most of the "pas un aspirateur à
 * argent" change: money is something you go and look at, not something the app shows you while you
 * are reading.
 */
@Composable
fun KitsuneShell(
    navController: NavHostController,
    viewModel: ShellViewModel = hiltViewModel()
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Stories.id) }
    val current = HomeTab.fromId(tab)
    val needsProvider by viewModel.needsProvider.collectAsStateWithLifecycle()

    // The one launch modal. It is genuine onboarding (a carousel explaining the vault, the memory
    // system and the bring-your-own-key model), not a prompt to accept or buy something.
    // The preference key is shared with `ImportBackupUseCase`, which marks it seen on a restore so a
    // returning user is not re-onboarded; it must stay "welcome_shown"/"kitsune_prefs".
    val context = LocalContext.current
    val welcomePrefs = remember {
        context.getSharedPreferences("kitsune_prefs", Context.MODE_PRIVATE)
    }
    var showWelcome by remember { mutableStateOf(!welcomePrefs.getBoolean("welcome_shown", false)) }
    if (showWelcome) {
        val markSeen = {
            welcomePrefs.edit().putBoolean("welcome_shown", true).apply()
            showWelcome = false
        }
        WelcomeDialog(
            onDismiss = markSeen,
            onStartFirstPersona = {
                markSeen()
                navController.navigate(Routes.personaCreate())
            },
            onOpenPersonalInfo = {
                markSeen()
                tab = HomeTab.Settings.id
            }
        )
    }

    // Back from any tab returns to Histoires before it leaves the shell — the Android convention,
    // and it stops a stray back press from dropping the user onto the lock screen.
    BackHandler(enabled = current != HomeTab.Stories) { tab = HomeTab.Stories.id }

    val items = listOf(
        NavBarItem(
            route = HomeTab.Stories.id,
            label = "Histoires",
            icon = Icons.Outlined.AutoStories,
            selectedIcon = Icons.Filled.AutoStories
        ),
        NavBarItem(
            route = HomeTab.Create.id,
            label = "Créer",
            icon = Icons.Outlined.Brush,
            selectedIcon = Icons.Filled.Brush
        ),
        NavBarItem(
            route = HomeTab.Discover.id,
            label = "Découvrir",
            icon = Icons.Outlined.Explore,
            selectedIcon = Icons.Filled.Explore
        ),
        NavBarItem(
            route = HomeTab.Profile.id,
            label = "Profil",
            icon = Icons.Outlined.Person,
            selectedIcon = Icons.Filled.Person
        ),
        NavBarItem(
            route = HomeTab.Settings.id,
            label = "Réglages",
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Filled.Settings
        )
    )

    Column(Modifier.fillMaxSize()) {
        // Nothing can be written without an AI provider, so until one is configured the shell says so
        // on every tab — quietly, above the content, never as a dialog.
        if (needsProvider) {
            KitsuneNotice(
                text = stringResource(R.string.shell_needs_provider),
                icon = Icons.Filled.Key,
                tone = NoticeTone.Warn,
                action = stringResource(R.string.shell_needs_provider_action),
                onAction = { navController.navigate(Routes.providers()) },
                modifier = Modifier.padding(
                    start = KitsuneTheme.spacing.gutter,
                    end = KitsuneTheme.spacing.gutter,
                    top = KitsuneTheme.spacing.md
                )
            )
        }
        // `consumeWindowInsets`, and it is load-bearing: each tab is a `KitsunePage`, i.e. a Scaffold,
        // and a Scaffold with no bottomBar puts the navigation-bar inset into the content padding its
        // screen then applies. But this Box already ends above `KitsuneNavBar`, which applies
        // `navigationBarsPadding()` itself — so without this the tab content would be pushed up by a
        // second nav-bar height and leave a dead band above the tab bar.
        Box(
            Modifier
                .weight(1f)
                .consumeWindowInsets(WindowInsets.navigationBars)
        ) {
            // Crossfade rather than a slide: the five tabs are peers, so implying a left/right
            // order between them would be a lie about the information architecture.
            Crossfade(
                targetState = current,
                animationSpec = tween(KitsuneTheme.motion.standard),
                label = "shellTab"
            ) { selected ->
                when (selected) {
                    HomeTab.Stories -> StoriesTab(
                        onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                        onGoToCreate = { tab = HomeTab.Create.id }
                    )

                    HomeTab.Create -> CreateTab(
                        onCreatePersona = { navController.navigate(Routes.personaCreate()) },
                        onCreateUniverse = { navController.navigate(Routes.universeCreate()) },
                        onOpenPersona = { id -> navController.navigate(Routes.personaDetail(id)) },
                        onOpenUniverse = { id -> navController.navigate(Routes.universeDetail(id)) },
                        onOpenPersonaDraft = { jobId -> navController.navigate(Routes.personaDraftReview(jobId)) },
                        onOpenUniverseDraft = { jobId -> navController.navigate(Routes.universeDraftReview(jobId)) },
                        onOpenInspiration = { target -> navController.navigate(Routes.inspirationWizard(target)) },
                        onOpenToneLibrary = { navController.navigate(Routes.toneLibrary()) }
                    )

                    HomeTab.Discover -> DiscoverTab(
                        onOpenListing = { id -> navController.navigate(Routes.marketplaceDetail(id)) },
                        onOpenCreator = { id -> navController.navigate(Routes.creatorListings(id)) }
                    )

                    HomeTab.Profile -> ProfileTab(
                        onOpenSettings = { tab = HomeTab.Settings.id },
                        onOpenBadges = { navController.navigate(Routes.myBadges()) },
                        onOpenFollows = { navController.navigate(Routes.myFollows()) },
                        onOpenMessages = { navController.navigate(Routes.messages()) },
                        onOpenProposals = { navController.navigate(Routes.proposals()) },
                        onOpenMyListings = { creatorId ->
                            navController.navigate(Routes.creatorListings(creatorId))
                        }
                    )

                    HomeTab.Settings -> com.kitsune.feature.settings.SettingsScreen(
                        onBack = {},
                        showBack = false,
                        onAccountDeleted = { navController.navigate(Routes.LOCK) { popUpTo(0) } },
                        onOpenProviders = { navController.navigate(Routes.providers()) },
                        onOpenModels = { navController.navigate(Routes.models()) },
                        onOpenGeneration = { navController.navigate(Routes.generation()) }
                    )
                }
            }
        }
        KitsuneNavBar(
            items = items,
            selectedRoute = tab,
            onSelect = { tab = it }
        )
    }
}
