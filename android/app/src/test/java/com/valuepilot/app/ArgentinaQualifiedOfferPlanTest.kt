package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ArgentinaQualifiedOfferPlanTest {
    @Test
    fun requestPreservesExactIdentityAndStructuredQuantity() {
        val match = match(quantity = quantity("500", "g"))
        val request = ArgentinaQualifiedOfferPlan.request(
            match,
            ArgentinaQualifiedLaunchAreas.BuenosAiresCaba
        )

        val item = requireNotNull(request).items.single()
        assertEquals("Yerba Mate", item.query)
        assertEquals("500", item.amount)
        assertEquals("g", item.unit)
        assertEquals("product-a", item.productEvidenceKey)
    }

    @Test
    fun unknownQuantityDoesNotCreateAPlannerRequest() {
        assertNull(
            ArgentinaQualifiedOfferPlan.request(
                match(quantity = null),
                ArgentinaQualifiedLaunchAreas.BuenosAiresCaba
            )
        )
    }

    @Test
    fun presentationUsesCompleteSingleStoreFallbackWhenPolicyIsUnresolved() {
        val fallback = plan(complete = true, missing = emptyList())
        val decision = decision(
            selected = null,
            cheapestSingleStore = fallback
        )

        assertSame(fallback, ArgentinaQualifiedOfferPlan.presentationPlan(decision))
    }

    @Test
    fun presentationRejectsIncompleteSingleStoreFallback() {
        val incomplete = plan(complete = false, missing = listOf("line-1"))
        val decision = decision(
            selected = null,
            cheapestSingleStore = incomplete
        )

        assertNull(ArgentinaQualifiedOfferPlan.presentationPlan(decision))
    }

    private fun match(quantity: ArgentinaBackendContract.Quantity?) =
        PracticalShoppingSearchIdentityPresentation.Match(
            displayName = "Yerba Mate",
            brand = "Mate",
            matchLabel = "exact barcode",
            packageQuantityLabel = quantity?.let { "${it.amount} ${it.unit}" },
            sourceLabel = "Argentina SEPA / Precios Claros",
            identityKey = "product-a",
            qualifiedOffers = listOf(
                ArgentinaBackendContract.SearchOffer(
                    offerKey = "offer-a",
                    productEvidenceKey = "product-a",
                    storeKey = "store-a",
                    storeName = "Store A",
                    storeAddress = emptyMap(),
                    storeLatitude = "-34.6",
                    storeLongitude = "-58.4",
                    distanceKm = "1.2",
                    listPrice = ArgentinaBackendContract.Money("12.50", "ARS", java.math.BigDecimal("12.50")),
                    referencePrice = null,
                    quantity = quantity,
                    quantityStatus = if (quantity == null) "UNKNOWN" else "KNOWN",
                    unitPrice = null,
                    unitPriceBasis = null,
                    releaseDate = "2026-09-06",
                    freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
                    availability = ArgentinaBackendContract.Availability.UNKNOWN,
                    provenance = ArgentinaBackendContract.Provenance(
                        provider = "ARGENTINA_SEPA_PRECIOS_CLAROS",
                        releaseDate = "2026-09-06",
                        freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
                        source = "Precios Claros"
                    )
                )
            )
        )

    private fun quantity(amount: String, unit: String) = ArgentinaBackendContract.Quantity(
        dimension = "MASS",
        amount = amount,
        unit = unit,
        baseAmount = amount,
        baseUnit = unit
    )

    private fun decision(
        selected: ArgentinaBackendContract.Plan?,
        cheapestSingleStore: ArgentinaBackendContract.Plan?
    ) = ArgentinaBackendContract.ShoppingDecision(
        schemaVersion = ArgentinaBackendContract.SHOPPING_SCHEMA_VERSION,
        request = ArgentinaBackendContract.ShoppingRequest(
            latitude = "-34.605506",
            longitude = "-58.442562",
            radiusKm = "25",
            items = listOf(
                ArgentinaBackendContract.RequestItem(
                    lineId = "line-1",
                    query = "Yerba Mate",
                    amount = "1",
                    unit = "count",
                    productEvidenceKey = "product-a"
                )
            )
        ),
        cheapestSingleStore = cheapestSingleStore,
        closestCompletePriceEvidenceStore = cheapestSingleStore,
        cheapestTwoStoreCombination = null,
        cheapestUpToTwoStores = cheapestSingleStore,
        cheapestPerLineUnboundedStores = null,
        decisionFrontier = listOfNotNull(cheapestSingleStore),
        bestSensibleChoice = ArgentinaBackendContract.BestSensibleChoice(
            status = if (selected == null) "UNRESOLVED" else "SELECTED",
            selectedPlan = selected
        ),
        diagnostics = emptyMap(),
        safety = mapOf("availability" to "UNKNOWN")
    )

    private fun plan(complete: Boolean, missing: List<String>) =
        ArgentinaBackendContract.Plan(
            type = "SINGLE_STORE",
            planId = "plan-1",
            storeCount = 1,
            storeKeys = listOf("store-a"),
            stores = listOf(
                ArgentinaBackendContract.Store(
                    storeKey = "store-a",
                    storeName = "Store A",
                    distanceKm = "1.2",
                    latitude = "-34.6",
                    longitude = "-58.4",
                    address = emptyMap()
                )
            ),
            lines = listOf(
                ArgentinaBackendContract.PlanLine(
                    lineId = "line-1",
                    productEvidenceKey = "product-a",
                    productName = "Yerba Mate",
                    brand = "Mate",
                    offerKey = "offer-a",
                    storeKey = "store-a",
                    storeName = "Store A",
                    distanceKm = "1.2",
                    requestedQuantity = quantity("1", "count").copy(
                        dimension = "COUNT",
                        baseAmount = "1",
                        baseUnit = "count"
                    ),
                    packageQuantity = quantity("1", "count").copy(
                        dimension = "COUNT",
                        baseAmount = "1",
                        baseUnit = "count"
                    ),
                    packageCount = 1,
                    suppliedQuantity = quantity("1", "count").copy(
                        dimension = "COUNT",
                        baseAmount = "1",
                        baseUnit = "count"
                    ),
                    excessQuantity = quantity("0", "count").copy(
                        dimension = "COUNT",
                        baseAmount = "0",
                        baseUnit = "count"
                    ),
                    lineTotal = ArgentinaBackendContract.Money("12.50", "ARS", java.math.BigDecimal("12.50")),
                    packagePrice = ArgentinaBackendContract.Money("12.50", "ARS", java.math.BigDecimal("12.50")),
                    availability = ArgentinaBackendContract.Availability.UNKNOWN,
                    freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
                    promotions = emptyList(),
                    provenance = ArgentinaBackendContract.Provenance(
                        provider = "ARGENTINA_SEPA_PRECIOS_CLAROS",
                        releaseDate = "2026-09-06",
                        freshnessStatus = ArgentinaBackendContract.Freshness.FRESH,
                        source = "Precios Claros"
                    )
                )
            ),
            total = ArgentinaBackendContract.Money("12.50", "ARS", java.math.BigDecimal("12.50")),
            maxStraightLineDistanceKm = "1.2",
            completePriceEvidence = complete,
            missingLineIds = missing
        )
}
