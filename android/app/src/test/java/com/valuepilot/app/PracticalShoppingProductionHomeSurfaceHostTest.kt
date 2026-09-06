package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.PracticalShoppingPolicy
import com.valuepilot.core.PracticalShoppingProductionOrchestrationRequest
import com.valuepilot.core.PracticalShoppingProductionPriceBinding
import com.valuepilot.core.PracticalShoppingProductionStoreScope
import com.valuepilot.core.ProductionDatasetDispositionRegistry
import com.valuepilot.core.ProductionDatasetLifecycleRegistry
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingRequest
import com.valuepilot.core.ShoppingStoreKey
import com.valuepilot.core.ShoppingTravel
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingProductionHomeSurfaceHostTest {

    private val fixture = StapleWatchProductionPriceTestFixture()
    private val policy =
        PracticalShoppingPolicy(
            minimumSecondStopSavings = Money.parse("15.00", "CAD"),
            maxAdditionalTravelSeconds = 600L,
            maxAdditionalDistanceMetres = 5_000L
        )

    @Test
    fun rendererContractExposesOnlyTheDemoFreeHomeUiState() {
        val renderMethod =
            PracticalShoppingProductionHomeRenderer::class.java.methods
                .single { method -> method.name == "render" }

        assertEquals(
            listOf(PracticalShoppingProductionHomeUiState::class.java),
            renderMethod.parameterTypes.toList()
        )
        assertEquals(Void.TYPE, renderMethod.returnType)
    }

    @Test
    fun hostDoesNotExposeDetachedOrchestrationResultApplyPath() {
        val publicMethods =
            PracticalShoppingProductionHomeSurfaceHost::class.java.declaredMethods
                .filter { method -> Modifier.isPublic(method.modifiers) }

        assertFalse(
            publicMethods.any { method ->
                method.parameterTypes.any { type ->
                    type.name ==
                        "com.valuepilot.core.PracticalShoppingProductionOrchestrationResult"
                }
            }
        )
        assertTrue(publicMethods.any { method -> method.name == "evaluateAndApply" })
    }

    @Test
    fun hostRerunsProductionEvaluationAndKeepsGenerationsOrdered() {
        val rendered = mutableListOf<PracticalShoppingProductionHomeUiState?>()
        val host = PracticalShoppingProductionHomeSurfaceHost { state -> rendered += state }
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()
        val request = request(ShoppingItemKey("host-coffee"), "host-store")
        val names = mapOf(ShoppingStoreKey("host-store") to "Neighbourhood Market")
        val itemNames = mapOf(ShoppingItemKey("host-coffee") to "Coffee")

        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.APPLIED,
            host.evaluateAndApply(
                generation = 4L,
                request = request,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                storeDisplayNames = names,
                itemDisplayNames = itemNames
            )
        )
        assertEquals(1, rendered.size)
        assertEquals(PracticalShoppingProductionHomeStatus.READY, rendered.single()?.status)
        assertEquals("Not enough price coverage yet", rendered.single()?.result?.headline)

        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.STALE,
            host.evaluateAndApply(
                generation = 3L,
                request = request(ShoppingItemKey("host-other"), "host-store"),
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                storeDisplayNames = names,
                itemDisplayNames = mapOf(ShoppingItemKey("host-other") to "Other")
            )
        )
        assertEquals(1, rendered.size)

        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.DUPLICATE,
            host.evaluateAndApply(
                generation = 4L,
                request = request,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                storeDisplayNames = names,
                itemDisplayNames = itemNames
            )
        )
        assertEquals(1, rendered.size)

        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.APPLIED,
            host.clear(5L)
        )
        assertEquals(2, rendered.size)
        assertNull(rendered.last())
    }

    @Test
    fun sameGenerationWithDifferentProjectedContentIsRejectedAsConflict() {
        val price =
            fixture.case(
                requestId = "host-conflict-price",
                providerItemId = "host-milk",
                merchantKey = "merchant-host-conflict",
                locationKey = "location-host-conflict",
                commerceChannelKey = "IN_STORE",
                priceMinor = 599L
            )
        val item = ShoppingItemKey("host-milk")
        val storeKey = ShoppingStoreKey("host-conflict-store")
        val store =
            PracticalShoppingProductionStoreScope(
                storeKey = storeKey,
                merchantKey = "merchant-host-conflict",
                locationKey = "location-host-conflict",
                commerceChannelKey = "IN_STORE",
                travelFromUser = ShoppingTravel(1_200L, 300L)
            )
        val request =
            PracticalShoppingProductionOrchestrationRequest(
                shoppingRequest = ShoppingRequest(listOf(item)),
                stores = listOf(store),
                storePairs = emptyList(),
                priceBindings =
                    listOf(
                        PracticalShoppingProductionPriceBinding(
                            itemKey = item,
                            productKey = price.productKey,
                            storeKey = storeKey,
                            currentPriceRequestId = price.request.requestId
                        )
                    ),
                priceRequests = listOf(price.request),
                evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
                acceptancePolicy = fixture.acceptancePolicy,
                planningPolicy = policy
            )
        val registries = fixture.registries(listOf(price))
        val rendered = mutableListOf<PracticalShoppingProductionHomeUiState?>()
        val host = PracticalShoppingProductionHomeSurfaceHost { state -> rendered += state }

        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.APPLIED,
            host.evaluateAndApply(
                generation = 8L,
                request = request,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                storeDisplayNames = mapOf(storeKey to "First Market"),
                itemDisplayNames = mapOf(item to "Milk")
            )
        )
        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.GENERATION_CONFLICT,
            host.evaluateAndApply(
                generation = 8L,
                request = request,
                lifecycleRegistry = registries.lifecycle,
                dispositionRegistry = registries.disposition,
                storeDisplayNames = mapOf(storeKey to "Different Market"),
                itemDisplayNames = mapOf(item to "Milk")
            )
        )
        assertEquals(1, rendered.size)
        assertEquals("First Market", rendered.single()?.result?.primary?.storeName)
    }

    @Test
    fun sameGenerationWithDifferentNoCoverageRequestIsRejectedAsConflict() {
        val item = ShoppingItemKey("host-no-coverage-item")
        val firstRequest = request(item, "host-no-coverage-first-store")
        val secondRequest = request(item, "host-no-coverage-second-store")
        val rendered = mutableListOf<PracticalShoppingProductionHomeUiState?>()
        val host = PracticalShoppingProductionHomeSurfaceHost { state -> rendered += state }
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()
        val itemNames = mapOf(item to "Same item")

        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.APPLIED,
            host.evaluateAndApply(
                generation = 9L,
                request = firstRequest,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                storeDisplayNames =
                    mapOf(
                        ShoppingStoreKey("host-no-coverage-first-store") to "Same market"
                    ),
                itemDisplayNames = itemNames
            )
        )
        assertEquals("Not enough price coverage yet", rendered.single()?.result?.headline)

        // Both requests intentionally produce the same no-coverage projection.
        // The declared store scope is still part of the request identity, so a
        // same-generation replacement must fail closed instead of being treated
        // as an idempotent replay.
        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.GENERATION_CONFLICT,
            host.evaluateAndApply(
                generation = 9L,
                request = secondRequest,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                storeDisplayNames =
                    mapOf(
                        ShoppingStoreKey("host-no-coverage-second-store") to "Same market"
                    ),
                itemDisplayNames = itemNames
            )
        )
        assertEquals(1, rendered.size)
        assertEquals(
            ShoppingItemKey("host-no-coverage-item"),
            rendered.single()?.items?.single()?.itemKey
        )
    }

    @Test
    fun sameGenerationWithDifferentNoCoverageDisplayStateIsRejectedAsConflict() {
        val item = ShoppingItemKey("host-no-coverage-label-item")
        val request = request(item, "host-no-coverage-label-store")
        val rendered = mutableListOf<PracticalShoppingProductionHomeUiState?>()
        val host = PracticalShoppingProductionHomeSurfaceHost { state -> rendered += state }
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()
        val storeNames =
            mapOf(ShoppingStoreKey("host-no-coverage-label-store") to "Same market")

        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.APPLIED,
            host.evaluateAndApply(
                generation = 10L,
                request = request,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                storeDisplayNames = storeNames,
                itemDisplayNames = mapOf(item to "First label")
            )
        )

        // The production projection does not contain a missing item's display
        // label, but the renderer state does. A changed label at the same
        // generation must not be silently dropped as a duplicate.
        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.GENERATION_CONFLICT,
            host.evaluateAndApply(
                generation = 10L,
                request = request,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                storeDisplayNames = storeNames,
                itemDisplayNames = mapOf(item to "Second label")
            )
        )
        assertEquals(1, rendered.size)
        assertEquals("First label", rendered.single()?.items?.single()?.name)
    }

    @Test
    fun rendererFailureDoesNotConsumeTheGeneration() {
        var shouldFail = true
        var rendered: PracticalShoppingProductionHomeUiState? = null
        val host =
            PracticalShoppingProductionHomeSurfaceHost { state ->
                if (shouldFail) error("synthetic renderer failure")
                rendered = state
            }
        val lifecycleRegistry = ProductionDatasetLifecycleRegistry()
        val dispositionRegistry = ProductionDatasetDispositionRegistry()
        val request = request(ShoppingItemKey("host-retry"), "host-retry-store")
        val names = mapOf(ShoppingStoreKey("host-retry-store") to "Retry Market")
        val itemNames = mapOf(ShoppingItemKey("host-retry") to "Retry item")

        var thrown: IllegalStateException? = null
        try {
            host.evaluateAndApply(
                generation = 7L,
                request = request,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                storeDisplayNames = names,
                itemDisplayNames = itemNames
            )
        } catch (error: IllegalStateException) {
            thrown = error
        }
        assertTrue(thrown != null)

        shouldFail = false
        assertEquals(
            PracticalShoppingProductionHomeRefreshDisposition.APPLIED,
            host.evaluateAndApply(
                generation = 7L,
                request = request,
                lifecycleRegistry = lifecycleRegistry,
                dispositionRegistry = dispositionRegistry,
                storeDisplayNames = names,
                itemDisplayNames = itemNames
            )
        )
        assertEquals(PracticalShoppingProductionHomeStatus.READY, rendered?.status)
    }

    private fun request(
        item: ShoppingItemKey,
        storeKey: String
    ): PracticalShoppingProductionOrchestrationRequest {
        val store =
            PracticalShoppingProductionStoreScope(
                storeKey = ShoppingStoreKey(storeKey),
                merchantKey = "merchant-$storeKey",
                locationKey = "location-$storeKey",
                commerceChannelKey = "IN_STORE",
                travelFromUser = ShoppingTravel(1_200L, 300L)
            )
        return PracticalShoppingProductionOrchestrationRequest(
            shoppingRequest = ShoppingRequest(listOf(item)),
            stores = listOf(store),
            storePairs = emptyList(),
            priceBindings = emptyList(),
            priceRequests = emptyList(),
            evaluatedAtEpochMillis = fixture.evaluatedAtEpochMillis,
            acceptancePolicy = fixture.acceptancePolicy,
            planningPolicy = policy
        )
    }
}
