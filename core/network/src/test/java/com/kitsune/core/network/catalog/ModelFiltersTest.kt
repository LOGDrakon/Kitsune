package com.kitsune.core.network.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

private fun model(id: String, context: Int, inputCost: Double, outputCost: Double) = ModelInfo(
    id = id,
    provider = "test",
    maxInputTokens = context,
    maxOutputTokens = context,
    inputCostPerMillionTokens = inputCost,
    outputCostPerMillionTokens = outputCost
)

class ModelFiltersTest {

    private val catalog = listOf(
        model("cheap-small-context", context = 8_000, inputCost = 0.1, outputCost = 0.2),
        model("cheap-large-context", context = 200_000, inputCost = 0.2, outputCost = 0.3),
        model("expensive-large-context", context = 200_000, inputCost = 10.0, outputCost = 20.0),
        model("expensive-small-context", context = 8_000, inputCost = 15.0, outputCost = 25.0)
    )

    @Test
    fun `combining context and cost filters narrows to models matching both simultaneously`() {
        val result = catalog.withMinContext(128_000).withMaxCostPerMillionTokens(5.0)

        assertEquals(listOf("cheap-large-context"), result.map { it.id })
    }

    @Test
    fun `context filter alone keeps every model above the threshold regardless of cost`() {
        val result = catalog.withMinContext(128_000)

        assertEquals(setOf("cheap-large-context", "expensive-large-context"), result.map { it.id }.toSet())
    }

    @Test
    fun `cost filter alone keeps every model under the threshold regardless of context`() {
        val result = catalog.withMaxCostPerMillionTokens(1.0)

        assertEquals(setOf("cheap-small-context", "cheap-large-context"), result.map { it.id }.toSet())
    }

    @Test
    fun `combined filters can exclude every model when no candidate satisfies both`() {
        val result = catalog.withMinContext(128_000).withMaxCostPerMillionTokens(0.1)

        assertEquals(emptyList<ModelInfo>(), result)
    }
}
