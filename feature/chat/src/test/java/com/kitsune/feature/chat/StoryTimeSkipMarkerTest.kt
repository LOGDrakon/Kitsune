package com.kitsune.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryTimeSkipMarkerTest {

    @Test
    fun `extracts a trailing STORY_TIME line and strips it from the content`() {
        val content = "Some scene text.\nSTORY_TIME: Late summer, the evening after the festival."

        val (cleaned, extracted) = extractAndStripStoryTimeMarker(content)

        assertEquals("Some scene text.", cleaned)
        assertEquals("Late summer, the evening after the festival.", extracted)
    }

    @Test
    fun `returns the content unchanged and null when no marker is present`() {
        val content = "Some scene text without any marker."

        val (cleaned, extracted) = extractAndStripStoryTimeMarker(content)

        assertEquals(content, cleaned)
        assertNull(extracted)
    }

    @Test
    fun `treats a blank marker value as absent but still strips the line`() {
        val content = "Some scene text.\nSTORY_TIME:"

        val (cleaned, extracted) = extractAndStripStoryTimeMarker(content)

        assertEquals("Some scene text.", cleaned)
        assertNull(extracted)
    }

    @Test
    fun `ignores a marker-looking line that is not the final line`() {
        val content = "STORY_TIME: this reads like a marker but is actually narrated mid-scene.\n" +
            "And then more narrative continues after it."

        val (cleaned, extracted) = extractAndStripStoryTimeMarker(content)

        assertEquals(content, cleaned)
        assertNull(extracted)
    }
}
