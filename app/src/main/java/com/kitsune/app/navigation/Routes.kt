package com.kitsune.app.navigation

/**
 * The route map.
 *
 * v1 had 38 string routes all reachable from the same flat `NavHost`, with no expressed notion of
 * which ones were *destinations the user navigates to* versus *sub-pages of somewhere*. Ten of them
 * (`proposals`, `tone_library`, `messages`, `my_follows`, `my_badges`, `timeline`, `story_memory`,
 * `chat-tree`, `novel`, `inspiration_wizard`) were only reachable from a menu item buried two levels
 * down, which is most of why the app felt like a pile of features rather than a product.
 *
 * v2 keeps the same flat graph — it works, and rebuilding it as nested graphs would buy nothing —
 * but groups the routes by **where they belong in the information architecture**, and funnels the
 * whole app through one tabbed shell ([HOME], rendered by `KitsuneShell`). Every route below is
 * reachable from a tab in at most two taps, and the groups here are the documentation of that.
 */
object Routes {

    // -----------------------------------------------------------------------------------------
    // Pre-shell: first run, unlock, and the duress decoy. Unchanged from v1 — this flow is sound.
    // -----------------------------------------------------------------------------------------

    const val LANGUAGE_SELECTION = "language_selection"
    const val ACCOUNT_CHOICE = "account_choice"
    const val RECOVER_ACCOUNT = "recover_account"
    const val AGE_VERIFICATION = "age_verification"
    const val UNDERAGE = "underage"
    const val PIN_SETUP = "pin_setup"
    const val LOCK = "lock"
    const val DECOY_HOME = "decoy_home"
    const val DECOY_NOTE_EDITOR_PATTERN = "decoy_note_editor?noteId={noteId}"

    /** The tabbed shell. The only top-level destination once the vault is unlocked. */
    const val HOME = "home"

    // -----------------------------------------------------------------------------------------
    // Tab 1 — Histoires. Reading and playing.
    // -----------------------------------------------------------------------------------------

    const val CHAT_PATTERN = "chat/{chatId}"

    /**
     * The five screens that used to be separate menu entries hanging off the chat's overflow. They
     * are still separate destinations (they are full screens with their own scroll state), but they
     * are now presented as one "outils de l'histoire" sheet inside the chat, so the user meets them
     * as facets of the story rather than as unrelated features.
     */
    const val STORY_MEMORY_PATTERN = "story_memory/{chatId}"
    const val TIMELINE_PATTERN = "timeline/{chatId}"
    const val CHAT_BRANCH_TREE_PATTERN = "chat-tree/{chatId}"
    const val NOVEL_MODE_PATTERN = "novel/{chatId}"
    const val IMAGE_GENERATION_PATTERN = "image_generation/{chatId}"

    /** Every story belonging to one persona. Reached from that persona, not from the shelf. */
    const val CHAT_LIST_PATTERN = "chats/{personaId}"

    // -----------------------------------------------------------------------------------------
    // Tab 2 — Créer. Personas and universes, which v1 split across two separate home tabs even
    // though they are the same activity: authoring the cast and the world you then play in.
    // -----------------------------------------------------------------------------------------

    const val PERSONA_CREATE_PATTERN =
        "persona_create?universeId={universeId}&chatId={chatId}&fromNpcId={fromNpcId}&draftJobId={draftJobId}"
    const val PERSONA_DETAIL_PATTERN = "persona_detail/{personaId}"
    const val PERSONA_IMAGE_GENERATION_PATTERN = "persona_image_generation/{personaId}"
    const val UNIVERSE_CREATE = "universe_create"
    const val UNIVERSE_CREATE_PATTERN = "universe_create?draftJobId={draftJobId}"
    const val UNIVERSE_DETAIL_PATTERN = "universe_detail/{universeId}"
    const val UNIVERSE_CHAT_CREATE_PATTERN = "universe_chat_create/{universeId}"

    /** The free "I don't know what to make" Q&A. Entered from the Créer tab's own empty state. */
    const val INSPIRATION_WIZARD_PATTERN = "inspiration_wizard/{target}"

    /** Reusable tone presets. Belongs with authoring, not with Settings where v1 hid it. */
    const val TONE_LIBRARY = "tone_library"

    // -----------------------------------------------------------------------------------------
    // Tab 3 — Découvrir. The community marketplace.
    // -----------------------------------------------------------------------------------------

    const val MARKETPLACE_DETAIL_PATTERN = "marketplace_detail/{listingId}"
    const val CREATOR_LISTINGS_PATTERN = "creator-listings/{creatorId}"

    // -----------------------------------------------------------------------------------------
    // Tab 4 — Profil. Who the user is in the community, plus everything about their Ofudas.
    //
    // The store lives here and nowhere else. In v1 it was a shopping-cart icon in the home app bar,
    // a button in Settings, a button in the chat, a button in novel mode and a launch dialog.
    // -----------------------------------------------------------------------------------------

    const val STORE = "store"

    /** The whole price list on one screen, so pricing is published rather than discovered. */
    const val PRICES = "prices"

    const val MY_BADGES = "my_badges"
    const val MY_FOLLOWS = "my_follows"
    const val MESSAGES = "messages"
    const val PROPOSALS = "proposals"

    // -----------------------------------------------------------------------------------------
    // Tab 5 — Réglages.
    // -----------------------------------------------------------------------------------------

    const val SETTINGS = "settings"

    // -----------------------------------------------------------------------------------------
    // Builders
    // -----------------------------------------------------------------------------------------

    fun decoyNoteEditorCreate() = "decoy_note_editor"
    fun decoyNoteEditorEdit(noteId: String) = "decoy_note_editor?noteId=$noteId"
    fun personaCreate() = "persona_create"
    fun personaCreateForUniverse(universeId: String) = "persona_create?universeId=$universeId"
    fun personaCreateForChat(universeId: String, chatId: String) =
        "persona_create?universeId=$universeId&chatId=$chatId"
    fun personaCreateFromNpc(universeId: String, chatId: String, npcId: String) =
        "persona_create?universeId=$universeId&chatId=$chatId&fromNpcId=$npcId"
    fun personaDraftReview(jobId: String) = "persona_create?draftJobId=$jobId"
    fun personaDetail(personaId: String) = "persona_detail/$personaId"
    fun personaImageGeneration(personaId: String) = "persona_image_generation/$personaId"
    fun inspirationWizard(target: String) = "inspiration_wizard/$target"
    fun chatList(personaId: String) = "chats/$personaId"
    fun chat(chatId: String) = "chat/$chatId"
    fun storyMemory(chatId: String) = "story_memory/$chatId"
    fun timeline(chatId: String) = "timeline/$chatId"
    fun chatBranchTree(chatId: String) = "chat-tree/$chatId"
    fun novelMode(chatId: String) = "novel/$chatId"
    fun imageGeneration(chatId: String) = "image_generation/$chatId"
    fun universeCreate() = "universe_create"
    fun universeDraftReview(jobId: String) = "universe_create?draftJobId=$jobId"
    fun universeDetail(universeId: String) = "universe_detail/$universeId"
    fun universeChatCreate(universeId: String) = "universe_chat_create/$universeId"
    fun store() = "store"
    fun prices() = "prices"
    fun marketplaceDetail(listingId: String) = "marketplace_detail/$listingId"
    fun creatorListings(creatorId: String) = "creator-listings/$creatorId"
    fun proposals() = "proposals"
    fun toneLibrary() = "tone_library"
    fun messages() = "messages"
    fun myFollows() = "my_follows"
    fun myBadges() = "my_badges"
}

/**
 * The shell's tabs. Identified by a plain string rather than an index so a tab can be added or
 * reordered without silently sending the user somewhere else (v1's `TAB_CHATS = 0` constants meant
 * the selected tab was an integer stored in saved state).
 */
enum class HomeTab(val id: String) {
    Stories("stories"),
    Create("create"),
    Discover("discover"),
    Profile("profile"),
    Settings("settings");

    companion object {
        fun fromId(id: String?): HomeTab = entries.firstOrNull { it.id == id } ?: Stories
    }
}
