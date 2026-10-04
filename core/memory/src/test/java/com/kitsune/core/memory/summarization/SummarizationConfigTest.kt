package com.kitsune.core.memory.summarization

import com.kitsune.core.common.memory.MemorySettingsHolder
import com.kitsune.core.data.local.entities.ChatEntity
import com.kitsune.core.data.local.entities.ChatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SummarizationConfigTest {

    @Before
    fun reset() {
        MemorySettingsHolder.apply(rawWindowSize = 40, loreEntries = 12, detailedExtraction = false)
    }

    private fun chat(rawWindow: Int? = null, lore: Int? = null) = ChatEntity(
        id = "c", universeId = null, personaId = "p", title = "", mode = ChatMode.CHAT,
        memoryRawWindow = rawWindow, memoryLoreEntries = lore, createdAt = 0L, updatedAt = 0L
    )

    @Test
    fun `a story without its own values follows the global settings`() {
        assertEquals(40, SummarizationConfig.rawWindowSize(chat()))
        assertEquals(12, SummarizationConfig.maxLoreEntries(chat()))
        assertEquals(4, SummarizationConfig.detailedLoreEntries(chat()))
        assertEquals(12, SummarizationConfig.chronologyMaxEntries(chat()))
        assertFalse(SummarizationConfig.detailedExtraction(chat()))
    }

    @Test
    fun `a story's own values win, and the reserved window moves with the raw window`() {
        val saga = chat(rawWindow = 60, lore = 20)
        assertEquals(60, SummarizationConfig.rawWindowSize(saga))
        assertEquals(60, SummarizationConfig.reservedWindow(saga))
        assertEquals(20, SummarizationConfig.maxLoreEntries(saga))
        assertEquals(6, SummarizationConfig.detailedLoreEntries(saga))
        assertEquals(20, SummarizationConfig.chronologyMaxEntries(saga))
        assertTrue(SummarizationConfig.detailedExtraction(saga))
    }

    @Test
    fun `a small story roster turns the detailed extraction off even when the global one is on`() {
        MemorySettingsHolder.apply(rawWindowSize = 60, loreEntries = 20, detailedExtraction = true)
        assertFalse(SummarizationConfig.detailedExtraction(chat(rawWindow = 24, lore = 8)))
        assertEquals(2, SummarizationConfig.detailedLoreEntries(chat(rawWindow = 24, lore = 8)))
    }
}
