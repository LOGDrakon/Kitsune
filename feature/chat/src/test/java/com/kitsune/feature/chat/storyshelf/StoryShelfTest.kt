package com.kitsune.feature.chat.storyshelf

import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shelf is the app's new front door, and `feature:chat` still has no `ChatViewModelTest` — so
 * everything decidable about it lives here, as pure functions, following the same rule that produced
 * `ChatStyleContract`, `NarrativeDirector` and `StoryCardPresets`.
 *
 * The badge is the part worth being careful about: it is the only thing on the shelf that makes a
 * claim ("something happened here"), and a badge that lies is worse than no badge at all.
 */
class StoryShelfTest {

    private fun chat(
        id: String = "c",
        title: String = "",
        updatedAt: Long = 1_000L,
        lastVisitedAt: Long = 0L
    ) = ChatEntity(
        id = id,
        universeId = null,
        personaId = "p",
        title = title,
        mode = ChatMode.CHAT,
        updatedAt = updatedAt,
        lastVisitedAt = lastVisitedAt,
        createdAt = 0L
    )

    private fun story(
        id: String = "s",
        unseen: Boolean = false,
        updatedAt: Long = 0L
    ) = ShelfStory(
        chatId = id,
        title = id,
        coverImageId = null,
        avatarImageId = null,
        chapterCount = 0,
        isEnsemble = false,
        hasUnseenDevelopment = unseen,
        updatedAt = updatedAt
    )

    // --- The badge ---

    @Test
    fun `a story that moved after the last visit is flagged`() {
        assertTrue(hasUnseenDevelopment(chat(updatedAt = 500L, lastVisitedAt = 100L)))
    }

    @Test
    fun `a story untouched since the last visit is not flagged`() {
        assertFalse(hasUnseenDevelopment(chat(updatedAt = 100L, lastVisitedAt = 500L)))
        assertFalse(hasUnseenDevelopment(chat(updatedAt = 100L, lastVisitedAt = 100L)))
    }

    @Test
    fun `stories that predate the field never light up`() {
        // Every existing conversation carries lastVisitedAt = 0. Treating that as "never seen" would
        // badge the user's entire library on the first launch after the migration, which tells them
        // nothing and trains them to ignore the badge forever.
        assertFalse(hasUnseenDevelopment(chat(updatedAt = 9_999L, lastVisitedAt = 0L)))
    }

    // --- The order ---

    @Test
    fun `what moved without you comes first`() {
        // Otherwise a story that developed three weeks ago stays buried under conversations opened
        // yesterday and closed immediately — which is exactly the shelf's reason to exist.
        val ordered = shelfOrder(
            listOf(
                story("recent-but-quiet", unseen = false, updatedAt = 900L),
                story("old-but-moved", unseen = true, updatedAt = 100L)
            )
        )
        assertEquals(listOf("old-but-moved", "recent-but-quiet"), ordered.map { it.chatId })
    }

    @Test
    fun `within a group, most recent first`() {
        val ordered = shelfOrder(
            listOf(
                story("a", unseen = true, updatedAt = 100L),
                story("b", unseen = true, updatedAt = 900L)
            )
        )
        assertEquals(listOf("b", "a"), ordered.map { it.chatId })
    }

    @Test
    fun `ordering keeps every story`() {
        val input = (1..5).map { story("s$it", unseen = it % 2 == 0, updatedAt = it * 10L) }
        assertEquals(input.size, shelfOrder(input).size)
        assertEquals(input.map { it.chatId }.toSet(), shelfOrder(input).map { it.chatId }.toSet())
    }

    // --- The title ---

    @Test
    fun `a user-given title always wins`() {
        assertEquals(
            "Mon histoire",
            shelfTitle(chat(title = "Mon histoire"), "Chapitre premier", "Aria", "Histoire")
        )
    }

    @Test
    fun `an unnamed story takes its first chapter's title`() {
        // The chaptering pass already asks the model for an evocative title and it has never been
        // displayed anywhere — this is the whole reason a shelf can have real spines.
        assertEquals("Le retour de mission", shelfTitle(chat(), "Le retour de mission", "Aria", "Histoire"))
    }

    @Test
    fun `before any chapter, the character names the story`() {
        assertEquals("Aria", shelfTitle(chat(), null, "Aria", "Histoire"))
        assertEquals("Aria", shelfTitle(chat(), "", "Aria", "Histoire"))
    }

    @Test
    fun `an ensemble scene with no cast name still gets a spine`() {
        assertEquals("Histoire", shelfTitle(chat(), null, null, "Histoire"))
        assertEquals("Histoire", shelfTitle(chat(), "  ".trim(), "", "Histoire"))
    }
}
