package com.kitsune.core.network.persona

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asking for five personas used to return five moods of the same character.
 *
 * The cause was an instruction no call could follow: each proposal is an independent request, and
 * every one of them was told to "invent a genuinely different character concept **from the others**"
 * — siblings it never sees. Told to differ from something unobservable, a model can only be
 * generically different, which means it lands on the modal answer every time.
 *
 * These tests pin the two things that replaced it, both of which a single call can actually act on:
 * a prescribed angle per index, and the concepts already produced in this batch.
 */
class PersonaProposalDifferentiationTest {

    /** The two functions under test read no injected state, so relaxed mocks are enough to build it. */
    private val useCase = GenerateQuickPersonaUseCase(
        chatCompletionRepository = mockk(relaxed = true),
        llmModelResolver = mockk(relaxed = true),
        appLanguageManager = mockk(relaxed = true),
        userProfileStore = mockk(relaxed = true)
    )

    private fun message(index: Int, count: Int, seen: List<String> = emptyList()) =
        useCase.buildUserMessage("une rivale au travail", index, count, seen)

    // --- A single proposal is left alone ---

    @Test
    fun `asking for one persona adds no differentiation noise`() {
        // There is nothing to differ from, so the user's own words should reach the model unchanged.
        assertEquals("une rivale au travail", message(index = 0, count = 1))
    }

    // --- Prescribed angles ---

    @Test
    fun `each proposal in a batch is pushed toward a different part of the character`() {
        // The heart of the fix: five calls must not receive five copies of the same instruction.
        val angles = (0 until 5).map { message(it, 5) }
        assertEquals("every proposal must get its own angle", 5, angles.toSet().size)
    }

    @Test
    fun `the first proposal is already steered, before any sibling exists`() {
        // Nothing has been generated yet, so feedback cannot help here — only a prescribed angle can.
        val first = message(index = 0, count = 3)
        assertTrue(first.contains("Angle for this one:"))
    }

    @Test
    fun `angles wrap around rather than running out on a large batch`() {
        // A count beyond the angle list must still produce an instruction, never an index crash.
        val many = (0 until 12).map { message(it, 12) }
        many.forEach { assertTrue(it.contains("Angle for this one:")) }
    }

    @Test
    fun `the user's own request always survives the added instruction`() {
        (0 until 6).forEach { assertTrue(message(it, 6).startsWith("une rivale au travail")) }
    }

    // --- Feedback from earlier siblings ---

    @Test
    fun `later proposals are told what has already been produced`() {
        val text = message(index = 2, count = 3, seen = listOf("Aria — mercenaire taciturne", "Bram — libraire anxieux"))
        assertTrue(text.contains("Aria — mercenaire taciturne"))
        assertTrue(text.contains("Bram — libraire anxieux"))
        assertTrue(text.contains("do not repeat"))
    }

    @Test
    fun `no phantom avoid-list when nothing has been produced yet`() {
        // An empty "already proposed" heading would be worse than none: it invites the model to treat
        // the absence as meaningful.
        assertFalse(message(index = 0, count = 4).contains("Already proposed"))
    }

    // --- Sampling ---

    @Test
    fun `generation decodes hotter than a single answer would`() {
        // 0.9 is a sensible temperature for one answer and the wrong one for a set that must differ.
        assertTrue(requireNotNull(useCase.samplingFor(0).temperature) > 0.9)
    }

    @Test
    fun `later proposals get more latitude than earlier ones`() {
        // They are the ones a model most tends to phone in.
        val first = requireNotNull(useCase.samplingFor(0).temperature)
        val fifth = requireNotNull(useCase.samplingFor(4).temperature)
        assertTrue(fifth > first)
    }

    @Test
    fun `temperature stays under the point where a JSON contract breaks`() {
        // This call must still return a parseable object, so the ceiling is lower than prose affords.
        (0 until 20).forEach {
            assertTrue("index $it", requireNotNull(useCase.samplingFor(it).temperature) <= 1.2)
        }
    }

    @Test
    fun `every proposal carries repetition control`() {
        assertNotNull(useCase.samplingFor(0).presencePenalty)
        assertNotNull(useCase.samplingFor(0).frequencyPenalty)
    }
}
