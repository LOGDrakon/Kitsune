package com.kitsune.feature.chat

/**
 * Appended only to the [com.kitsune.feature.chat.DirectorToolKind.TIME_SKIP] instruction — a
 * deterministic user action, so the story-time anchor (`ChatEntity.storyTimeAnchor`) can be
 * updated immediately from this single call instead of waiting for the next background
 * summarization/lore-extraction batch (`ExtractLoreEntriesUseCase`) to (maybe) catch up with it.
 */
const val STORY_TIME_SKIP_MARKER_INSTRUCTION =
    "\n\nAfter writing the scene, end your reply with one final line in exactly this format and " +
        "nothing after it: STORY_TIME: <a short, self-contained description of the new point in " +
        "the story's timeline — date, season, time of day, elapsed time since a landmark event>."

private val STORY_TIME_MARKER_REGEX = Regex("""(?m)^STORY_TIME:\s*(.*)$""")

/**
 * Extracts the trailing `STORY_TIME: ...` line requested by [STORY_TIME_SKIP_MARKER_INSTRUCTION],
 * returning the content with that line (and the blank line it leaves behind) removed, plus the
 * extracted value (`null` if absent or blank). Only matches when the marker is genuinely the
 * LAST line of the text — deliberately not "anywhere in the text" — so a narrative sentence that
 * happens to start with "STORY_TIME:" (unlikely, but not impossible from a model that doesn't
 * follow formatting instructions perfectly) is never mistaken for the real marker and stripped
 * from the middle of the scene.
 */
fun extractAndStripStoryTimeMarker(content: String): Pair<String, String?> {
    val trimmedEnd = content.trimEnd()
    val lastLineStart = trimmedEnd.lastIndexOf('\n') + 1
    val lastLine = trimmedEnd.substring(lastLineStart)

    val match = STORY_TIME_MARKER_REGEX.matchEntire(lastLine) ?: return content to null
    val value = match.groupValues[1].trim().ifBlank { null }
    val cleaned = trimmedEnd.substring(0, lastLineStart).trimEnd('\n')
    return cleaned to value
}
