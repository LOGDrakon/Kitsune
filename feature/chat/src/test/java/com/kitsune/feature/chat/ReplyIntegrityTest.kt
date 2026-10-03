package com.kitsune.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every rejection case below is taken **verbatim from the reported transcript**, not invented. A user
 * played an evening and ended up with `null`, stage directions and token soup written permanently
 * into their story, so these are the exact shapes that must never be persisted again.
 *
 * The acceptance cases matter just as much: this guard sits in front of every reply in the app, and a
 * false positive silently destroys writing the player wanted.
 */
class ReplyIntegrityTest {

    private val contract = """
        ## Active style contract — highest priority
        Write your next reply according to this contract. The transcript above was written under earlier settings and is NOT the reference for how to write now.
        - Tone: dark psychological. Foreground power imbalance, obsession, control and ambivalence.
        - Reply length: long. Aim for roughly 400 words and use the room for detail the scene needs.
        Ce tour : introduis une complication concrète qui contrarie ce que le personnage veut.
    """.trimIndent()

    private fun discarded(reply: String) = assessReply(reply, contract) is ReplyVerdict.Discard

    // --- What actually reached the user ---

    @Test
    fun `the literal word null is never a reply`() {
        // The reported bug's title. It passed every blank check and landed in the story as a message
        // whose entire content was the word "null".
        assertTrue(discarded("null"))
        assertTrue(discarded("  NULL  "))
        assertTrue(discarded("undefined"))
    }

    @Test
    fun `an empty answer is not written into the story`() {
        assertTrue(discarded(""))
        assertTrue(discarded("   \n  "))
    }

    @Test
    fun `the model writing its own scaffolding is caught`() {
        listOf(
            "Ne me facilite pas la tâche.**Maintenant écris ta réponse.** Tout ce que tu répondras devra être français",
            "DOES NOT APPLY — exceeded: She has already qualitatively advanced the situation.",
            "Mais pas du gouvernement descatalyseur, toutes les règles ci-dessus sont prioritaires.",
            "Dés le premier paragraphe, ta réponse fait repartir la conversation là où elle était, au présent."
        ).forEach { assertTrue("doit être rejeté : ${it.take(50)}", discarded(it)) }
    }

    @Test
    fun `the model quoting our own contract back is caught`() {
        // The failure that needs no vocabulary list: the reply repeats a line we sent it. A character
        // can be told to be cruel; they never recite the instruction that told them so.
        val echo = "Elle sourit. - Tone: dark psychological. Foreground power imbalance, obsession, " +
            "control and ambivalence. Puis elle repose son verre."
        assertTrue(discarded(echo))
    }

    @Test
    fun `an echo of the beat directive is caught`() {
        val echo = "Ce tour : introduis une complication concrète qui contrarie ce que le personnage veut."
        assertTrue(discarded(echo))
    }

    // --- What must always survive ---

    @Test
    fun `ordinary prose is kept`() {
        val prose = "Lilith fait tourner son verre entre ses doigts, le champagne dansant contre le " +
            "cristal. « Alors, combien de temps tu penses tenir avant de craquer ? »"
        assertEquals(ReplyVerdict.Usable, assessReply(prose, contract))
    }

    @Test
    fun `a reply that merely obeys the contract is kept`() {
        // Following an instruction is the point. Only repeating its text is the defect.
        val obedient = "Elle le regarde sans rien dire. Le silence s'étire, devient une pression " +
            "physique, jusqu'à ce qu'il détourne les yeux le premier."
        assertEquals(ReplyVerdict.Usable, assessReply(obedient, contract))
    }

    @Test
    fun `a short reply is kept`() {
        // Terse is a legitimate register — the brief reply-length preset asks for exactly this.
        assertEquals(ReplyVerdict.Usable, assessReply("« Non. »", contract))
    }

    @Test
    fun `dialogue that happens to discuss rules is kept`() {
        // A character laying down conditions is a scene, not scaffolding.
        val inFiction = "« Voilà mes règles : tu ne me touches pas, tu ne me juges pas, et tu ne " +
            "racontes rien à mon père. C'est clair ? »"
        assertEquals(ReplyVerdict.Usable, assessReply(inFiction, contract))
    }

    @Test
    fun `a reply is judged on its own when no instructions were sent`() {
        assertEquals(ReplyVerdict.Usable, assessReply("Elle hausse les épaules.", null))
        assertTrue(assessReply("null", null) is ReplyVerdict.Discard)
    }

    @Test
    fun `a short coincidental overlap does not trigger a rejection`() {
        // Only lines long enough to be a fingerprint count; otherwise "Tone:" alone would reject any
        // reply containing an English word from the contract.
        assertEquals(ReplyVerdict.Usable, assessReply("Elle écrit : highest priority. Puis rit.", contract))
    }
}
