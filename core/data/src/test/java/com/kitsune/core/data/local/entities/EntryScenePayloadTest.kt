package com.kitsune.core.data.local.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entry scenes were the one piece of authored persona content that reached neither the export file
 * nor the marketplace — a character published with three written openings arrived with none, and the
 * author had no way to notice because their own copy was intact. These tests pin the round trip that
 * makes that impossible to reintroduce.
 */
class EntryScenePayloadTest {

    private fun scene(id: String = "scene-1") = EntrySceneEntity(
        id = id,
        personaId = "persona-1",
        title = "Retour de mission",
        scenario = "Elle rentre blessée, bien après minuit, et tu es encore debout.",
        firstMessage = "La porte claque. « Ne dis rien. »",
        createdAt = 42L
    )

    @Test
    fun `every authored field survives a full round trip`() {
        val original = scene()
        val restored = decodeEntryScenes(encodeEntryScenes(listOf(original)))
            .single()
            .toEntity(personaId = "persona-on-the-other-side")

        assertEquals(original.title, restored.title)
        assertEquals(original.scenario, restored.scenario)
        assertEquals(original.firstMessage, restored.firstMessage)
    }

    @Test
    fun `the receiving install owns the identity and the persona`() {
        val restored = decodeEntryScenes(encodeEntryScenes(listOf(scene())))
            .single()
            .toEntity(personaId = "persona-on-the-other-side")

        assertEquals("persona-on-the-other-side", restored.personaId)
        assertNotEquals("scene-1", restored.id)
    }

    @Test
    fun `two imports of the same scene do not collide`() {
        val payload = decodeEntryScenes(encodeEntryScenes(listOf(scene()))).single()
        assertNotEquals(payload.toEntity("p").id, payload.toEntity("p").id)
    }

    @Test
    fun `several scenes survive together, in order`() {
        val scenes = listOf(scene("a").copy(title = "A"), scene("b").copy(title = "B"))
        assertEquals(listOf("A", "B"), decodeEntryScenes(encodeEntryScenes(scenes)).map { it.title })
    }

    @Test
    fun `a persona with no scenes carries no field at all`() {
        assertNull(encodeEntryScenes(emptyList()))
    }

    @Test
    fun `a listing published before scenes travelled decodes to none`() {
        assertTrue(decodeEntryScenes(null).isEmpty())
        assertTrue(decodeEntryScenes("  ").isEmpty())
    }

    @Test
    fun `a damaged blob loses the scenes, never the persona`() {
        assertTrue(decodeEntryScenes("{not json").isEmpty())
    }

    @Test
    fun `a scene that only names itself still imports`() {
        val restored = decodeEntryScenes("""[{"title":"Minimal"}]""").single().toEntity("p")
        assertEquals("Minimal", restored.title)
        assertEquals("", restored.scenario)
        assertEquals("", restored.firstMessage)
    }
}
