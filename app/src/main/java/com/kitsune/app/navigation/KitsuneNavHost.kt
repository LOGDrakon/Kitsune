package com.kitsune.app.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kitsune.app.inspiration.InspirationWizardScreen
import com.kitsune.feature.settings.generation.GenerationScreen
import com.kitsune.feature.settings.models.ModelsScreen
import com.kitsune.feature.settings.providers.ProvidersScreen
import com.kitsune.core.common.inspiration.InspirationDraftHolder
import com.kitsune.core.network.inspiration.InspirationTarget
import com.kitsune.feature.auth.LockScreen
import com.kitsune.feature.auth.notes.DecoyNoteEditorScreen
import com.kitsune.feature.auth.notes.DecoyNotesListScreen
import com.kitsune.feature.chat.ChatScreen
import com.kitsune.feature.chat.branchtree.ChatBranchTreeScreen
import com.kitsune.feature.chat.imagegen.ImageGenerationScreen
import com.kitsune.feature.chat.list.ChatListScreen
import com.kitsune.feature.chat.memory.StoryMemoryScreen
import com.kitsune.feature.chat.novel.NovelModeScreen
import com.kitsune.feature.chat.timeline.TimelineScreen
import com.kitsune.feature.marketplace.CreatorListingsScreen
import com.kitsune.feature.marketplace.ListingDetailScreen
import com.kitsune.feature.marketplace.MyFollowsScreen
import com.kitsune.feature.onboarding.AccountChoiceScreen
import com.kitsune.feature.onboarding.AgeVerificationScreen
import com.kitsune.feature.onboarding.LanguageSelectionScreen
import com.kitsune.feature.onboarding.PinSetupScreen
import com.kitsune.feature.onboarding.RecoverAccountScreen
import com.kitsune.feature.onboarding.UnderageScreen
import com.kitsune.feature.persona.create.PersonaCreationScreen
import com.kitsune.feature.persona.detail.PersonaDetailScreen
import com.kitsune.feature.persona.exportimport.PersonaExportImportScreen
import com.kitsune.feature.persona.imagegen.PersonaImageGenerationScreen
import com.kitsune.feature.persona.tonelibrary.ToneLibraryScreen
import com.kitsune.feature.settings.messages.MessagesScreen
import com.kitsune.feature.settings.proposals.ProposalsScreen
import com.kitsune.feature.universe.chatcreate.UniverseChatCreationScreen
import com.kitsune.feature.universe.create.UniverseCreationScreen
import com.kitsune.feature.universe.detail.UniverseDetailScreen

/**
 * 220ms, matching `KitsuneTheme.motion.standard`.
 *
 * v1 ran every transition at 300ms including a push into a settings sub-page. At the tenth
 * repetition that is the difference between an app that feels quick and one that feels like it is
 * thinking. The distance moved is also shorter — a quarter-width slide under a crossfade rather than
 * a full-width slide, so the eye tracks the content instead of the motion.
 */
private const val TRANSITION_MS = 220

/**
 * Registers a destination with the app's one and only push/pop transition.
 *
 * v1 spelled these four lambdas out at all 38 call sites — roughly 500 lines of copy-paste, and the
 * reason a handful of screens had subtly different (or missing) animations.
 */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    /** Root-level destinations (onboarding, lock, shell) crossfade instead of sliding. */
    fade: Boolean = false,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        arguments = arguments,
        enterTransition = {
            if (fade) fadeIn(tween(TRANSITION_MS))
            else slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(TRANSITION_MS)
            ) + fadeIn(tween(TRANSITION_MS))
        },
        exitTransition = {
            if (fade) fadeOut(tween(TRANSITION_MS))
            else slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(TRANSITION_MS)
            ) + fadeOut(tween(TRANSITION_MS))
        },
        popEnterTransition = {
            if (fade) fadeIn(tween(TRANSITION_MS))
            else slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(TRANSITION_MS)
            ) + fadeIn(tween(TRANSITION_MS))
        },
        popExitTransition = {
            if (fade) fadeOut(tween(TRANSITION_MS))
            else slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(TRANSITION_MS)
            ) + fadeOut(tween(TRANSITION_MS))
        },
        content = content
    )
}

@Composable
fun KitsuneNavHost(
    startRoute: String,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = startRoute) {

        // -------------------------------------------------------------------------------------
        // First run and unlock
        // -------------------------------------------------------------------------------------

        screen(Routes.LANGUAGE_SELECTION, fade = true) {
            LanguageSelectionScreen(
                onLanguageSelected = {
                    navController.navigate(Routes.ACCOUNT_CHOICE) {
                        popUpTo(Routes.LANGUAGE_SELECTION) { inclusive = true }
                    }
                }
            )
        }
        screen(Routes.ACCOUNT_CHOICE, fade = true) {
            AccountChoiceScreen(
                onCreateNewAccount = {
                    navController.navigate(Routes.AGE_VERIFICATION) {
                        popUpTo(Routes.ACCOUNT_CHOICE) { inclusive = true }
                    }
                },
                onRecoverAccount = { navController.navigate(Routes.RECOVER_ACCOUNT) }
            )
        }
        screen(Routes.RECOVER_ACCOUNT) {
            RecoverAccountScreen(onRecovered = { navController.navigate(Routes.HOME) { popUpTo(0) } })
        }
        screen(Routes.AGE_VERIFICATION, fade = true) {
            AgeVerificationScreen(
                onAdultVerified = {
                    navController.navigate(Routes.PIN_SETUP) {
                        popUpTo(Routes.AGE_VERIFICATION) { inclusive = true }
                    }
                },
                onUnderage = {
                    navController.navigate(Routes.UNDERAGE) {
                        popUpTo(Routes.AGE_VERIFICATION) { inclusive = true }
                    }
                }
            )
        }
        screen(Routes.UNDERAGE, fade = true) { UnderageScreen() }
        screen(Routes.PIN_SETUP) {
            PinSetupScreen(onPinConfigured = { navController.navigate(Routes.HOME) { popUpTo(0) } })
        }
        screen(Routes.LOCK, fade = true) {
            LockScreen(
                onUnlockedReal = { navController.navigate(Routes.HOME) { popUpTo(0) } },
                onUnlockedPanic = { navController.navigate(Routes.DECOY_HOME) { popUpTo(0) } }
            )
        }
        screen(Routes.DECOY_HOME, fade = true) {
            DecoyNotesListScreen(
                onOpenNote = { noteId -> navController.navigate(Routes.decoyNoteEditorEdit(noteId)) },
                onCreateNote = { navController.navigate(Routes.decoyNoteEditorCreate()) }
            )
        }
        screen(Routes.DECOY_NOTE_EDITOR_PATTERN) {
            DecoyNoteEditorScreen(onBack = { navController.popBackStack() })
        }

        // -------------------------------------------------------------------------------------
        // The shell
        // -------------------------------------------------------------------------------------

        screen(Routes.HOME, fade = true) {
            KitsuneShell(navController = navController)
        }

        // -------------------------------------------------------------------------------------
        // Histoires
        // -------------------------------------------------------------------------------------

        screen(Routes.CHAT_PATTERN, listOf(navArgument("chatId") { type = NavType.StringType })) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onOpenMemory = { chatId -> navController.navigate(Routes.storyMemory(chatId)) },
                onOpenNovelMode = { chatId -> navController.navigate(Routes.novelMode(chatId)) },
                onOpenTimeline = { chatId -> navController.navigate(Routes.timeline(chatId)) },
                onOpenBranchTree = { chatId -> navController.navigate(Routes.chatBranchTree(chatId)) },
                onOpenImageGeneration = { chatId -> navController.navigate(Routes.imageGeneration(chatId)) },
                onForkChat = { chatId ->
                    navController.navigate(Routes.chat(chatId)) { popUpTo(Routes.HOME) }
                },
                onCreatePersonaForChat = { universeId, chatId ->
                    navController.navigate(Routes.personaCreateForChat(universeId, chatId))
                },
                onCreatePersonaFromNpc = { universeId, chatId, npcId ->
                    navController.navigate(Routes.personaCreateFromNpc(universeId, chatId, npcId))
                }
            )
        }
        screen(Routes.STORY_MEMORY_PATTERN, listOf(navArgument("chatId") { type = NavType.StringType })) {
            StoryMemoryScreen(onBack = { navController.popBackStack() })
        }
        screen(Routes.TIMELINE_PATTERN, listOf(navArgument("chatId") { type = NavType.StringType })) {
            TimelineScreen(onBack = { navController.popBackStack() })
        }
        screen(Routes.CHAT_BRANCH_TREE_PATTERN, listOf(navArgument("chatId") { type = NavType.StringType })) {
            ChatBranchTreeScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) }
            )
        }
        screen(Routes.NOVEL_MODE_PATTERN, listOf(navArgument("chatId") { type = NavType.StringType })) {
            NovelModeScreen(
                onBack = { navController.popBackStack() }
            )
        }
        screen(Routes.IMAGE_GENERATION_PATTERN, listOf(navArgument("chatId") { type = NavType.StringType })) {
            ImageGenerationScreen(
                onBack = { navController.popBackStack() },
                onOpenInspirationWizard = {
                    navController.navigate(Routes.inspirationWizard(InspirationTarget.IMAGE.name))
                }
            )
        }
        screen(Routes.CHAT_LIST_PATTERN, listOf(navArgument("personaId") { type = NavType.StringType })) {
            ChatListScreen(
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                onBack = { navController.popBackStack() }
            )
        }

        // -------------------------------------------------------------------------------------
        // Créer
        // -------------------------------------------------------------------------------------

        screen(
            Routes.PERSONA_CREATE_PATTERN,
            listOf(
                navArgument("universeId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("chatId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("fromNpcId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("draftJobId") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) {
            PersonaCreationScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS_PROFILE) }
            )
        }
        screen(Routes.PERSONA_DETAIL_PATTERN, listOf(navArgument("personaId") { type = NavType.StringType })) {
            PersonaDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                onOpenImageGeneration = { personaId ->
                    navController.navigate(Routes.personaImageGeneration(personaId))
                },
                onOpenExport = { personaId, name -> navController.navigate(Routes.personaExport(personaId, name)) }
            )
        }
        screen(
            Routes.PERSONA_EXPORT_PATTERN,
            listOf(
                navArgument("personaId") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType; defaultValue = "" }
            )
        ) { entry ->
            PersonaExportImportScreen(
                personaId = entry.arguments?.getString("personaId").orEmpty(),
                personaName = entry.arguments?.getString("name").orEmpty(),
                onBack = { navController.popBackStack() },
                viewModel = hiltViewModel()
            )
        }
        screen(
            Routes.PERSONA_IMAGE_GENERATION_PATTERN,
            listOf(navArgument("personaId") { type = NavType.StringType })
        ) {
            PersonaImageGenerationScreen(
                onBack = { navController.popBackStack() },
                onOpenInspirationWizard = {
                    navController.navigate(Routes.inspirationWizard(InspirationTarget.IMAGE.name))
                }
            )
        }
        screen(Routes.UNIVERSE_CREATE_PATTERN, listOf(
            navArgument("draftJobId") { type = NavType.StringType; nullable = true; defaultValue = null }
        )) {
            UniverseCreationScreen(
                onSaved = { universeId ->
                    navController.navigate(Routes.universeDetail(universeId)) { popUpTo(Routes.HOME) }
                },
                onCancel = { navController.popBackStack() }
            )
        }
        screen(
            Routes.UNIVERSE_DETAIL_PATTERN,
            listOf(navArgument("universeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val universeId = backStackEntry.arguments?.getString("universeId") ?: return@screen
            UniverseDetailScreen(
                universeId = universeId,
                onBack = { navController.popBackStack() },
                onOpenPersona = { personaId -> navController.navigate(Routes.personaDetail(personaId)) },
                onCreatePersona = { id -> navController.navigate(Routes.personaCreateForUniverse(id)) },
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                onCreateEnsembleChat = { id -> navController.navigate(Routes.universeChatCreate(id)) }
            )
        }
        screen(
            Routes.UNIVERSE_CHAT_CREATE_PATTERN,
            listOf(navArgument("universeId") { type = NavType.StringType })
        ) {
            UniverseChatCreationScreen(
                onCreated = { chatId ->
                    navController.navigate(Routes.chat(chatId)) {
                        popUpTo(Routes.UNIVERSE_CHAT_CREATE_PATTERN) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }
        screen(Routes.INSPIRATION_WIZARD_PATTERN, listOf(navArgument("target") { type = NavType.StringType })) {
            InspirationWizardScreen(
                onBack = { navController.popBackStack() },
                onDescriptionReady = { target, description ->
                    InspirationDraftHolder.set(description)
                    when (target) {
                        InspirationTarget.IMAGE -> navController.popBackStack()
                        InspirationTarget.PERSONA, InspirationTarget.UNIVERSE -> {
                            val destination = if (target == InspirationTarget.UNIVERSE) {
                                Routes.universeCreate()
                            } else {
                                Routes.personaCreate()
                            }
                            navController.navigate(destination) {
                                popUpTo(Routes.INSPIRATION_WIZARD_PATTERN) { inclusive = true }
                            }
                        }
                    }
                }
            )
        }
        screen(Routes.TONE_LIBRARY) {
            ToneLibraryScreen(onBack = { navController.popBackStack() })
        }

        // -------------------------------------------------------------------------------------
        // Découvrir
        // -------------------------------------------------------------------------------------

        screen(
            Routes.MARKETPLACE_DETAIL_PATTERN,
            listOf(navArgument("listingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val listingId = backStackEntry.arguments?.getString("listingId") ?: return@screen
            ListingDetailScreen(
                listingId = listingId,
                onBack = { navController.popBackStack() },
                onOpenCreator = { creatorId -> navController.navigate(Routes.creatorListings(creatorId)) }
            )
        }
        screen(
            Routes.CREATOR_LISTINGS_PATTERN,
            listOf(navArgument("creatorId") { type = NavType.StringType })
        ) { backStackEntry ->
            val creatorId = backStackEntry.arguments?.getString("creatorId") ?: return@screen
            CreatorListingsScreen(
                creatorId = creatorId,
                onBack = { navController.popBackStack() },
                onOpenListing = { listingId -> navController.navigate(Routes.marketplaceDetail(listingId)) }
            )
        }

        // -------------------------------------------------------------------------------------
        // Profil
        // -------------------------------------------------------------------------------------

        screen(Routes.MY_FOLLOWS) {
            MyFollowsScreen(
                onBack = { navController.popBackStack() },
                onOpenCreatorListings = { creatorId -> navController.navigate(Routes.creatorListings(creatorId)) }
            )
        }
        screen(Routes.MESSAGES) {
            MessagesScreen(onBack = { navController.popBackStack() })
        }
        screen(Routes.PROPOSALS) {
            ProposalsScreen(onBack = { navController.popBackStack() })
        }

        // -------------------------------------------------------------------------------------
        // Réglages
        //
        // Settings is a tab inside the shell, but it is also pushed as a standalone destination from
        // deep screens (persona creation's "configure your models" link). Both must work, so the
        // route stays registered here and the tab renders the same screen without a back arrow.
        // -------------------------------------------------------------------------------------

        screen(Routes.SETTINGS_PROFILE) {
            com.kitsune.feature.settings.SettingsScreen(
                onBack = { navController.popBackStack() },
                startOnProfile = true,
                onAccountDeleted = { navController.navigate(Routes.LOCK) { popUpTo(0) } },
                onOpenProviders = { navController.navigate(Routes.providers()) },
                onOpenModels = { navController.navigate(Routes.models()) },
                onOpenGeneration = { navController.navigate(Routes.generation()) }
            )
        }
        screen(Routes.SETTINGS) {
            com.kitsune.feature.settings.SettingsScreen(
                onBack = { navController.popBackStack() },
                onAccountDeleted = { navController.navigate(Routes.LOCK) { popUpTo(0) } },
                onOpenProviders = { navController.navigate(Routes.providers()) },
                onOpenModels = { navController.navigate(Routes.models()) },
                onOpenGeneration = { navController.navigate(Routes.generation()) }
            )
        }
        screen(Routes.PROVIDERS) {
            ProvidersScreen(onBack = { navController.popBackStack() })
        }
        screen(Routes.GENERATION) {
            GenerationScreen(onBack = { navController.popBackStack() })
        }
        screen(Routes.MODELS) {
            ModelsScreen(
                onBack = { navController.popBackStack() },
                onOpenProviders = { navController.navigate(Routes.providers()) }
            )
        }
    }
}
