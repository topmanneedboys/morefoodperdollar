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
        assertEquals(
            false,
            ArgentinaProductStoreMapAdapter.canQuery(
                match("product-unknown-quantity", null).copy(packageQuantityLabel = null)
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

    @Test
    fun attachedSearchOffersRenderWithoutASecondShopResponse() {
        val selected = match("product-yerba", "4006381333931")
        val result = ArgentinaProductStoreMapAdapter.query(
            offers = listOf(
                searchOffer("product-yerba", "search-offer", "store-search")
            ),
            selected = selected,
            origin = null
        )

        assertEquals(ProductStoreMapResultStatus.READY, result.status)
        assertEquals(listOf("store-search"), result.pins.map { it.storeKey.value })
        assertEquals("search-offer", result.pins.single().relationshipKey)
        assertEquals(true, result.pins.single().availabilityUnknown)
    }

    @Test
    fun attachedSearchOfferWithAnotherIdentityCannotLeakIntoTheSelectedMap() {
        val selected = match("product-yerba", "4006381333931")
        val result = ArgentinaProductStoreMapAdapter.query(
            offers = listOf(
                searchOffer("other-product", "other-offer", "store-other")
            ),
            selected = selected,
            origin = null
        )

        assertEquals(ProductStoreMapResultStatus.NO_EXACT_RELATIONSHIP, result.status)
        assertTrue(result.pins.isEmpty())
    }

    @Test
    fun searchOfferPreviewSurvivesTheInMemorySearchToBasketHandoff() {
        val selected = match("product-yerba", "4006381333931").copy(
            qualifiedOffers = listOf(searchOffer("product-yerba", "search-offer", "store-search"))
        )
        val identity = requireNotNull(PracticalShoppingPrivateIdentity.fromSearchMatch(selected))

        assertEquals(selected.identityKey, identity.identityKey)
        assertEquals(listOf("search-offer"), identity.qualifiedOffers.map { it.offerKey })
    }

    @Test
    fun basketPlanProjectionKeepsCompleteListedTotalExplicit() {
        val response = response(
            plans = listOf(
                plan(line("product-yerba", "offer-a", "store-a"), store("store-a"))
            )
        )
        val projection = ArgentinaBasketPlanProjector.project(
            response,
            listOf(
                PracticalShoppingPrivateIdentity(
                    identityKey = "product-yerba",
                    displayName = "Yerba Mate",
                    sourceLabel = "Argentina SEPA / Precios Claros",
                    packageQuantityLabel = "1 kg"
                )
            )
        )

        assertEquals(ArgentinaBasketPlanStatus.CURRENT_COMPLETE, projection.status)
        assertEquals("Listed total · ARS 12.50", projection.totalText)
        assertTrue(projection.notice.contains("availability remains unknown"))
        assertEquals(listOf("product-yerba"), projection.lines.map { it.productEvidenceKey })
        assertEquals("ARS 12.50/item", projection.lines.single().unitPriceText)
        assertTrue(projection.evaluatedPlanSummary?.contains("SINGLE_STORE") == true)
    }

    @Test
    fun basketPlanProjectionNeverTurnsMissingLinesIntoAZeroTotal() {
        val complete = plan(line("product-yerba", "offer-a", "store-a"), store("store-a"))
        val partial = complete.copy(
            completePriceEvidence = false,
            missingLineIds = listOf("line-missing")
        )
        val request = response(listOf(complete)).result.request.copy(
            items = listOf(
                ArgentinaBackendContract.RequestItem("line-offer-a", "Yerba Mate", "1", "kg", "product-yerba"),
                ArgentinaBackendContract.RequestItem("line-missing", "Tea", "1", "count", "tea")
            )
        )
        val response = response(listOf(partial)).copy(
            result = response(listOf(partial)).result.copy(request = request)
        )
        val projection = ArgentinaBasketPlanProjector.project(response, emptyList())

        assertEquals(ArgentinaBasketPlanStatus.CURRENT_PARTIAL, projection.status)
        assertEquals("Known subtotal · ARS 12.50", projection.totalText)
        assertTrue(projection.missingIdentityNames.contains("Tea"))
        assertTrue(projection.lines.none { it.productEvidenceKey == "tea" })
    }

    private fun searchOffer(product: String, offer: String, store: String) =
        ArgentinaBackendContract.SearchOffer(
            offerKey = offer,
            productEvidenceKey = product,
            storeKey = store,
            storeName = store,
            storeAddress = mapOf("city" to "Buenos Aires"),
            storeLatitude = "-34.601",
            storeLongitude = "-58.381",
            distanceKm = "1.2",
            listPrice = money("12.50"),
            referencePrice = null,
            quantity = quantity(),
            quantityStatus = "KNOWN",
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
