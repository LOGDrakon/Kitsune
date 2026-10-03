package com.kitsune.core.memory.lore

import com.kitsune.core.data.local.entities.LoreEntryEntity
import com.kitsune.core.data.local.entities.LoreEntryType
import com.kitsune.core.data.local.entities.MemoryFragmentEntity
import com.kitsune.core.data.local.entities.MemoryFragmentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val CHAT_ID = "chat-1"

class RankLoreEntriesUseCaseTest {

    private val useCase = RankLoreEntriesUseCase()

    private fun entry(
        name: String,
        type: LoreEntryType = LoreEntryType.CHARACTER,
        version: Int = 1,
        aliases: List<String> = emptyList()
    ) = LoreEntryEntity(
        aliases = aliases,
        id = "lore-$name",
        chatId = CHAT_ID,
        entryType = type,
        name = name,
        summary = "$name summary",
        content = "$name content",
        version = version,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun fragmentFor(entry: LoreEntryEntity, embedding: List<Float>) = MemoryFragmentEntity(
        id = "frag-${entry.id}",
        chatId = CHAT_ID,
        sourceType = MemoryFragmentSource.LORE_ENTRY,
        sourceKey = entry.id,
        text = entry.content,
        embedding = embedding,
        createdAt = 0L
    )

    /** Query pointing straight at [1, 0]; an entry embedded at [0, 1] is orthogonal to it. */
    private val query = listOf(1f, 0f)
    private val aligned = listOf(1f, 0f)
    private val orthogonal = listOf(0f, 1f)

    private fun names(result: List<LoreEntryEntity>) = result.map { it.name }

    @Test
    fun `semantically relevant entries outrank merely recent ones`() {
        val recent = entry("Recent")
        val relevant = entry("Relevant")
        // `entries` arrives updatedAt DESC, so "Recent" has the recency advantage.
        val result = useCase(
            entries = listOf(recent, relevant),
            fragments = listOf(fragmentFor(recent, orthogonal), fragmentFor(relevant, aligned)),
            queryEmbedding = query,
            rawWindowText = "",
            budget = 2
        )

        assertEquals(listOf("Relevant", "Recent"), names(result))
    }

    @Test
    fun `an entity named in the raw window is never dropped`() {
        val hot = entry("Hot")
        // Cold: last by recency, lowest importance, orthogonal to the query — it loses on every term.
        val cold = entry("Ombrelune", version = 1)
        val entries = List(20) { entry("Filler$it", version = 10) } + hot + cold

        val result = useCase(
            entries = entries,
            fragments = entries.map { fragmentFor(it, if (it == cold) orthogonal else aligned) },
            queryEmbedding = query,
            rawWindowText = "Le narrateur croise Ombrelune devant la taverne.",
            budget = 5
        )

        assertTrue("pinned entity must survive the budget", names(result).contains("Ombrelune"))
    }

    @Test
    fun `whole-word pinning matches punctuation and accents but not a longer word`() {
        val aria = entry("Aria")
        val elise = entry("Élise")
        val entries = listOf(aria, elise)
        val fragments = entries.map { fragmentFor(it, orthogonal) }

        fun pinnedIn(text: String) = names(
            useCase(entries, fragments, query, rawWindowText = text, budget = 1)
        ).first()

        assertEquals("Aria", pinnedIn("\"Aria\", dit-il."))
        assertEquals("Aria", pinnedIn("C'est Aria, la mercenaire."))
        assertEquals("Élise", pinnedIn("Élise entre dans la piece."))
        assertEquals("Élise", pinnedIn("Il salue Élise."))
    }

    @Test
    fun `a name embedded inside a longer word does not pin the entry`() {
        // Java's \b is defined over [a-zA-Z0-9_], which is why this uses \p{L} lookaround instead.
        val aria = entry("Aria")
        val other = entry("Kaito")
        val entries = listOf(aria, other)
        val fragments = listOf(fragmentFor(aria, orthogonal), fragmentFor(other, aligned))

        val result = useCase(entries, fragments, query, rawWindowText = "Ariane traverse la cour.", budget = 1)

        assertEquals(listOf("Kaito"), names(result))
    }

    @Test
    fun `an alias mentioned in the raw window pins the entry`() {
        val aria = entry("Aria", aliases = listOf("la mercenaire"))
        val other = entry("Kaito")
        val entries = List(10) { entry("Filler$it", version = 10) } + aria + other

        val result = useCase(
            entries = entries,
            fragments = entries.map { fragmentFor(it, if (it == aria) orthogonal else aligned) },
            queryEmbedding = query,
            rawWindowText = "La mercenaire se retourne vers lui.",
            budget = 3
        )

        assertTrue(names(result).contains("Aria"))
    }

    @Test
    fun `falls back to the previous recency roster when the embedder failed`() {
        val entries = (1..20).map { entry("Entry$it") }

        val result = useCase(entries, fragments = emptyList(), queryEmbedding = null, rawWindowText = "Entry20", budget = 5)

        assertEquals(entries.take(5), result)
    }

    @Test
    fun `an entry with no indexed fragment still ranks instead of crashing`() {
        val withFragment = entry("Indexed")
        val withoutFragment = entry("NotIndexedYet")

        val result = useCase(
            entries = listOf(withoutFragment, withFragment),
            fragments = listOf(fragmentFor(withFragment, aligned)),
            queryEmbedding = query,
            rawWindowText = "",
            budget = 2
        )

        assertEquals(2, result.size)
        assertEquals("Indexed", result.first().name)
    }

    @Test
    fun `events rank below characters at equal relevance`() {
        // Events now have their own ## Story Chronology section and should stop crowding the roster.
        val character = entry("Kaito", type = LoreEntryType.CHARACTER)
        val event = entry("Le duel", type = LoreEntryType.EVENT)
        val entries = listOf(event, character)

        val result = useCase(
            entries = entries,
            fragments = entries.map { fragmentFor(it, aligned) },
            queryEmbedding = query,
            rawWindowText = "",
            budget = 2
        )

        assertEquals(listOf("Kaito", "Le duel"), names(result))
    }

    @Test
    fun `never returns more than the budget even when many entries are pinned`() {
        val entries = (1..10).map { entry("Pinned$it") }
        val rawWindow = entries.joinToString(" ") { it.name }

        val result = useCase(entries, entries.map { fragmentFor(it, aligned) }, query, rawWindow, budget = 3)

        assertEquals(3, result.size)
    }

    @Test
    fun `an empty roster or a zero budget yields nothing`() {
        assertTrue(useCase(emptyList(), emptyList(), query, "", budget = 5).isEmpty())
        assertTrue(useCase(listOf(entry("A")), emptyList(), query, "", budget = 0).isEmpty())
    }

    @Test
    fun `non-lore fragments are ignored when scoring`() {
        val target = entry("Target")
        val chunkFragment = MemoryFragmentEntity(
            id = "chunk-frag",
            chatId = CHAT_ID,
            sourceType = MemoryFragmentSource.MESSAGE_CHUNK,
            // Deliberately colliding with the entry id: a MESSAGE_CHUNK must never be mistaken
            // for an entry's own embedding.
            sourceKey = target.id,
            text = "unrelated",
            embedding = aligned,
            createdAt = 0L
        )

        val result = useCase(listOf(target), listOf(chunkFragment), query, "", budget = 1)

        assertEquals(1, result.size)
        assertFalse(result.first().content.isBlank())
    }
}
