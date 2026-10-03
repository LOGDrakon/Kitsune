package com.kitsune.core.memory.timeline

import com.kitsune.core.data.local.entities.KeyMomentEntity
import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.MomentType
import com.kitsune.core.data.local.entities.StoryMood
import com.kitsune.core.data.repository.KeyMomentRepository
import com.kitsune.core.data.repository.LoreEntryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHAT_ID = "chat-1"

class BuildStoryChronologyUseCaseTest {

    private val keyMomentRepository = mockk<KeyMomentRepository>()
    private val loreEntryRepository = mockk<LoreEntryRepository>()

    private val useCase = BuildStoryChronologyUseCase(keyMomentRepository, loreEntryRepository)

    private fun moment(
        title: String,
        summary: String = "$title happened",
        anchorCreatedAt: Long,
        momentOrder: Int = 0,
        storyTimeLabel: String = ""
    ) = KeyMomentEntity(
        id = "moment-$title",
        chatId = CHAT_ID,
        messageId = "msg-1",
        momentType = MomentType.PLOT_TWIST,
        mood = StoryMood.TENSE,
        title = title,
        summary = summary,
        snippets = "",
        isAutoDetected = true,
        createdAt = 0L,
        momentOrder = momentOrder,
        anchorCreatedAt = anchorCreatedAt,
        storyTimeLabel = storyTimeLabel
    )

    private fun event(
        name: String,
        summary: String = "$name happened",
        anchorCreatedAt: Long,
        occurredAt: String = "un soir d'hiver",
        type: LoreEntryType = LoreEntryType.EVENT
    ) = LoreEntryEntity(
        id = "lore-$name",
        chatId = CHAT_ID,
        entryType = type,
        name = name,
        summary = summary,
        content = "content of $name",
        occurredAt = occurredAt,
        anchorCreatedAt = anchorCreatedAt,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `returns an empty ledger when there is nothing to show`() {
        assertTrue(useCase.build(emptyList(), emptyList(), maxEntries = 12).isEmpty())
    }

    @Test
    fun `orders entries oldest first by anchor`() {
        val result = useCase.build(
            moments = listOf(
                moment("Third", anchorCreatedAt = 300),
                moment("First", anchorCreatedAt = 100),
                moment("Second", anchorCreatedAt = 200)
            ),
            events = emptyList(),
            maxEntries = 12
        )

        assertEquals(3, result.size)
        assertTrue(result[0].contains("First"))
        assertTrue(result[1].contains("Second"))
        assertTrue(result[2].contains("Third"))
    }

    @Test
    fun `legacy rows with no anchor sort first in insertion order`() {
        // Rows written before migration v27->v28 have anchorCreatedAt = 0. They tie, fall back to
        // momentOrder, and land at the front — which is correct, they are the oldest by construction.
        val result = useCase.build(
            moments = listOf(
                moment("Recent", anchorCreatedAt = 500),
                moment("LegacyB", anchorCreatedAt = 0, momentOrder = 2),
                moment("LegacyA", anchorCreatedAt = 0, momentOrder = 1)
            ),
            events = emptyList(),
            maxEntries = 12
        )

        assertTrue(result[0].contains("LegacyA"))
        assertTrue(result[1].contains("LegacyB"))
        assertTrue(result[2].contains("Recent"))
    }

    @Test
    fun `collapses an event that restates a key moment from the same batch, keeping the moment`() {
        val result = useCase.build(
            moments = listOf(moment("Le pacte au port", summary = "Aria scelle un pacte au port sous la pluie", anchorCreatedAt = 100)),
            events = listOf(event("Le pacte au port", summary = "Aria scelle un pacte au port sous la pluie", anchorCreatedAt = 100)),
            maxEntries = 12
        )

        assertEquals(1, result.size)
        // The key moment survives: it is the one carrying a title and a mood.
        assertTrue(result[0].contains("Le pacte au port"))
    }

    @Test
    fun `keeps two distinct beats extracted from the same batch`() {
        val result = useCase.build(
            moments = listOf(moment("Duel sur les toits", summary = "Kaito affronte le capitaine", anchorCreatedAt = 100)),
            events = listOf(event("Incendie du grenier", summary = "Le grenier brule pendant la nuit", anchorCreatedAt = 100)),
            maxEntries = 12
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `caps the ledger while preserving the two oldest entries`() {
        val moments = (1..20).map { moment("Beat$it", anchorCreatedAt = it * 100L) }

        val result = useCase.build(moments, emptyList(), maxEntries = 6)

        // 6 rendered entries + 1 elision line.
        assertEquals(7, result.size)
        assertTrue("the inciting incident must survive", result[0].contains("Beat1"))
        assertTrue(result[1].contains("Beat2"))
        assertTrue("elision marker expected", result[2].contains("older events omitted"))
        assertTrue("the most recent beat must survive", result.last().contains("Beat20"))
    }

    @Test
    fun `numbers rendered entries consecutively across the elision`() {
        val moments = (1..20).map { moment("Beat$it", anchorCreatedAt = it * 100L) }

        val result = useCase.build(moments, emptyList(), maxEntries = 6)

        val numbers = result.filterNot { it.contains("older events omitted") }.map { it.substringBefore('.').toInt() }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), numbers)
    }

    @Test
    fun `renders the in-fiction label in brackets, and omits the brackets when absent`() {
        val result = useCase.build(
            moments = listOf(
                moment("Labelled", anchorCreatedAt = 100, storyTimeLabel = "Early winter, mid-morning"),
                moment("Unlabelled", anchorCreatedAt = 200)
            ),
            events = emptyList(),
            maxEntries = 12
        )

        assertTrue(result[0].contains("[Early winter, mid-morning]"))
        assertFalse(result[1].contains("["))
    }

    @Test
    fun `an empty maxEntries budget yields nothing`() {
        val result = useCase.build(listOf(moment("Beat", anchorCreatedAt = 1)), emptyList(), maxEntries = 0)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `only event lore entries with a stated in-fiction date reach the ledger`() = runTest {
        coEvery { keyMomentRepository.getByChat(CHAT_ID) } returns emptyList()
        coEvery { loreEntryRepository.getByChat(CHAT_ID) } returns listOf(
            event("Aria", anchorCreatedAt = 100, type = LoreEntryType.CHARACTER),
            event("Le port", anchorCreatedAt = 200, type = LoreEntryType.LOCATION),
            event("Sans date", anchorCreatedAt = 300, occurredAt = ""),
            event("Le duel", anchorCreatedAt = 400)
        )

        val result = useCase(CHAT_ID, maxEntries = 12)

        assertEquals(1, result.size)
        assertTrue(result[0].contains("Le duel"))
    }
}
