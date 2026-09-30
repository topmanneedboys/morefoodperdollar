package com.valuepilot.app

import com.valuepilot.core.ProductStoreMapResultStatus
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaProductStoreMapTest {
    @Test
    fun adapterKeepsOnlyTheSelectedExactProductAndStableStoreIdentity() {
        val selected = match("product-yerba", "4006381333931")
        val response = response(
            plans = listOf(
                plan(line("product-yerba", "offer-a", "store-a"), store("store-a")),
                plan(line("other-product", "offer-b", "store-b"), store("store-b")),
                plan(line("product-yerba", "offer-a", "store-a"), store("store-a"))
            )
        )

        val result = ArgentinaProductStoreMapAdapter.query(
            response,
            selected,
            origin = com.valuepilot.core.ProductStoreMapCoordinate(-346055060, -584425620)
        )

        assertEquals(ProductStoreMapResultStatus.READY, result.status)
        assertEquals(listOf("store-a"), result.pins.map { it.storeKey.value })
        assertEquals("offer-a", result.pins.single().relationshipKey)
        assertTrue(result.pins.single().approximateDistanceMetres!! >= 0L)
    }

    @Test
    fun explicitPackageQuantityIsUsedAndUnknownQuantityDoesNotGetInvented() {
        assertEquals("1" to "kg", ArgentinaProductStoreMapAdapter.explicitPackageQuantity("1 kg"))
        assertEquals("500" to "g", ArgentinaProductStoreMapAdapter.explicitPackageQuantity("500 g"))
        assertEquals(null, ArgentinaProductStoreMapAdapter.explicitPackageQuantity(null))
        assertEquals(null, ArgentinaProductStoreMapAdapter.explicitPackageQuantity("family size"))
    }

    @Test
    fun mapActionRequiresTheQualifiedArgentinaIdentityRail() {
        assertTrue(ArgentinaProductStoreMapAdapter.canQuery(match("product-yerba", "4006381333931")))
        assertEquals(
            false,
            ArgentinaProductStoreMapAdapter.canQuery(
                match("offline-display-name", null).copy(sourceLabel = null)
            )
        )
    }

    @Test
    fun coordinatesOutsideTheBroadArgentinaEnvelopeAreExcluded() {
        val selected = match("product-yerba", "4006381333931")
        val response = response(
            plans = listOf(
                plan(
                    line("product-yerba", "offer-invalid", "store-invalid"),
                    store("store-invalid").copy(latitude = "0", longitude = "0")
                )
            )
        )
        val result = ArgentinaProductStoreMapAdapter.query(response, selected, null)
        assertEquals(ProductStoreMapResultStatus.NO_VALID_COORDINATES, result.status)
        assertEquals(1, result.excludedCoordinateCount)
    }

    private fun match(key: String, gtin: String?) =
        PracticalShoppingSearchIdentityPresentation.Match(
            displayName = "Yerba Mate",
            brand = "Mate",
            matchLabel = "exact barcode",
            packageQuantityLabel = "1 kg",
            sourceLabel = "Argentina SEPA / Precios Claros",
            identityKey = key,
            gtin = gtin
        )

    private fun response(plans: List<ArgentinaBackendContract.Plan>): ArgentinaBackendContract.ShopResponse {
        val selected = plans.first()
        val decision = ArgentinaBackendContract.ShoppingDecision(
            schemaVersion = ArgentinaBackendContract.SHOPPING_SCHEMA_VERSION,
            request = ArgentinaBackendContract.ShoppingRequest(
                latitude = "-34.6",
                longitude = "-58.44",
                radiusKm = "25",
                items = listOf(
                    ArgentinaBackendContract.RequestItem(
                        lineId = "line-1",
                        query = "Yerba Mate",
                        amount = "1",
                        unit = "kg",
                        productEvidenceKey = "product-yerba"
                    )
                )
            ),
            cheapestSingleStore = selected,
            closestCompletePriceEvidenceStore = selected,
            cheapestTwoStoreCombination = null,
            cheapestUpToTwoStores = selected,
            cheapestPerLineUnboundedStores = selected,
            decisionFrontier = plans,
            bestSensibleChoice = ArgentinaBackendContract.BestSensibleChoice(
                status = "BEST_SENSIBLE_CHOICE_UNDER_POLICY",
                selectedPlan = selected
            ),
            diagnostics = emptyMap(),
            safety = mapOf(
                "availability" to "UNKNOWN",
                "distance" to "STRAIGHT_LINE_HAVERSINE_ONLY",
                "routing" to "NOT_PROVIDED",
                "completePriceEvidenceIsNotInventory" to "true"
            )
        )
        return ArgentinaBackendContract.ShopResponse(
            common = ArgentinaBackendContract.CommonEnvelope(
                apiContractVersion = ArgentinaBackendContract.API_VERSION,
                requestId = "fixture",
                releaseId = "sepa-2026-09-06",
                releaseDate = "2026-09-06",
                freshness = ArgentinaBackendContract.Freshness.FRESH,
                evidenceSemantics = ArgentinaBackendContract.EvidenceSemantics(
                    availability = ArgentinaBackendContract.Availability.UNKNOWN,
                    distance = "STRAIGHT_LINE_HAVERSINE_ONLY",
                    promotions = null,
                    pricePublicationIsNotInventory = true
                ),
                diagnostics = emptyMap()
            ),
            regionsQueried = listOf("ar-caba"),
            result = decision
        )
    }

    private fun plan(
        line: ArgentinaBackendContract.PlanLine,
        store: ArgentinaBackendContract.Store
    ) = ArgentinaBackendContract.Plan(
        type = "SINGLE_STORE",
        planId = "plan-${store.storeKey}",
        storeCount = 1,
        storeKeys = listOf(store.storeKey),
        stores = listOf(store),
        lines = listOf(line),
        total = money("12.50"),
        maxStraightLineDistanceKm = "1.2",
        completePriceEvidence = true,
        missingLineIds = emptyList()
    )

    private fun line(product: String, offer: String, store: String) =
        ArgentinaBackendContract.PlanLine(
            lineId = "line-${offer}",
            productEvidenceKey = product,
            productName = "Yerba Mate",
            brand = "Mate",
            offerKey = offer,
            storeKey = store,
            storeName = store,
            distanceKm = "1.2",
            requestedQuantity = quantity(),
            packageQuantity = quantity(),
            packageCount = 1,
            suppliedQuantity = quantity(),
            excessQuantity = quantity(allowZero = true),
            lineTotal = money("12.50"),
            packagePrice = money("12.50"),
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

    private fun store(key: String) = ArgentinaBackendContract.Store(
        storeKey = key,
        storeName = key,
        distanceKm = "1.2",
        latitude = "-34.601",
        longitude = "-58.381",
        address = mapOf("city" to "Buenos Aires", "street" to "Calle 1")
    )

    private fun quantity(allowZero: Boolean = false) = ArgentinaBackendContract.Quantity(
        dimension = "COUNT",
        amount = if (allowZero) "0" else "1",
        unit = "count",
        baseAmount = if (allowZero) "0" else "1",
        baseUnit = "count",
        allowZero = allowZero
    )

    private fun money(amount: String) = ArgentinaBackendContract.Money(amount, "ARS", BigDecimal(amount))
}
