package com.valuepilot.app

import com.valuepilot.core.EvidenceAcceptancePolicy
import com.valuepilot.core.EvidenceFreshnessPolicy
import com.valuepilot.core.ProductionBestValuePresentationSnapshot
import com.valuepilot.core.ProductionDatasetDispositionRegistry
import com.valuepilot.core.ProductionDatasetLifecycleRegistry
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionSearchSurfaceHostTest {

    private val acceptancePolicy =
        EvidenceAcceptancePolicy(
            EvidenceFreshnessPolicy(
                freshForMillis = 2_000L,
                staleAfterMillis = 5_000L,
                futureToleranceMillis = 100L
            )
        )

    @Test
    fun `renderer contract exposes only ui ready state`() {
        val renderMethod =
            ProductionSearchSurfaceRenderer::class.java.methods
                .single { method -> method.name == "render" }

        assertEquals(listOf(ProductionSearchUiState::class.java), renderMethod.parameterTypes.toList())
        assertEquals(Void.TYPE, renderMethod.returnType)
    }

    @Test
    fun `host exposes no public detached presentation snapshot apply path`() {
        val publicMethods =
            ProductionSearchSurfaceHost::class.java.declaredMethods
                .filter { method -> Modifier.isPublic(method.modifiers) }

        assertFalse(
            publicMethods.any { method ->
                method.parameterTypes.any { type ->
                    type == ProductionBestValuePresentationSnapshot::class.java
                }
            }
        )
        assertTrue(publicMethods.any { method -> method.name == "evaluateAndApply" })
    }

    @Test
    fun `raw evaluation is re-run through shared core and obeys generation ordering`() {
        val rendered = mutableListOf<ProductionSearchUiState?>()
        val host = ProductionSearchSurfaceHost { state -> rendered += state }
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()

        assertEquals(
            ProductionSearchRefreshDisposition.APPLIED,
            host.evaluateAndApply(
                generation = 4L,
                request = request(1_000L, lifecycleRegistry, dispositionRegistry)
            )
        )
        assertEquals(1, rendered.size)
        assertEquals(1_000L, rendered.single()?.evaluatedAtEpochMillis)
        assertTrue(rendered.single()?.groups?.isEmpty() == true)
        assertTrue(rendered.single()?.blocked?.isEmpty() == true)

        assertEquals(
            ProductionSearchRefreshDisposition.STALE,
            host.evaluateAndApply(
                generation = 3L,
                request = request(2_000L, lifecycleRegistry, dispositionRegistry)
            )
        )
        assertEquals(1, rendered.size)

        assertEquals(
            ProductionSearchRefreshDisposition.DUPLICATE,
            host.evaluateAndApply(
                generation = 4L,
                request = request(1_000L, lifecycleRegistry, dispositionRegistry)
            )
        )
        assertEquals(1, rendered.size)

        assertEquals(
            ProductionSearchRefreshDisposition.APPLIED,
            host.evaluateAndApply(
                generation = 5L,
                request = request(2_000L, lifecycleRegistry, dispositionRegistry)
            )
        )
        assertEquals(2, rendered.size)
        assertEquals(2_000L, rendered.last()?.evaluatedAtEpochMillis)

        assertEquals(
            ProductionSearchRefreshDisposition.APPLIED,
            host.clear(6L)
        )
        assertEquals(3, rendered.size)
        assertNull(rendered.last())
    }

    @Test
    fun `renderer failure does not consume raw evaluation generation`() {
        var shouldFail = true
        var renderedState: ProductionSearchUiState? = null
        val host =
            ProductionSearchSurfaceHost { state ->
                if (shouldFail) error("synthetic renderer failure")
                renderedState = state
            }
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()
        val request = request(1_000L, lifecycleRegistry, dispositionRegistry)

        var thrown: IllegalStateException? = null
        try {
            host.evaluateAndApply(7L, request)
        } catch (error: IllegalStateException) {
            thrown = error
        }
        assertTrue(thrown != null)

        shouldFail = false
        assertEquals(
            ProductionSearchRefreshDisposition.APPLIED,
            host.evaluateAndApply(7L, request)
        )
        assertEquals(1_000L, renderedState?.evaluatedAtEpochMillis)
    }

    private fun request(
        evaluatedAtEpochMillis: Long,
        lifecycleRegistry: ProductionDatasetLifecycleRegistry,
        dispositionRegistry: ProductionDatasetDispositionRegistry
    ): ProductionSearchSurfaceEvaluationRequest =
        ProductionSearchSurfaceEvaluationRequest(
            priceRequests = emptyList(),
            candidates = emptyList(),
            lifecycleRegistry = lifecycleRegistry,
            dispositionRegistry = dispositionRegistry,
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
            acceptancePolicy = acceptancePolicy,
            quantityCandidates = emptyList()
        )
}
