package com.kitsune.feature.chat.style

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [decideBeat] est pur et déterministe, donc entièrement testable — c'est précisément pour cela que
 * toute la logique décidable vit ici plutôt que dans `ChatViewModel`, qui n'a aucun test.
 */
class NarrativeDirectorTest {

    private fun state(
        turnsSinceComplication: Int = 0,
        turnsSinceQuiet: Int = 0,
        oldestOpenThreadAgeTurns: Int? = null,
        openThreadCount: Int = 0,
        recentTensionSignals: Int = 0,
        totalTurns: Int = 50
    ) = NarrativeState(
        turnsSinceComplication, turnsSinceQuiet, oldestOpenThreadAgeTurns,
        openThreadCount, recentTensionSignals, totalTurns
    )

    @Test
    fun `a story that has just started is never directed`() {
        // La scène d'ouverture doit s'installer : diriger dès le deuxième tour casserait la mise en
        // place au lieu de l'enrichir.
        (0 until NarrativeThresholds.WARMUP_TURNS).forEach { turn ->
            assertNull("tour $turn", decideBeat(state(totalTurns = turn, turnsSinceComplication = 99)))
        }
        assertNotNull(decideBeat(state(totalTurns = NarrativeThresholds.WARMUP_TURNS, turnsSinceComplication = 99)))
    }

    @Test
    fun `high tension asks for a breather rather than another complication`() {
        // Le cas qui justifie l'ordre des règles : empiler une complication sur une scène déjà
        // saturée produit du bruit. L'accalmie passe donc AVANT la complication.
        val beat = decideBeat(state(
            recentTensionSignals = NarrativeThresholds.HIGH_TENSION_SIGNALS,
            turnsSinceQuiet = NarrativeThresholds.MIN_TURNS_BETWEEN_QUIET,
            turnsSinceComplication = 99
        ))
        assertEquals(NarrativeBeat.QUIET, beat)
    }

    @Test
    fun `high tension does not force a breather if one just happened`() {
        // Sinon l'histoire s'arrêterait de progresser : deux accalmies coup sur coup, c'est du surplace.
        val beat = decideBeat(state(
            recentTensionSignals = 5,
            turnsSinceQuiet = 1,
            turnsSinceComplication = 99
        ))
        assertEquals(NarrativeBeat.COMPLICATION, beat)
    }

    @Test
    fun `a thread left dangling too long is called back`() {
        val beat = decideBeat(state(
            oldestOpenThreadAgeTurns = NarrativeThresholds.STALE_THREAD_TURNS,
            turnsSinceComplication = 99
        ))
        assertEquals(NarrativeBeat.CALLBACK, beat)
    }

    @Test
    fun `a recent thread is not called back — it is not forgotten yet`() {
        val beat = decideBeat(state(oldestOpenThreadAgeTurns = 2, turnsSinceComplication = 0))
        assertTrue(beat != NarrativeBeat.CALLBACK)
    }

    @Test
    fun `a conversation running flat gets a complication`() {
        val beat = decideBeat(state(turnsSinceComplication = NarrativeThresholds.COMPLICATION_INTERVAL))
        assertEquals(NarrativeBeat.COMPLICATION, beat)
    }

    @Test
    fun `too many open threads close one instead of opening another`() {
        val beat = decideBeat(state(
            openThreadCount = NarrativeThresholds.THREAD_SATURATION,
            turnsSinceComplication = 0,
            oldestOpenThreadAgeTurns = 1
        ))
        assertEquals(NarrativeBeat.REVELATION, beat)
    }

    @Test
    fun `a calm well-paced story escalates gently`() {
        val beat = decideBeat(state(turnsSinceComplication = 1, openThreadCount = 1, oldestOpenThreadAgeTurns = 1))
        assertEquals(NarrativeBeat.ESCALATION, beat)
    }

    @Test
    fun `every beat renders a non-empty directive, and no beat renders none`() {
        // Une directive vide serait pire que pas de directive : elle consommerait des tokens en fin
        // de contexte — l'emplacement le plus influent — pour ne rien dire.
        NarrativeBeat.entries.forEach { beat ->
            val text = buildBeatDirective(beat)
            assertNotNull(beat.name, text)
            assertTrue(beat.name, !text.isNullOrBlank())
        }
        assertNull(buildBeatDirective(null))
    }

    @Test
    fun `the decision is deterministic — same state, same beat`() {
        val s = state(turnsSinceComplication = 12, openThreadCount = 2, oldestOpenThreadAgeTurns = 5)
        val first = decideBeat(s)
        repeat(20) { assertEquals(first, decideBeat(s)) }
    }

    @Test
    fun `an empty state never crashes`() {
        // Chat neuf, aucune donnée dérivée encore : doit renvoyer null, pas lever.
        assertNull(decideBeat(NarrativeState()))
    }
}
