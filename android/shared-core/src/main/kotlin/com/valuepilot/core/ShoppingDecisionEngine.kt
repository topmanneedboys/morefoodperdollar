package com.valuepilot.core

import java.math.BigInteger

private const val MAX_DECISION_BASKET_LINES = 128
private const val MAX_DECISION_STORES = 64
private const val MAX_DECISION_CURRENT_OFFERS = 512
private const val MAX_DECISION_HISTORICAL_OBSERVATIONS = 512
private const val MAX_DECISION_PURCHASES = 512

/** The factual classes the decision engine keeps separate. */
enum class ShoppingDecisionEvidenceClass {
    CURRENT_AUTHORIZED_RETAILER_OFFER,
    HISTORICAL_RETAILER_OBSERVATION,
    SHOPPER_CONFIRMED_PURCHASE_PRICE,
    PERSONAL_PRICE_OBSERVATION,
    TARGET_PRICE,
    UNKNOWN,
    ESTIMATED_TRAVEL_COST
}

/** Authorization is part of a future offer contract, never inferred from a provider name. */
enum class ShoppingDecisionAuthorizationState {
    AUTHORIZED,
    UNAUTHORIZED,
    UNKNOWN
}

/** Small immutable provenance record required before a current offer can be considered. */
data class ShoppingDecisionOfferProvenance(
    val providerId: String,
    val sourceId: String,
    val rightsRecordId: String
) {
    init {
        require(providerId.isNotBlank() && providerId.length <= 160)
        require(sourceId.isNotBlank() && sourceId.length <= 240)
        require(rightsRecordId.isNotBlank() && rightsRecordId.length <= 240)
    }
}

/**
 * Future-ready current-offer input.  This type does not activate a feed; callers must supply an
 * explicit authorization state, exact timestamp, provenance, freshness, store and quantity.
 */
data class ShoppingDecisionCurrentOffer(
    val offerId: String,
    val productIdentityKey: ShoppingItemKey,
    val storeKey: ShoppingStoreKey,
    val price: Money,
    val packageQuantity: NormalizedQuantity?,
    val observedAtEpochMillis: Long,
    val freshness: EvidenceFreshness,
    val authorization: ShoppingDecisionAuthorizationState,
    val provenance: ShoppingDecisionOfferProvenance
) {
    init {
        require(offerId.isNotBlank() && offerId.length <= 240)
        require(price.minorUnits > 0L)
        require(observedAtEpochMillis > 0L)
    }
}

/** Historical retailer evidence is descriptive only and never becomes a current offer. */
data class ShoppingDecisionHistoricalRetailerObservation(
    val observationId: String,
    val productIdentityKey: ShoppingItemKey,
    val storeKey: ShoppingStoreKey,
    val price: Money,
    val packageQuantity: NormalizedQuantity?,
    val observedAtEpochMillis: Long?,
    val provenance: ShoppingDecisionOfferProvenance
) {
    init {
        require(observationId.isNotBlank() && observationId.length <= 240)
        require(price.minorUnits > 0L)
        require(observedAtEpochMillis == null || observedAtEpochMillis > 0L)
    }
}

/** One exact basket identity and the quantity the shopper wants to cover. */
data class ShoppingDecisionBasketLine(
    val lineId: String,
    val productIdentityKey: ShoppingItemKey,
    val displayName: String,
    val requestedQuantity: NormalizedQuantity?
) {
    init {
        require(lineId.isNotBlank() && lineId.length <= 240)
        require(displayName.isNotBlank() && displayName.length <= 512)
    }
}

/** One store plus optional explicit, manually calculated trip assumptions. */
data class ShoppingDecisionStoreScenario(
    val storeKey: ShoppingStoreKey,
    val tripCostInput: TripCostInput? = null
)

/** All existing immutable facts are passed by reference; this is not a second persistence model. */
data class ShoppingDecisionInput(
    val basket: List<ShoppingDecisionBasketLine>,
    val stores: List<ShoppingDecisionStoreScenario>,
    val currentOffers: List<ShoppingDecisionCurrentOffer> = emptyList(),
    val historicalRetailerObservations: List<ShoppingDecisionHistoricalRetailerObservation> = emptyList(),
    val confirmedPurchases: List<ConfirmedPurchase> = emptyList(),
    val priceWatchState: PriceWatchState = PriceWatchState.empty()
) {
    init {
        require(basket.isNotEmpty())
        require(basket.size <= MAX_DECISION_BASKET_LINES)
        require(basket.map { it.lineId }.distinct().size == basket.size)
        require(basket.map { it.productIdentityKey }.distinct().size == basket.size) {
            "A decision basket uses one exact line per product identity"
        }
        require(stores.isNotEmpty())
        require(stores.size <= MAX_DECISION_STORES)
        require(stores.map { it.storeKey }.distinct().size == stores.size)
        require(currentOffers.size <= MAX_DECISION_CURRENT_OFFERS)
        require(currentOffers.map { it.offerId }.distinct().size == currentOffers.size)
        require(historicalRetailerObservations.size <= MAX_DECISION_HISTORICAL_OBSERVATIONS)
        require(
            historicalRetailerObservations.map { it.observationId }.distinct().size ==
                historicalRetailerObservations.size
        )
        require(confirmedPurchases.size <= MAX_DECISION_PURCHASES)
        require(confirmedPurchases.map { it.purchaseId }.distinct().size == confirmedPurchases.size)
    }
}

enum class ShoppingDecisionLineStatus {
    CURRENT_CALCULABLE,
    CURRENT_PRICE_KNOWN_QUANTITY_UNKNOWN,
    CURRENT_OFFER_CONFLICT,
    HISTORICAL_OR_PERSONAL_ONLY,
    TARGET_ONLY,
    UNKNOWN
}

enum class ShoppingDecisionBlocker {
    NO_CURRENT_AUTHORIZED_OFFER,
    CURRENT_OFFER_UNAUTHORIZED,
    CURRENT_OFFER_NOT_FRESH,
    BASKET_QUANTITY_UNKNOWN,
    OFFER_QUANTITY_UNKNOWN,
    QUANTITY_UNIT_MISMATCH,
    MULTIPLE_CURRENT_OFFERS_CONFLICT,
    HISTORICAL_QUANTITY_UNKNOWN,
    HISTORICAL_UNIT_MISMATCH,
    HISTORICAL_PRICE_UNAVAILABLE,
    ARITHMETIC_OVERFLOW,
    CURRENCY_NOT_COMPARABLE,
    TARGET_IS_NOT_CURRENT
}

data class ShoppingDecisionExplanation(
    val known: List<String>,
    val estimated: List<String>,
    val missing: List<String>,
    val factsUsed: List<String>,
    val why: String
) {
    init {
        require(why.isNotBlank())
        listOf(known, estimated, missing, factsUsed).flatten().forEach {
            require(it.isNotBlank())
        }
    }
}

data class ShoppingDecisionLineResult(
    val line: ShoppingDecisionBasketLine,
    val storeKey: ShoppingStoreKey,
    val status: ShoppingDecisionLineStatus,
    val evidenceClasses: Set<ShoppingDecisionEvidenceClass>,
    val currentOfferIds: List<String>,
    val historicalRetailerObservationIds: List<String>,
    val confirmedPurchaseIds: List<String>,
    val personalObservationIds: List<String>,
    val currentAmount: Money?,
    val historicalAmount: Money?,
    val targetPrice: Money?,
    val blockers: Set<ShoppingDecisionBlocker>,
    val explanation: ShoppingDecisionExplanation
) {
    init {
        require(currentOfferIds.distinct().size == currentOfferIds.size)
        require(historicalRetailerObservationIds.distinct().size == historicalRetailerObservationIds.size)
        require(confirmedPurchaseIds.distinct().size == confirmedPurchaseIds.size)
        require(personalObservationIds.distinct().size == personalObservationIds.size)
        require(evidenceClasses.isNotEmpty())
        require(status != ShoppingDecisionLineStatus.CURRENT_CALCULABLE || currentAmount != null)
        require(currentAmount == null || currentAmount.minorUnits >= 0L)
        require(historicalAmount == null || historicalAmount.minorUnits >= 0L)
    }
}

enum class ShoppingDecisionScenarioStatus {
    CURRENT_COMPLETE,
    CURRENT_PARTIAL,
    HISTORICAL_ONLY,
    TARGET_ONLY,
    UNKNOWN
}

data class ShoppingDecisionScenarioResult(
    val storeKey: ShoppingStoreKey,
    val lines: List<ShoppingDecisionLineResult>,
    val evidenceClasses: Set<ShoppingDecisionEvidenceClass>,
    val status: ShoppingDecisionScenarioStatus,
    val currentBasketTotal: Money?,
    val partialCurrentTotals: List<Money>,
    val historicalBasketTotals: List<Money>,
    val calculableCurrentLineCount: Int,
    val calculableHistoricalLineCount: Int,
    val breakEvenSavingsRequired: Money?,
    val tripCost: TripCostResult?,
    val netCurrentAfterTripCost: Money?,
    val explanation: ShoppingDecisionExplanation
) {
    init {
        require(lines.isNotEmpty())
        require(evidenceClasses.isNotEmpty())
        require(
            (ShoppingDecisionEvidenceClass.ESTIMATED_TRAVEL_COST in evidenceClasses) ==
                (tripCost != null)
        )
        require(calculableCurrentLineCount in 0..lines.size)
        require(calculableHistoricalLineCount in 0..lines.size)
        require(partialCurrentTotals.map { it.currencyCode to it.fractionDigits }.distinct().size == partialCurrentTotals.size)
        require(historicalBasketTotals.map { it.currencyCode to it.fractionDigits }.distinct().size == historicalBasketTotals.size)
        require((breakEvenSavingsRequired == null) == (tripCost == null))
        require(netCurrentAfterTripCost == null || currentBasketTotal != null)
    }

    val completeCurrentEvidence: Boolean
        get() = status == ShoppingDecisionScenarioStatus.CURRENT_COMPLETE
}

data class ShoppingDecisionResult(
    val scenarios: List<ShoppingDecisionScenarioResult>,
    val explanation: ShoppingDecisionExplanation
) {
    init {
        require(scenarios.isNotEmpty())
        require(scenarios.map { it.storeKey }.distinct().size == scenarios.size)
    }
}

/**
 * Stateless, deterministic whole-basket decision engine.  It never ranks stores and therefore
 * cannot turn partial or personal evidence into a "cheapest" recommendation.
 */
object ShoppingDecisionEngine {

    fun evaluate(input: ShoppingDecisionInput): ShoppingDecisionResult {
        val scenarios =
            input.stores
                .sortedBy { it.storeKey.value }
                .map { scenario -> evaluateScenario(input, scenario) }

        val completeCount = scenarios.count { it.completeCurrentEvidence }
        val explanation =
            ShoppingDecisionExplanation(
                known = listOf("Evaluated ${scenarios.size} explicit store scenario(s)."),
                estimated = scenarios.filter { it.tripCost != null }.map { "Estimated travel cost for ${it.storeKey.value}." },
                missing =
                    if (completeCount == scenarios.size) emptyList()
                    else listOf("Current authorized offer coverage is incomplete for at least one scenario."),
                factsUsed = scenarios.flatMap { it.lines.flatMap { line -> line.explanation.factsUsed } }.distinct().sorted(),
                why =
                    if (completeCount == scenarios.size) {
                        "Every explicit scenario has complete, comparable current price evidence; no store ranking is performed."
                    } else {
                        "The engine reports evidence and arithmetic without claiming a current cheapest store."
                    }
            )
        return ShoppingDecisionResult(scenarios = scenarios, explanation = explanation)
    }

    private fun evaluateScenario(
        input: ShoppingDecisionInput,
        scenario: ShoppingDecisionStoreScenario
    ): ShoppingDecisionScenarioResult {
        val lines =
            input.basket
                .sortedBy { it.lineId }
                .map { line -> evaluateLine(input, line, scenario.storeKey) }

        val currentAmounts = lines.mapNotNull { it.currentAmount }
        val historicalAmounts = lines.mapNotNull { it.historicalAmount }
        val partialCurrentTotals = sumByMoneySpec(currentAmounts)
        val historicalBasketTotals = sumByMoneySpec(historicalAmounts)
        val currentBasketTotal =
            if (currentAmounts.size == lines.size && partialCurrentTotals.size == 1) {
                partialCurrentTotals.single()
            } else {
                null
            }

        val currentEvidencePresent =
            lines.any { ShoppingDecisionEvidenceClass.CURRENT_AUTHORIZED_RETAILER_OFFER in it.evidenceClasses }
        val targetPresent = lines.any { ShoppingDecisionEvidenceClass.TARGET_PRICE in it.evidenceClasses }
        val historicalPresent =
            lines.any {
                it.evidenceClasses.any { kind ->
                    kind == ShoppingDecisionEvidenceClass.HISTORICAL_RETAILER_OBSERVATION ||
                        kind == ShoppingDecisionEvidenceClass.SHOPPER_CONFIRMED_PURCHASE_PRICE ||
                        kind == ShoppingDecisionEvidenceClass.PERSONAL_PRICE_OBSERVATION
                }
            }

        val status =
            when {
                currentBasketTotal != null -> ShoppingDecisionScenarioStatus.CURRENT_COMPLETE
                currentEvidencePresent -> ShoppingDecisionScenarioStatus.CURRENT_PARTIAL
                historicalPresent -> ShoppingDecisionScenarioStatus.HISTORICAL_ONLY
                targetPresent -> ShoppingDecisionScenarioStatus.TARGET_ONLY
                else -> ShoppingDecisionScenarioStatus.UNKNOWN
            }

        val tripCost = scenario.tripCostInput?.let(TripCostCalculator::calculate)
        val breakEven = tripCost?.total
        val net =
            if (currentBasketTotal != null && tripCost != null && sameMoneySpec(currentBasketTotal, tripCost.total)) {
                currentBasketTotal.copy(
                    minorUnits = Math.subtractExact(currentBasketTotal.minorUnits, tripCost.total.minorUnits)
                )
            } else {
                null
            }

        val missing = lines.filter { it.currentAmount == null }.map { it.line.displayName }
        val known =
            buildList {
                add("${currentAmounts.size}/${lines.size} basket line(s) have calculable current amounts.")
                if (historicalAmounts.isNotEmpty()) {
                    add("${historicalAmounts.size}/${lines.size} line(s) have calculable non-current history.")
                }
                if (currentBasketTotal != null) add("Current basket total is exact: ${currentBasketTotal.currencyCode}.")
            }
        val estimated = tripCost?.let { listOf("Trip cost is an estimate from explicit distance and vehicle assumptions: ${it.total.currencyCode}.") }
            ?: emptyList()
        val facts =
            lines.flatMap { it.explanation.factsUsed }.distinct().sorted() +
                (tripCost?.let { listOf("trip:${scenario.storeKey.value}") } ?: emptyList())
        val why =
            when (status) {
                ShoppingDecisionScenarioStatus.CURRENT_COMPLETE ->
                    "All basket lines have one comparable authorized current amount; this result still does not rank stores."
                ShoppingDecisionScenarioStatus.CURRENT_PARTIAL ->
                    "At least one current offer exists, but missing, conflicted, or non-comparable lines keep the basket incomplete."
                ShoppingDecisionScenarioStatus.HISTORICAL_ONLY ->
                    "Only historical, confirmed-purchase, or personal observations are available; they are not today's store prices."
                ShoppingDecisionScenarioStatus.TARGET_ONLY ->
                    "A watch target exists, but a target is not a current offer."
                ShoppingDecisionScenarioStatus.UNKNOWN ->
                    "No usable price evidence is available for this store."
            }
        return ShoppingDecisionScenarioResult(
            storeKey = scenario.storeKey,
            lines = lines,
            evidenceClasses =
                lines.flatMap { it.evidenceClasses }.toSet() +
                    (if (tripCost != null) setOf(ShoppingDecisionEvidenceClass.ESTIMATED_TRAVEL_COST) else emptySet()),
            status = status,
            currentBasketTotal = currentBasketTotal,
            partialCurrentTotals = partialCurrentTotals,
            historicalBasketTotals = historicalBasketTotals,
            calculableCurrentLineCount = currentAmounts.size,
            calculableHistoricalLineCount = historicalAmounts.size,
            breakEvenSavingsRequired = breakEven,
            tripCost = tripCost,
            netCurrentAfterTripCost = net,
            explanation =
                ShoppingDecisionExplanation(
                    known = known,
                    estimated = estimated,
                    missing = missing,
                    factsUsed = facts.distinct().sorted(),
                    why = why
                )
        )
    }

    private fun evaluateLine(
        input: ShoppingDecisionInput,
        line: ShoppingDecisionBasketLine,
        storeKey: ShoppingStoreKey
    ): ShoppingDecisionLineResult {
        val matchingOffers =
            input.currentOffers.filter {
                it.productIdentityKey == line.productIdentityKey && it.storeKey == storeKey
            }
        val eligibleOffers =
            matchingOffers.filter {
                it.authorization == ShoppingDecisionAuthorizationState.AUTHORIZED &&
                    (it.freshness == EvidenceFreshness.FRESH || it.freshness == EvidenceFreshness.AGING)
            }
        val offerIds = eligibleOffers.map { it.offerId }.sorted()
        val unauthorized = matchingOffers.any { it.authorization != ShoppingDecisionAuthorizationState.AUTHORIZED }
        val notFresh = matchingOffers.any {
            it.authorization == ShoppingDecisionAuthorizationState.AUTHORIZED &&
                it.freshness != EvidenceFreshness.FRESH && it.freshness != EvidenceFreshness.AGING
        }
        val offerConflict =
            eligibleOffers
                .map { it.price to it.packageQuantity }
                .distinct()
                .size > 1

        val selectedOffer =
            eligibleOffers
                .sortedWith(compareBy<ShoppingDecisionCurrentOffer>({ it.offerId }))
                .firstOrNull()
                ?.takeIf { !offerConflict }
        val currentCalculation = selectedOffer?.let { calculateAmount(line.requestedQuantity, it.packageQuantity, it.price) }

        val retailerHistory =
            input.historicalRetailerObservations
                .filter { it.productIdentityKey == line.productIdentityKey && it.storeKey == storeKey }
        val purchases =
            input.confirmedPurchases
                .filter { it.productIdentityKey == line.productIdentityKey.value && it.store?.storeKey == storeKey }
        val personal =
            input.priceWatchState.observations
                .filter { it.productIdentityKey == line.productIdentityKey.value && it.store?.storeKey == storeKey }
        val target = input.priceWatchState.watches.firstOrNull { it.productIdentityKey == line.productIdentityKey.value }?.targetPrice

        val historicalCandidates =
            buildList {
                retailerHistory.forEach { observation ->
                    add(
                        HistoricalCandidate(
                            id = "retailer:${observation.observationId}",
                            kind = ShoppingDecisionEvidenceClass.HISTORICAL_RETAILER_OBSERVATION,
                            price = observation.price,
                            packageQuantity = observation.packageQuantity,
                            dateKnown = observation.observedAtEpochMillis != null,
                            dateValue = observation.observedAtEpochMillis?.toString(),
                            amount = calculateAmount(line.requestedQuantity, observation.packageQuantity, observation.price)
                        )
                    )
                }
                purchases.forEach { purchase ->
                    purchase.amountPaid?.let { amountPaid ->
                        add(
                            HistoricalCandidate(
                                id = "purchase:${purchase.purchaseId}",
                                kind = ShoppingDecisionEvidenceClass.SHOPPER_CONFIRMED_PURCHASE_PRICE,
                                price = amountPaid,
                                packageQuantity = purchase.quantity,
                                dateKnown = purchase.purchaseDateIso != null,
                                dateValue = purchase.purchaseDateIso,
                                amount =
                                    calculateAmount(line.requestedQuantity, purchase.quantity, amountPaid)
                            )
                        )
                    }
                }
                personal.forEach { observation ->
                    add(
                        HistoricalCandidate(
                            id = "personal:${observation.observationId}",
                            kind = ShoppingDecisionEvidenceClass.PERSONAL_PRICE_OBSERVATION,
                            price = observation.price,
                            packageQuantity = observation.packageQuantity,
                            dateKnown = observation.observedDateIso != null,
                            dateValue = observation.observedDateIso,
                            amount = calculateAmount(line.requestedQuantity, observation.packageQuantity, observation.price)
                        )
                    )
                }
            }
                .sortedWith(compareByDescending<HistoricalCandidate> { it.dateKnown }.thenByDescending { it.dateValue ?: "" }.thenBy { it.id })

        val selectedHistorical = historicalCandidates.firstOrNull { it.amount != null }
        val evidence = linkedSetOf<ShoppingDecisionEvidenceClass>()
        if (selectedOffer != null || eligibleOffers.isNotEmpty()) evidence += ShoppingDecisionEvidenceClass.CURRENT_AUTHORIZED_RETAILER_OFFER
        retailerHistory.takeIf { it.isNotEmpty() }?.let { evidence += ShoppingDecisionEvidenceClass.HISTORICAL_RETAILER_OBSERVATION }
        purchases.takeIf { it.isNotEmpty() }?.let { evidence += ShoppingDecisionEvidenceClass.SHOPPER_CONFIRMED_PURCHASE_PRICE }
        personal.takeIf { it.isNotEmpty() }?.let { evidence += ShoppingDecisionEvidenceClass.PERSONAL_PRICE_OBSERVATION }
        if (target != null) evidence += ShoppingDecisionEvidenceClass.TARGET_PRICE
        if (evidence.isEmpty()) evidence += ShoppingDecisionEvidenceClass.UNKNOWN

        val blockers = linkedSetOf<ShoppingDecisionBlocker>()
        if (eligibleOffers.isEmpty()) {
            blockers += ShoppingDecisionBlocker.NO_CURRENT_AUTHORIZED_OFFER
            if (unauthorized) blockers += ShoppingDecisionBlocker.CURRENT_OFFER_UNAUTHORIZED
            if (notFresh) blockers += ShoppingDecisionBlocker.CURRENT_OFFER_NOT_FRESH
        }
        if (offerConflict) blockers += ShoppingDecisionBlocker.MULTIPLE_CURRENT_OFFERS_CONFLICT
        if (eligibleOffers.isNotEmpty() && line.requestedQuantity == null) blockers += ShoppingDecisionBlocker.BASKET_QUANTITY_UNKNOWN
        if (selectedOffer != null && selectedOffer.packageQuantity == null) blockers += ShoppingDecisionBlocker.OFFER_QUANTITY_UNKNOWN
        if (selectedOffer != null && currentCalculation == null && line.requestedQuantity != null && selectedOffer.packageQuantity != null) {
            blockers +=
                if (line.requestedQuantity.unit != selectedOffer.packageQuantity.unit) {
                    ShoppingDecisionBlocker.QUANTITY_UNIT_MISMATCH
                } else {
                    ShoppingDecisionBlocker.ARITHMETIC_OVERFLOW
                }
        }
        if (historicalCandidates.isNotEmpty() && selectedHistorical == null) {
            blockers += ShoppingDecisionBlocker.HISTORICAL_QUANTITY_UNKNOWN
        }
        if (target != null) blockers += ShoppingDecisionBlocker.TARGET_IS_NOT_CURRENT

        val status =
            when {
                currentCalculation != null && !offerConflict -> ShoppingDecisionLineStatus.CURRENT_CALCULABLE
                offerConflict -> ShoppingDecisionLineStatus.CURRENT_OFFER_CONFLICT
                eligibleOffers.isNotEmpty() && currentCalculation == null ->
                    ShoppingDecisionLineStatus.CURRENT_PRICE_KNOWN_QUANTITY_UNKNOWN
                historicalCandidates.isNotEmpty() || target != null -> ShoppingDecisionLineStatus.HISTORICAL_OR_PERSONAL_ONLY.takeIf { historicalCandidates.isNotEmpty() }
                    ?: ShoppingDecisionLineStatus.TARGET_ONLY
                else -> ShoppingDecisionLineStatus.UNKNOWN
            }

        val facts =
            offerIds.map { "offer:$it" } +
                retailerHistory.map { "retailer:${it.observationId}" } +
                purchases.map { "purchase:${it.purchaseId}" } +
                personal.map { "personal:${it.observationId}" } +
                (if (target != null) listOf("watch:${line.productIdentityKey.value}") else emptyList())
        val missing =
            if (currentCalculation == null) listOf("A comparable current amount for ${line.displayName} is unavailable.") else emptyList()
        val known =
            buildList {
                if (currentCalculation != null) add("Current authorized offer evidence is calculable.")
                if (selectedHistorical != null) add("Non-current evidence is available for context only.")
                if (target != null) add("A shopper target price is recorded.")
            }
        val why =
            when (status) {
                ShoppingDecisionLineStatus.CURRENT_CALCULABLE -> "Exact product, store, price, package quantity, freshness and authorization support this amount."
                ShoppingDecisionLineStatus.CURRENT_PRICE_KNOWN_QUANTITY_UNKNOWN -> "A current offer exists, but exact basket/package quantity arithmetic cannot be completed."
                ShoppingDecisionLineStatus.CURRENT_OFFER_CONFLICT -> "Multiple eligible current offers disagree; the engine keeps the result unknown instead of choosing silently."
                ShoppingDecisionLineStatus.HISTORICAL_OR_PERSONAL_ONLY -> "Historical, confirmed-purchase, or personal evidence is not a current store price."
                ShoppingDecisionLineStatus.TARGET_ONLY -> "A watch target is a shopper intention, not an observed price."
                ShoppingDecisionLineStatus.UNKNOWN -> "No exact usable evidence is available for this product at this store."
            }

        return ShoppingDecisionLineResult(
            line = line,
            storeKey = storeKey,
            status = status,
            evidenceClasses = evidence,
            currentOfferIds = offerIds,
            historicalRetailerObservationIds = retailerHistory.map { it.observationId }.sorted(),
            confirmedPurchaseIds = purchases.map { it.purchaseId }.sorted(),
            personalObservationIds = personal.map { it.observationId }.sorted(),
            currentAmount = currentCalculation,
            historicalAmount = selectedHistorical?.amount,
            targetPrice = target,
            blockers = blockers,
            explanation =
                ShoppingDecisionExplanation(
                    known = known,
                    estimated = emptyList(),
                    missing = missing,
                    factsUsed = facts.distinct().sorted(),
                    why = why
                )
        )
    }

    private data class HistoricalCandidate(
        val id: String,
        val kind: ShoppingDecisionEvidenceClass,
        val price: Money,
        val packageQuantity: NormalizedQuantity?,
        val dateKnown: Boolean,
        val dateValue: String?,
        val amount: Money?
    )

    private fun calculateAmount(
        requestedQuantity: NormalizedQuantity?,
        packageQuantity: NormalizedQuantity?,
        packagePrice: Money
    ): Money? {
        if (requestedQuantity == null || packageQuantity == null) return null
        if (requestedQuantity.unit != packageQuantity.unit) return null
        return try {
            val requested = BigInteger.valueOf(requestedQuantity.amountMicros)
            val packageAmount = BigInteger.valueOf(packageQuantity.amountMicros)
            val packageCount =
                requested
                    .add(packageAmount)
                    .subtract(BigInteger.ONE)
                    .divide(packageAmount)
                    .longValueExact()
            packagePrice.copy(minorUnits = Math.multiplyExact(packagePrice.minorUnits, packageCount))
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun sumByMoneySpec(values: List<Money>): List<Money> =
        values
            .groupBy { it.currencyCode to it.fractionDigits }
            .map { (spec, amounts) ->
                amounts.drop(1).fold(amounts.first()) { subtotal, amount -> subtotal + amount }
            }
            .sortedWith(compareBy<Money>({ it.currencyCode }, { it.fractionDigits }))

    private fun sameMoneySpec(left: Money, right: Money): Boolean =
        left.currencyCode == right.currencyCode && left.fractionDigits == right.fractionDigits
}
