package com.kitsune.core.network.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

private fun model(
    id: String,
    inputCost: Double? = 1.0,
    outputCost: Double? = 1.0,
    context: Int? = 100_000,
    quality: QualityTier = QualityTier.UNKNOWN,
    speed: SpeedTier = SpeedTier.UNKNOWN
) = ModelInfo(
    id = id,
    provider = "test",
    maxInputTokens = context,
    maxOutputTokens = context,
    inputCostPerMillionTokens = inputCost,
    outputCostPerMillionTokens = outputCost,
    speedTier = speed,
    qualityTier = quality
)

class UsageProfileTest {

    @Test
    fun `ECONOMICAL never picks an embeddings model even though it is the cheapest`() {
        val catalog = listOf(
            model("text-embedding-3-small", inputCost = 0.01, outputCost = 0.01),
            model("gpt-4.1", inputCost = 2.0, outputCost = 8.0),
            model("gpt-4o", inputCost = 2.5, outputCost = 10.0)
        )

        val picked = catalog.pickForProfile(UsageProfile.ECONOMICAL)

        assertEquals("gpt-4.1", picked?.id)
    }

    @Test
    fun `isChatCapable excludes embedding model ids`() {
        assert(!model("text-embedding-3-small").isChatCapable)
        assert(!model("text-embedding-3-large").isChatCapable)
        assert(model("gpt-4.1").isChatCapable)
    }

    @Test
    fun `pickForProfile returns null when catalog only has non-chat models`() {
        val catalog = listOf(model("text-embedding-3-small"), model("text-embedding-3-large"))

        assertEquals(null, catalog.pickForProfile(UsageProfile.ECONOMICAL))
    }

    @Test
    fun `QUALITY prefers a premium-tagged model over cost`() {
        val catalog = listOf(
            model("text-embedding-3-small", inputCost = 0.001, outputCost = 0.001),
            model("gpt-4o", inputCost = 2.5, outputCost = 10.0),
            model("gpt-5.1-chat", inputCost = 1.0, outputCost = 5.0, quality = QualityTier.PREMIUM)
        )

        val picked = catalog.pickForProfile(UsageProfile.QUALITY)

        assertNotNull(picked)
        assertEquals("gpt-5.1-chat", picked?.id)
    }
}
