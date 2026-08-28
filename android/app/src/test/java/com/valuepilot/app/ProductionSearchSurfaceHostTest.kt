package com.valuepilot.app

import com.valuepilot.core.ProductionBestValueBlockedPresentationItem
import com.valuepilot.core.ProductionBestValuePresentationSnapshot
import com.valuepilot.core.ProductionCurrentPriceEligibilityBlocker
import com.valuepilot.core.ProductionUnitValueEligibilityBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionSearchSurfaceHostTest {

    @Test
    fun `renderer contract exposes only ui ready state`() {
        val renderMethod =
            ProductionSearchSurfaceRenderer::class.java.methods
                .single { method -> method.name == "render" }

        assertEquals(listOf(ProductionSearchUiState::class.java), renderMethod.parameterTypes.toList())
        assertEquals(Void.TYPE, renderMethod.returnType)
    }

    @Test
    fun `applied snapshot renders ui state while stale duplicate and conflicts do not rerender`() {
        val rendered = mutableListOf<ProductionSearchUiState?>()
        val host = ProductionSearchSurfaceHost { state -> rendered += state }

        assertEquals(
            ProductionSearchRefreshDisposition.APPLIED,
            host.applySnapshot(4L, snapshot("first", 1_000L))
        )
        assertEquals(1, rendered.size)
        assertEquals("first", rendered.single()?.blocked?.single()?.candidateId)

        assertEquals(
            ProductionSearchRefreshDisposition.STALE,
            host.applySnapshot(3L, snapshot("stale", 900L))
        )
        assertEquals(1, rendered.size)

        assertEquals(
            ProductionSearchRefreshDisposition.DUPLICATE,
            host.applySnapshot(4L, snapshot("first", 1_000L))
        )
        assertEquals(1, rendered.size)

        assertEquals(
            ProductionSearchRefreshDisposition.GENERATION_CONFLICT,
            host.applySnapshot(4L, snapshot("conflict", 1_000L))
        )
        assertEquals(1, rendered.size)

        assertEquals(
            ProductionSearchRefreshDisposition.APPLIED,
            host.applySnapshot(5L, snapshot("newer", 2_000L))
        )
        assertEquals(2, rendered.size)
        assertEquals("newer", rendered.last()?.blocked?.single()?.candidateId)
    }

    @Test
    fun `newer clear renders null and stale result cannot repopulate`() {
        val rendered = mutableListOf<ProductionSearchUiState?>()
        val host = ProductionSearchSurfaceHost { state -> rendered += state }

        assertEquals(
            ProductionSearchRefreshDisposition.APPLIED,
            host.applySnapshot(10L, snapshot("visible"))
        )
        assertEquals(
            ProductionSearchRefreshDisposition.APPLIED,
            host.clear(11L)
        )

        assertEquals(2, rendered.size)
        assertNull(rendered.last())

        assertEquals(
            ProductionSearchRefreshDisposition.STALE,
            host.applySnapshot(10L, snapshot("late"))
        )
        assertEquals(2, rendered.size)
        assertNull(rendered.last())
    }

    @Test
    fun `renderer failure does not consume the incoming generation`() {
        var shouldFail = true
        var renderedState: ProductionSearchUiState? = null
        val host =
            ProductionSearchSurfaceHost { state ->
                if (shouldFail) error("synthetic renderer failure")
                renderedState = state
            }

        val source = snapshot("retryable")
        var thrown: IllegalStateException? = null
        try {
            host.applySnapshot(7L, source)
        } catch (error: IllegalStateException) {
            thrown = error
        }
        assertTrue(thrown != null)

        shouldFail = false
        assertEquals(
            ProductionSearchRefreshDisposition.APPLIED,
            host.applySnapshot(7L, source)
        )
        assertEquals("retryable", renderedState?.blocked?.single()?.candidateId)
    }

    private fun snapshot(
        candidateId: String,
        evaluatedAt: Long = 1_000L
    ): ProductionBestValuePresentationSnapshot =
        ProductionBestValuePresentationSnapshot(
            evaluatedAtEpochMillis = evaluatedAt,
            groups = emptyList(),
            blockedItems =
                listOf(
                    ProductionBestValueBlockedPresentationItem(
                        candidateId = candidateId,
                        unitValueBlockers =
                            setOf(ProductionUnitValueEligibilityBlocker.PRICE_STAGE_BLOCKED),
                        priceBlockers =
                            setOf(
                                ProductionCurrentPriceEligibilityBlocker.CANDIDATE_NOT_ACCEPTANCE_RANKABLE
                            ),
                        unitValuePolicyBlockReasons = emptySet()
                    )
                )
        )
}
