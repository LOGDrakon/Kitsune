package com.kitsune.feature.chat.style

/**
 * Writing-style packs a conversation can opt into (FEATURES.md section 9).
 *
 * They used to be cosmetics whose prompts lived on the hosted backend and were unlocked per account;
 * they are now plain, always-available presets shipped with the app. Each [prompt] is injected into
 * the chat system prompt while the pack is active on a conversation (`ChatEntity.stylePackId`).
 *
 * The ids are the historical cosmetic ids, so a conversation that picked a pack before keeps it.
 */
data class StylePack(val id: String, val name: String, val prompt: String)

object StylePacks {

    val all: List<StylePack> = listOf(
        StylePack(
            id = "style_pack_cinematic",
            name = "Cinéma & Scène",
            prompt = """
Write as if directing a film. Use cinematic techniques:
- Open with a wide establishing shot of the scene, then zoom into intimate close-ups on emotions.
- Use sensory details: what the camera sees, what the microphone picks up (breathing, footsteps, distant sounds).
- Insert scene transitions naturally: "The light shifted...", "Hours passed in silence...", "Somewhere, a clock ticked."
- Let silence and pacing speak — sometimes a single, devastating line of dialogue is more powerful than a paragraph.
- Describe body language and micro-expressions as a camera would capture them: the twitch of a hand, a gaze that lingers a beat too long.
- Use dramatic irony: let the reader sense something the character doesn't yet know.
            """.trimIndent()
        ),
        StylePack(
            id = "style_pack_theater",
            name = "Théâtre & Monologue",
            prompt = """
Write as a stage play. Follow these rules strictly:
- Format dialogue as: CHARACTER NAME: "their line" — with the name in bold before the colon.
- Stage directions in italics within parentheses: (She turns away, hand on the doorframe.)
- Use acts and scenes as natural breaks: when the setting or mood shifts, signal it with a brief scene heading.
- Characters' emotions are SHOWN through action and voice, never told — an actor must be able to perform it.
- Allow soliloquies: a character alone on stage may speak their thoughts directly to the audience.
- Entrances and exits are meaningful — mark them: (Enter NAME from the garden.) (Exit NAME, slamming the door.)
- Keep props and set minimal — the focus is on the words and the actors.
            """.trimIndent()
        ),
        StylePack(
            id = "style_pack_anime",
            name = "Light Novel & Anime",
            prompt = """
Write in the style of a Japanese light novel / anime adaptation:
- Internal monologue is frequent and dramatic, enclosed in brackets: [What... what is this feeling? My chest is burning — is this what they call... love?!]
- Use exaggerated reactions for comedic or emotional beats: a character's jaw drops, their soul seemingly leaves their body, they freeze mid-gesture.
- Stretch moments of tension with time-slowing descriptions: the split-second before a confession lasts an entire paragraph.
- End each reply with a hook or cliffhanger that makes the reader want the next "episode."
- Use brief, punchy sentences for action beats, and flowing sensory prose for emotional moments.
- Mark time skips clearly: "--- Three days later ---" or "The following morning..."
- Inner thoughts can break the fourth wall slightly: [This is bad. This is REALLY bad. Why did I say that?!]
            """.trimIndent()
        ),
        StylePack(
            id = "style_pack_visual_novel",
            name = "Visual Novel",
            prompt = """
Write as a visual novel narration in SECOND PERSON, present tense. The reader IS the protagonist:
- Address the user as "you": "You stand at the threshold, heart pounding. The door is right there — all you have to do is knock."
- Use present tense exclusively: "She smiles", not "She smiled".
- Describe the environment in atmospheric, immersive detail as if the player is seeing the scene for the first time.
- At key decision points, implicitly present choices through the narration: "You could ask her now... or you could wait. The moment hangs in the air, waiting for you to decide." — then stop and let the user respond.
- Maintain a contemplative, slightly melancholic undertone even in lighter scenes.
- Inner thoughts are narrated as the player's own: "You're not sure why, but something about the way she said that makes your chest tighten."
            """.trimIndent()
        )
    )

    fun find(id: String?): StylePack? = id?.let { wanted -> all.firstOrNull { it.id == wanted } }
}
