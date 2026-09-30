package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShoppingDecisionEngineTest {

    private val storeA = ShoppingStoreKey("store-a")
    private val storeB = ShoppingStoreKey("store-b")
    private val bread = ShoppingItemKey("product-bread")
    private val milk = ShoppingItemKey("product-milk")
    private val tea = ShoppingItemKey("product-tea")

    @Test
    fun `complete current basket uses exact package arithmetic and zero trip break even`() {
        val result = evaluate(
            basket = listOf(
                line("bread", bread, QuantityNormalization.count(1)),
                line("milk", milk, QuantityNormalization.count(1))
            ),
            stores = listOf(ShoppingDecisionStoreScenario(storeA, zeroTrip())) ,
            offers = listOf(
                offer("bread-now", bread, storeA, "3.25", QuantityNormalization.count(1)),
                offer("milk-now", milk, storeA, "4.75", QuantityNormalization.count(1))
            )
        ).scenarios.single()

        assertEquals(ShoppingDecisionScenarioStatus.CURRENT_COMPLETE, result.status)
        assertEquals(Money.parse("8.00", "CAD"), result.currentBasketTotal)
        assertEquals(Money.parse("0.00", "CAD"), result.breakEvenSavingsRequired)
        assertEquals(Money.parse("8.00", "CAD"), result.netCurrentAfterTripCost)
        assertTrue(result.explanation.factsUsed.contains("offer:bread-now"))
    }

    @Test
    fun `partial current coverage keeps unknown line visible and preserves personal history`() {
        val observation =
            PriceObservation.personal(
                observationId = "milk-memory",
                productIdentityKey = milk.value,
                packageQuantity = QuantityNormalization.count(1),
                price = Money.parse("5.25", "CAD"),
                observedDateIso = "2026-09-20",
                store = ConfirmedPurchaseStore(storeKey = storeA)
            )
        val result = evaluate(
            basket = listOf(
                line("bread", bread, QuantityNormalization.count(1)),
                line("milk", milk, QuantityNormalization.count(1))
            ),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            offers = listOf(offer("bread-now", bread, storeA, "3.25", QuantityNormalization.count(1))),
            priceWatchState = PriceWatchState(observations = listOf(observation))
        ).scenarios.single()

        assertEquals(ShoppingDecisionScenarioStatus.CURRENT_PARTIAL, result.status)
        assertNull(result.currentBasketTotal)
        assertEquals(1, result.calculableCurrentLineCount)
        assertEquals(1, result.calculableHistoricalLineCount)
        val milkLine = result.lines.single { it.line.productIdentityKey == milk }
        assertEquals(ShoppingDecisionLineStatus.HISTORICAL_OR_PERSONAL_ONLY, milkLine.status)
        assertTrue(ShoppingDecisionEvidenceClass.PERSONAL_PRICE_OBSERVATION in milkLine.evidenceClasses)
        assertEquals(Money.parse("5.25", "CAD"), milkLine.historicalAmount)
    }

    @Test
    fun `historical retailer and confirmed purchase never become current`() {
        val purchase =
            ConfirmedPurchase(
                purchaseId = "purchase-1",
                productIdentityKey = bread.value,
                identityOrigin = ConfirmedPurchaseIdentityOrigin.HOME_BASKET,
                displayName = "Bread",
                quantity = QuantityNormalization.count(1),
                amountPaid = Money.parse("2.50", "CAD"),
                purchaseDateIso = "2026-09-01",
                store = ConfirmedPurchaseStore(storeKey = storeA)
            )
        val historical =
            ShoppingDecisionHistoricalRetailerObservation(
                observationId = "retailer-old",
                productIdentityKey = bread,
                storeKey = storeA,
                price = Money.parse("2.75", "CAD"),
                packageQuantity = QuantityNormalization.count(1),
                observedAtEpochMillis = 1_758_000_000_000L,
                provenance = provenance("history")
            )
        val line = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(1))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            historical = listOf(historical),
            purchases = listOf(purchase)
        ).scenarios.single().lines.single()

        assertEquals(ShoppingDecisionLineStatus.HISTORICAL_OR_PERSONAL_ONLY, line.status)
        assertNull(line.currentAmount)
        assertEquals(listOf("retailer-old"), line.historicalRetailerObservationIds)
        assertEquals(listOf("purchase-1"), line.confirmedPurchaseIds)
        assertTrue(line.explanation.why.contains("not a current store price"))
    }

    @Test
    fun `unauthorized and stale offers remain blocked`() {
        val unauthorized = offer("not-authorized", bread, storeA, "1.00", QuantityNormalization.count(1))
            .copy(authorization = ShoppingDecisionAuthorizationState.UNAUTHORIZED)
        val stale = offer("stale", bread, storeA, "0.90", QuantityNormalization.count(1), EvidenceFreshness.STALE)
        val line = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(1))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            offers = listOf(unauthorized, stale)
        ).scenarios.single().lines.single()

        assertEquals(ShoppingDecisionLineStatus.UNKNOWN, line.status)
        assertNull(line.currentAmount)
        assertTrue(ShoppingDecisionBlocker.CURRENT_OFFER_UNAUTHORIZED in line.blockers)
        assertTrue(ShoppingDecisionBlocker.CURRENT_OFFER_NOT_FRESH in line.blockers)
        assertTrue(ShoppingDecisionEvidenceClass.UNKNOWN in line.evidenceClasses)
    }

    @Test
    fun `different currencies are reported separately and never summed`() {
        val result = evaluate(
            basket = listOf(
                line("bread", bread, QuantityNormalization.count(1)),
                line("milk", milk, QuantityNormalization.count(1))
            ),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            offers = listOf(
                offer("bread-cad", bread, storeA, "3.00", QuantityNormalization.count(1), currency = "CAD"),
                offer("milk-usd", milk, storeA, "4.00", QuantityNormalization.count(1), currency = "USD")
            )
        ).scenarios.single()

        assertEquals(ShoppingDecisionScenarioStatus.CURRENT_PARTIAL, result.status)
        assertNull(result.currentBasketTotal)
        assertEquals(2, result.partialCurrentTotals.size)
        assertEquals(setOf("CAD", "USD"), result.partialCurrentTotals.map { it.currencyCode }.toSet())
    }

    @Test
    fun `requested quantity rounds up exact package count`() {
        val result = evaluate(
            basket = listOf(line("tea", tea, QuantityNormalization.grams(1_500))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            offers = listOf(offer("tea-pack", tea, storeA, "3.00", QuantityNormalization.grams(1_000)))
        ).scenarios.single()

        assertEquals(Money.parse("6.00", "CAD"), result.currentBasketTotal)
        assertEquals(ShoppingDecisionLineStatus.CURRENT_CALCULABLE, result.lines.single().status)
    }

    @Test
    fun `repeated purchases retain both facts and use the latest dated context`() {
        fun purchase(id: String, date: String, price: String) =
            ConfirmedPurchase(
                purchaseId = id,
                productIdentityKey = bread.value,
                identityOrigin = ConfirmedPurchaseIdentityOrigin.HOME_BASKET,
                displayName = "Bread",
                quantity = QuantityNormalization.count(1),
                amountPaid = Money.parse(price, "CAD"),
                purchaseDateIso = date,
                store = ConfirmedPurchaseStore(storeKey = storeA)
            )
        val line = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(1))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            purchases = listOf(
                purchase("old", "2026-08-01", "2.00"),
                purchase("new", "2026-09-20", "3.00")
            )
        ).scenarios.single().lines.single()

        assertEquals(listOf("new", "old"), line.confirmedPurchaseIds)
        assertEquals(Money.parse("3.00", "CAD"), line.historicalAmount)
    }

    @Test
    fun `correcting purchase store changes only the matching scenario`() {
        val purchase =
            ConfirmedPurchase(
                purchaseId = "corrected",
                productIdentityKey = bread.value,
                identityOrigin = ConfirmedPurchaseIdentityOrigin.MANUAL,
                displayName = "Bread",
                quantity = QuantityNormalization.count(1),
                amountPaid = Money.parse("3.00", "CAD"),
                purchaseDateIso = "2026-09-25",
                store = ConfirmedPurchaseStore(storeKey = storeB)
            )
        val result = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(1))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA), ShoppingDecisionStoreScenario(storeB)),
            purchases = listOf(purchase)
        )

        assertEquals(ShoppingDecisionScenarioStatus.UNKNOWN, result.scenarios.first { it.storeKey == storeA }.status)
        assertEquals(ShoppingDecisionScenarioStatus.HISTORICAL_ONLY, result.scenarios.first { it.storeKey == storeB }.status)
    }

    @Test
    fun `watch target is not a current offer`() {
        val watch = PriceWatch(
            productIdentityKey = bread.value,
            displayName = "Bread",
            targetPrice = Money.parse("2.00", "CAD")
        )
        val line = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(1))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            priceWatchState = PriceWatchState(watches = listOf(watch))
        ).scenarios.single().lines.single()

        assertEquals(ShoppingDecisionLineStatus.TARGET_ONLY, line.status)
        assertEquals(Money.parse("2.00", "CAD"), line.targetPrice)
        assertNull(line.currentAmount)
        assertTrue(ShoppingDecisionBlocker.TARGET_IS_NOT_CURRENT in line.blockers)
    }

    @Test
    fun `zero trip cost produces an exact zero break even threshold`() {
        val scenario = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(1))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA, zeroTrip())) ,
            offers = listOf(offer("bread-now", bread, storeA, "3.00", QuantityNormalization.count(1)))
        ).scenarios.single()

        assertEquals(Money.parse("0.00", "CAD"), scenario.breakEvenSavingsRequired)
        assertEquals(Money.parse("3.00", "CAD"), scenario.netCurrentAfterTripCost)
        assertTrue(ShoppingDecisionEvidenceClass.ESTIMATED_TRAVEL_COST in scenario.evidenceClasses)
    }

    @Test
    fun `break even threshold distinguishes one item from a combined basket`() {
        val trip =
            TripCostInput(
                drivingDistanceMetres = 0L,
                distanceBasis = TripDistanceBasis.ADDITIONAL_DRIVING,
                fuelConsumptionMilliLitresPer100Km = 8_000L,
                fuelPricePerLitre = Money.parse("1.80", "CAD"),
                tolls = Money.parse("5.00", "CAD")
            )
        val one = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(1))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA, trip)),
            offers = listOf(offer("bread-now", bread, storeA, "3.00", QuantityNormalization.count(1)))
        ).scenarios.single()
        val combined = evaluate(
            basket = listOf(
                line("bread", bread, QuantityNormalization.count(1)),
                line("milk", milk, QuantityNormalization.count(1))
            ),
            stores = listOf(ShoppingDecisionStoreScenario(storeA, trip)),
            offers = listOf(
                offer("bread-now", bread, storeA, "3.00", QuantityNormalization.count(1)),
                offer("milk-now", milk, storeA, "5.00", QuantityNormalization.count(1))
            )
        ).scenarios.single()

        assertEquals(Money.parse("5.00", "CAD"), one.breakEvenSavingsRequired)
        assertEquals(Money.parse("-2.00", "CAD"), one.netCurrentAfterTripCost)
        assertEquals(Money.parse("3.00", "CAD"), combined.netCurrentAfterTripCost)
    }

    @Test
    fun `current price remains explicit when package quantity is unknown`() {
        val line = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(1))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            offers = listOf(offer("bread-now", bread, storeA, "2.00", null))
        ).scenarios.single().lines.single()

        assertEquals(ShoppingDecisionLineStatus.CURRENT_PRICE_KNOWN_QUANTITY_UNKNOWN, line.status)
        assertNull(line.currentAmount)
        assertTrue(ShoppingDecisionBlocker.OFFER_QUANTITY_UNKNOWN in line.blockers)
    }

    @Test
    fun `conflicting authorized current offers stay unknown instead of being ranked`() {
        val line = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(1))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            offers = listOf(
                offer("one", bread, storeA, "2.00", QuantityNormalization.count(1)),
                offer("two", bread, storeA, "2.50", QuantityNormalization.count(1))
            )
        ).scenarios.single().lines.single()

        assertEquals(ShoppingDecisionLineStatus.CURRENT_OFFER_CONFLICT, line.status)
        assertNull(line.currentAmount)
        assertTrue(ShoppingDecisionBlocker.MULTIPLE_CURRENT_OFFERS_CONFLICT in line.blockers)
    }

    @Test
    fun `changing basket quantity changes the same stateless scenario`() {
        val offer = offer("bread-now", bread, storeA, "2.00", QuantityNormalization.count(1))
        val first = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(1))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            offers = listOf(offer)
        ).scenarios.single().currentBasketTotal
        val second = evaluate(
            basket = listOf(line("bread", bread, QuantityNormalization.count(2))),
            stores = listOf(ShoppingDecisionStoreScenario(storeA)),
            offers = listOf(offer)
        ).scenarios.single().currentBasketTotal

        assertEquals(Money.parse("2.00", "CAD"), first)
        assertEquals(Money.parse("4.00", "CAD"), second)
    }

    private fun evaluate(
        basket: List<ShoppingDecisionBasketLine>,
        stores: List<ShoppingDecisionStoreScenario>,
        offers: List<ShoppingDecisionCurrentOffer> = emptyList(),
        historical: List<ShoppingDecisionHistoricalRetailerObservation> = emptyList(),
        purchases: List<ConfirmedPurchase> = emptyList(),
        priceWatchState: PriceWatchState = PriceWatchState.empty()
    ): ShoppingDecisionResult =
        ShoppingDecisionEngine.evaluate(
            ShoppingDecisionInput(
                basket = basket,
                stores = stores,
                currentOffers = offers,
                historicalRetailerObservations = historical,
                confirmedPurchases = purchases,
                priceWatchState = priceWatchState
            )
        )

    private fun line(
        id: String,
        key: ShoppingItemKey,
        quantity: NormalizedQuantity?
    ): ShoppingDecisionBasketLine =
        ShoppingDecisionBasketLine(id, key, id.replaceFirstChar(Char::uppercase), quantity)

    private fun offer(
        id: String,
        key: ShoppingItemKey,
        store: ShoppingStoreKey,
        price: String,
        quantity: NormalizedQuantity?,
        freshness: EvidenceFreshness = EvidenceFreshness.FRESH,
        currency: String = "CAD"
    ): ShoppingDecisionCurrentOffer =
        ShoppingDecisionCurrentOffer(
            offerId = id,
            productIdentityKey = key,
            storeKey = store,
            price = Money.parse(price, currency),
            packageQuantity = quantity,
            observedAtEpochMillis = 1_758_000_000_000L,
            freshness = freshness,
            authorization = ShoppingDecisionAuthorizationState.AUTHORIZED,
            provenance = provenance(id)
        )

    private fun provenance(id: String): ShoppingDecisionOfferProvenance =
        ShoppingDecisionOfferProvenance(
            providerId = "fixture-provider",
            sourceId = id,
            rightsRecordId = "rights-fixture-v1"
        )

    private fun zeroTrip(): TripCostInput =
        TripCostInput(
            drivingDistanceMetres = 0L,
            distanceBasis = TripDistanceBasis.ADDITIONAL_DRIVING,
            fuelConsumptionMilliLitresPer100Km = 8_000L,
            fuelPricePerLitre = Money.parse("1.80", "CAD")
        )
}
