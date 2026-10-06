package com.valuepilot.app

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaPrivateBasketPlanTest {
    @Test
    fun requestBuilderCarriesExactKeysOnceAndLeavesUnknownQuantityOut() {
        val first = identity("key-1", "Yerba")
        val second = identity("key-2", "Rice")
        val offers = mapOf(
            "key-1" to listOf(offer("key-1", "offer-1", "10.00")),
            "key-2" to listOf(offer("key-2", "offer-2", "20.00").copy(quantity = null))
        )

        val built = ArgentinaPrivateBasketRequestBuilder.build(
            identities = listOf(first, second, first),
            offersByIdentity = offers,
            area = ArgentinaQualifiedLaunchAreas.BuenosAiresCaba
        )

        assertEquals(listOf("key-1"), built.includedIdentityKeys)
        assertEquals(listOf("key-2"), built.unresolvedIdentityKeys)
        val request = built.request ?: error("expected one bounded request")
        assertEquals(1, request.items.size)
        assertEquals("key-1", request.items.single().productEvidenceKey)
        assertEquals("Yerba", request.items.single().query)
    }

    @Test
    fun requestBuilderIsStableForSameIdentitiesAndEvidence() {
        val identities = listOf(identity("key-2", "Rice"), identity("key-1", "Yerba"))
        val offers = mapOf(
            "key-1" to listOf(offer("key-1", "offer-1", "10.00")),
            "key-2" to listOf(offer("key-2", "offer-2", "20.00"))
        )
        val one = ArgentinaPrivateBasketRequestBuilder.build(
            identities, offers, ArgentinaQualifiedLaunchAreas.BuenosAiresCaba
        )
        val two = ArgentinaPrivateBasketRequestBuilder.build(
            identities, offers, ArgentinaQualifiedLaunchAreas.BuenosAiresCaba
        )
        assertEquals(one, two)
        assertEquals(one.request!!.items.map { it.lineId }, two.request!!.items.map { it.lineId })
    }

    @Test
    fun projectorKeepsPartialCoverageAndUnknownAvailabilityExplicit() {
        val identities = listOf(identity("key-1", "Yerba"), identity("key-2", "Rice"))
        val response = response(
            plan = plan(
                lines = listOf(planLine("key-1", "Yerba")),
                missing = listOf("line-2"),
                complete = false
            )
        )

        val projection = ArgentinaPrivateBasketPlanProjector.project(response, identities)

        assertEquals(ArgentinaPrivateBasketPlanStatus.CURRENT_PARTIAL, projection.status)
        assertEquals("Known subtotal · ARS 10.00", projection.totalText)
        assertEquals(listOf("Rice"), projection.missingIdentityNames)
        assertEquals("Availability unknown", projection.lines.first().availabilityText)
        assertTrue(projection.notice.contains("availability remains unknown"))
    }

    @Test
    fun projectorReportsNoPlanWithoutInventingTotals() {
        val identities = listOf(identity("key-1", "Yerba"))
        val projection = ArgentinaPrivateBasketPlanProjector.project(
            response(plan = null),
            identities
        )
        assertEquals(ArgentinaPrivateBasketPlanStatus.NO_CURRENT_PLAN, projection.status)
        assertNull(projection.totalText)
        assertEquals(listOf("Yerba"), projection.missingIdentityNames)
    }

    private fun identity(key: String, name: String) =
        PracticalShoppingPrivateIdentity(
            identityKey = key,
            displayName = name,
            sourceLabel = "Argentina SEPA / Precios Claros",
            packageQuantityLabel = "1 kg"
        )

    private fun offer(key: String, offerKey: String, amount: String) =
        ArgentinaBackendContract.SearchOffer(
            offerKey = offerKey,
            productEvidenceKey = key,
            storeKey = "store-1",
            storeName = "Mercado Central",
            storeAddress = mapOf("city" to "Buenos Aires"),
            storeLatitude = "-34.601",
            storeLongitude = "-58.381",
            distanceKm = "1.2",
            listPrice = money(amount),
            referencePrice = null,
            quantity = quantity(),
            quantityStatus = "KNOWN",
            unitPrice = null,
            unitPriceBasis = null,
            releaseDate = RELEASE_DATE,
            freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
            availability = ArgentinaBackendContract.Availability.UNKNOWN,
            provenance = provenance()
        )

    private fun response(plan: ArgentinaBackendContract.Plan?) =
        ArgentinaBackendContract.ShopResponse(
            common = common(),
            regionsQueried = listOf("ar-caba"),
            result = ArgentinaBackendContract.ShoppingDecision(
                schemaVersion = ArgentinaBackendContract.SHOPPING_SCHEMA_VERSION,
                request = ArgentinaBackendContract.ShoppingRequest(
                    latitude = "-34.605506",
                    longitude = "-58.442562",
                    radiusKm = "25",
                    items = listOf(
                        ArgentinaBackendContract.RequestItem("line-1", "Yerba", "1", "kg", "key-1"),
                        ArgentinaBackendContract.RequestItem("line-2", "Rice", "1", "kg", "key-2")
                    )
                ),
                cheapestSingleStore = plan,
                closestCompletePriceEvidenceStore = null,
                cheapestTwoStoreCombination = null,
                cheapestUpToTwoStores = plan,
                cheapestPerLineUnboundedStores = null,
                decisionFrontier = emptyList(),
                bestSensibleChoice = ArgentinaBackendContract.BestSensibleChoice(
                    status = if (plan == null) "NO_PLAN" else "PARTIAL",
                    selectedPlan = plan
                ),
                diagnostics = emptyMap(),
                safety = mapOf("availability" to "UNKNOWN")
            )
        )

    private fun plan(lines: List<ArgentinaBackendContract.PlanLine>, missing: List<String>, complete: Boolean) =
        ArgentinaBackendContract.Plan(
            type = "CHEAPEST_UP_TO_TWO_STORES",
            planId = "plan-1",
            storeCount = 1,
            storeKeys = listOf("store-1"),
            stores = listOf(
                ArgentinaBackendContract.Store(
                    storeKey = "store-1",
                    storeName = "Mercado Central",
                    distanceKm = "1.2",
                    latitude = "-34.601",
                    longitude = "-58.381",
                    address = mapOf("city" to "Buenos Aires")
                )
            ),
            lines = lines,
            total = money("10.00"),
            maxStraightLineDistanceKm = "1.2",
            completePriceEvidence = complete,
            missingLineIds = missing
        )

    private fun planLine(key: String, name: String) =
        ArgentinaBackendContract.PlanLine(
            lineId = if (key == "key-1") "line-1" else "line-2",
            productEvidenceKey = key,
            productName = name,
            brand = null,
            offerKey = "offer-$key",
            storeKey = "store-1",
            storeName = "Mercado Central",
            distanceKm = "1.2",
            requestedQuantity = quantity(),
            packageQuantity = quantity(),
            packageCount = 1,
            suppliedQuantity = quantity(),
            excessQuantity = quantity(allowZero = true),
            lineTotal = money("10.00"),
            packagePrice = money("10.00"),
            availability = ArgentinaBackendContract.Availability.UNKNOWN,
            freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
            promotions = emptyList(),
            provenance = provenance()
        )

    private fun common() = ArgentinaBackendContract.CommonEnvelope(
        apiContractVersion = ArgentinaBackendContract.API_VERSION,
        requestId = "request-1",
        releaseId = "release-1",
        releaseDate = RELEASE_DATE,
        freshness = ArgentinaBackendContract.Freshness.FRESH,
        evidenceSemantics = null,
        diagnostics = emptyMap()
    )

    private fun quantity(allowZero: Boolean = false) = ArgentinaBackendContract.Quantity(
        dimension = "MASS",
        amount = if (allowZero) "0" else "1",
        unit = "kg",
        baseAmount = if (allowZero) "0" else "1",
        baseUnit = "kg",
        allowZero = allowZero
    )

    private fun money(value: String) = ArgentinaBackendContract.Money(value, "ARS", BigDecimal(value))

    private fun provenance() = ArgentinaBackendContract.Provenance(
        provider = "ARGENTINA_SEPA_PRECIOS_CLAROS",
        releaseDate = RELEASE_DATE,
        freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
        source = "Precios Claros"
    )

    private companion object {
        const val RELEASE_DATE = "2026-09-06"
    }
}
