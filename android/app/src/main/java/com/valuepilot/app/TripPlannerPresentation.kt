package com.valuepilot.app

import com.valuepilot.core.TripBasketEconomics
import com.valuepilot.core.TripCostResult
import com.valuepilot.core.TripCostCalculator
import com.valuepilot.core.TripBasketSavingsStatus
import java.math.BigDecimal

internal data class TripPlannerBasketSnapshot(
    val plannedItemCount: Int,
    val privateIdentityCount: Int,
    val unknownItemCount: Int,
    val revision: String
) {
    init {
        require(plannedItemCount >= 0)
        require(privateIdentityCount >= 0)
        require(unknownItemCount >= 0)
        require(revision.isNotBlank())
    }

    val totalVisibleItemCount: Int
        get() = Math.addExact(Math.addExact(plannedItemCount, privateIdentityCount), unknownItemCount)
}

internal enum class TripPlannerSurfaceMode {
    EMPTY_BASKET,
    NEEDS_INPUT,
    CALCULATED,
    UNAVAILABLE
}

internal enum class TripPlannerEvidenceTone {
    POSITIVE,
    NEUTRAL,
    WARNING,
    UNKNOWN
}

internal data class TripPlannerEvidenceChip(
    val label: String,
    val count: Int,
    val tone: TripPlannerEvidenceTone
) {
    init {
        require(label.isNotBlank())
        require(count >= 0)
    }
}

internal data class TripPlannerSurfaceState(
    val mode: TripPlannerSurfaceMode,
    val headline: String,
    val guidance: String,
    val basketText: String,
    val destinationText: String,
    val originText: String,
    val assumptionsText: String,
    val tripCostText: String,
    val savingsText: String,
    val decisionText: String = "Shopping decision unavailable until an exact store is selected.",
    val tripCostHeadline: String,
    val tripCostDetail: String,
    val breakEvenText: String,
    val decisionHeadline: String,
    val decisionDetail: String,
    val evidenceChips: List<TripPlannerEvidenceChip>,
    val destinationReady: Boolean,
    val originReady: Boolean,
    val assumptionsReady: Boolean,
    val directionsAvailable: Boolean,
    val canCalculate: Boolean,
    val basketRevision: String
) {
    init {
        listOf(
            headline,
            guidance,
            basketText,
            destinationText,
            originText,
            assumptionsText,
            tripCostText,
            savingsText,
            decisionText,
            tripCostHeadline,
            tripCostDetail,
            breakEvenText,
            decisionHeadline,
            decisionDetail,
            basketRevision
        ).forEach { require(it.isNotBlank()) }
        require(evidenceChips.isNotEmpty())
        require(mode != TripPlannerSurfaceMode.CALCULATED || canCalculate)
    }
}

internal object TripPlannerProjector {
    fun project(
        state: TripPlannerState?,
        basket: TripPlannerBasketSnapshot,
        directoryAvailable: Boolean,
        unavailable: Boolean = false,
        decision: com.valuepilot.core.ShoppingDecisionScenarioResult? = null
    ): TripPlannerSurfaceState {
        if (unavailable || state == null) {
            return TripPlannerSurfaceState(
                mode = TripPlannerSurfaceMode.UNAVAILABLE,
                headline = "Trip planner unavailable",
                guidance = "ValuePilot could not safely read the private trip draft. Existing data was not replaced.",
                basketText = basketText(basket),
                destinationText = "Destination store unavailable",
                originText = "Starting point unavailable",
                assumptionsText = "Trip assumptions unavailable",
                tripCostText = "Trip cost unknown",
                savingsText = unknownSavingsText(),
                decisionText = decision?.let(ShoppingDecisionTripPlannerBridge::summary)
                    ?: "Shopping decision unavailable because the private trip draft could not be read.",
                tripCostHeadline = "Trip cost unavailable",
                tripCostDetail = "The private trip draft could not be read safely; existing data was preserved.",
                breakEvenText = "Break-even threshold unavailable until a readable trip cost exists.",
                decisionHeadline = "Not enough evidence to recommend a store",
                decisionDetail = "ValuePilot cannot make a stronger shopping decision until the private trip draft is readable.",
                evidenceChips = unavailableEvidenceChips(basket),
                destinationReady = false,
                originReady = false,
                assumptionsReady = false,
                directionsAvailable = false,
                canCalculate = false,
                basketRevision = basket.revision
            )
        }
        val input = state.costInputOrNull()
        val hasBasket = basket.totalVisibleItemCount > 0
        val ready = hasBasket && state.destination != null && state.origin != null && input != null
        val result = if (ready) runCatching { TripCostCalculator.calculate(input) }.getOrNull() else null
        val mode =
            when {
                !hasBasket -> TripPlannerSurfaceMode.EMPTY_BASKET
                result != null -> TripPlannerSurfaceMode.CALCULATED
                else -> TripPlannerSurfaceMode.NEEDS_INPUT
            }
        val decisionPresentation = decisionPresentation(decision, result, basket)
        return TripPlannerSurfaceState(
            mode = mode,
            headline = if (result == null) "Plan your trip" else "Trip cost estimate",
            guidance = guidance(mode, directoryAvailable),
            basketText = basketText(basket),
            destinationText = state.destination?.let(::storeText) ?: "Choose a destination store",
            originText = state.origin?.let { "Starting point: ${it.label} (${it.kind.name.lowercase().replace('_', ' ')})" }
                ?: "Starting point: not entered",
            assumptionsText = input?.let(::assumptionsText) ?: "Enter driving distance, fuel use and fuel price",
            tripCostText = result?.let(::costText) ?: "Trip cost: unknown until complete assumptions are entered",
            savingsText = unknownSavingsText(),
            decisionText = decision?.let(ShoppingDecisionTripPlannerBridge::summary)
                ?: "Shopping decision unavailable until an exact destination store is selected.",
            tripCostHeadline = decisionPresentation.tripCostHeadline,
            tripCostDetail = decisionPresentation.tripCostDetail,
            breakEvenText = decisionPresentation.breakEvenText,
            decisionHeadline = decisionPresentation.decisionHeadline,
            decisionDetail = decisionPresentation.decisionDetail,
            evidenceChips = evidenceChips(decision, basket),
            destinationReady = state.destination != null,
            originReady = state.origin != null,
            assumptionsReady = input != null,
            directionsAvailable = state.destination?.let { it.latitudeE7 != null && it.longitudeE7 != null } == true,
            canCalculate = ready,
            basketRevision = basket.revision
        )
    }

    private fun guidance(mode: TripPlannerSurfaceMode, directoryAvailable: Boolean): String =
        when (mode) {
            TripPlannerSurfaceMode.EMPTY_BASKET -> "Add something to Basket first. This planner always reads the current Basket; it does not copy or replace it."
            TripPlannerSurfaceMode.NEEDS_INPUT ->
                if (directoryAvailable) {
                    "Choose a store and enter your own route and vehicle assumptions. Automatic road routing is not connected."
                } else {
                    "No bundled store directory is shipped. You can still enter a private destination store and manual route assumptions."
                }
            TripPlannerSurfaceMode.CALCULATED -> "Manual trip estimate only. Distance and vehicle assumptions are entered by you; no route fact or live price is implied."
            TripPlannerSurfaceMode.UNAVAILABLE -> ""
        }

    private fun basketText(basket: TripPlannerBasketSnapshot): String =
        "Current Basket: ${basket.plannedItemCount} planned items, ${basket.privateIdentityCount} private identities, ${basket.unknownItemCount} unresolved items."

    private fun storeText(store: TripStoreSelection): String =
        buildString {
            append("Destination: ")
            append(store.displayName)
            store.addressLabel?.let { append(" · ").append(it) }
            append(" · ").append(store.sourceLabel)
        }

    private fun assumptionsText(input: com.valuepilot.core.TripCostInput): String =
        "${input.distanceBasis.name.replace('_', ' ')} ${formatDistance(input.drivingDistanceMetres)} · ${input.fuelConsumptionMilliLitresPer100Km} ml/100 km · fuel ${formatMoney(input.fuelPricePerLitre)}"

    private fun costText(result: TripCostResult): String =
        "Trip total: ${formatMoney(result.total)} · fuel ${formatMoney(result.fuelCost)} · tolls ${formatMoney(result.tolls)} · parking ${formatMoney(result.parking)}"

    private fun unknownSavingsText(): String =
        when (TripBasketEconomics().status) {
            TripBasketSavingsStatus.UNKNOWN_NO_AUTHORIZED_CURRENT_OFFERS ->
                "Basket savings: UNKNOWN — no authorized current retailer offers are connected. Trip cost is not a savings recommendation."
            TripBasketSavingsStatus.KNOWN_COMPARABLE_OFFERS -> "Basket savings available"
        }

    private data class DecisionPresentation(
        val tripCostHeadline: String,
        val tripCostDetail: String,
        val breakEvenText: String,
        val decisionHeadline: String,
        val decisionDetail: String
    )

    private fun decisionPresentation(
        decision: com.valuepilot.core.ShoppingDecisionScenarioResult?,
        result: TripCostResult?,
        basket: TripPlannerBasketSnapshot
    ): DecisionPresentation {
        val total = result?.total
        val tripCostHeadline =
            total?.let { "About ${formatMoney(it)} for this trip" }
                ?: "Trip cost not ready"
        val tripCostDetail =
            result?.let {
                "Estimate from your manual distance and vehicle assumptions · " +
                    "${formatDistance(it.effectiveDrivingDistanceMetres)} total driving"
            } ?: "Complete the destination, starting point and driving assumptions to calculate this estimate."
        val breakEvenText =
            decision?.breakEvenSavingsRequired?.let {
                "You would need at least ${formatMoney(it)} in additional Basket savings to break even."
            } ?: total?.let {
                "You would need at least ${formatMoney(it)} in additional Basket savings to break even."
            } ?: "Break-even threshold appears after the trip cost is known."
        val status = decision?.status
        val decisionHeadline =
            when (status) {
                com.valuepilot.core.ShoppingDecisionScenarioStatus.CURRENT_COMPLETE ->
                    "Current basket coverage is complete"
                com.valuepilot.core.ShoppingDecisionScenarioStatus.CURRENT_PARTIAL ->
                    "Some current prices are missing"
                com.valuepilot.core.ShoppingDecisionScenarioStatus.HISTORICAL_ONLY ->
                    "Historical context only"
                com.valuepilot.core.ShoppingDecisionScenarioStatus.TARGET_ONLY ->
                    "A target is not a current offer"
                com.valuepilot.core.ShoppingDecisionScenarioStatus.UNKNOWN,
                null -> "Not enough current evidence to recommend a store"
            }
        val decisionDetail =
            when (status) {
                com.valuepilot.core.ShoppingDecisionScenarioStatus.CURRENT_COMPLETE ->
                    "Every evaluated Basket line has a comparable current amount, but ValuePilot still does not rank stores."
                com.valuepilot.core.ShoppingDecisionScenarioStatus.CURRENT_PARTIAL ->
                    "Some current amounts are calculable, while missing, conflicted or non-comparable lines remain unknown."
                com.valuepilot.core.ShoppingDecisionScenarioStatus.HISTORICAL_ONLY ->
                    "Personal or historical records can add context, but they are not today's store prices."
                com.valuepilot.core.ShoppingDecisionScenarioStatus.TARGET_ONLY ->
                    "A private watch target can guide you, but it cannot prove a current saving."
                com.valuepilot.core.ShoppingDecisionScenarioStatus.UNKNOWN,
                null ->
                    if (basket.unknownItemCount > 0) {
                        "Current price evidence is unknown for part of this Basket; availability and savings are not inferred."
                    } else {
                        "No authorized current retailer offer is connected, so ValuePilot will not claim a cheapest store."
                    }
            }
        return DecisionPresentation(
            tripCostHeadline = tripCostHeadline,
            tripCostDetail = tripCostDetail,
            breakEvenText = breakEvenText,
            decisionHeadline = decisionHeadline,
            decisionDetail = decisionDetail
        )
    }

    private fun evidenceChips(
        decision: com.valuepilot.core.ShoppingDecisionScenarioResult?,
        basket: TripPlannerBasketSnapshot
    ): List<TripPlannerEvidenceChip> {
        if (decision == null) return unavailableEvidenceChips(basket)
        val historicalKinds = setOf(
            com.valuepilot.core.ShoppingDecisionEvidenceClass.HISTORICAL_RETAILER_OBSERVATION,
            com.valuepilot.core.ShoppingDecisionEvidenceClass.SHOPPER_CONFIRMED_PURCHASE_PRICE,
            com.valuepilot.core.ShoppingDecisionEvidenceClass.PERSONAL_PRICE_OBSERVATION
        )
        val incompatibleBlockers = setOf(
            com.valuepilot.core.ShoppingDecisionBlocker.BASKET_QUANTITY_UNKNOWN,
            com.valuepilot.core.ShoppingDecisionBlocker.OFFER_QUANTITY_UNKNOWN,
            com.valuepilot.core.ShoppingDecisionBlocker.QUANTITY_UNIT_MISMATCH,
            com.valuepilot.core.ShoppingDecisionBlocker.MULTIPLE_CURRENT_OFFERS_CONFLICT,
            com.valuepilot.core.ShoppingDecisionBlocker.CURRENCY_NOT_COMPARABLE,
            com.valuepilot.core.ShoppingDecisionBlocker.ARITHMETIC_OVERFLOW
        )
        val historical = decision.lines.count { line -> line.evidenceClasses.any(historicalKinds::contains) }
        val unknownCurrent = decision.lines.count { it.currentAmount == null } + basket.unknownItemCount
        val incompatible = decision.lines.count { line -> line.blockers.any(incompatibleBlockers::contains) }
        return listOf(
            TripPlannerEvidenceChip(
                label = "Usable current",
                count = decision.calculableCurrentLineCount,
                tone = if (decision.calculableCurrentLineCount > 0) TripPlannerEvidenceTone.POSITIVE else TripPlannerEvidenceTone.UNKNOWN
            ),
            TripPlannerEvidenceChip(
                label = "History / personal",
                count = historical,
                tone = if (historical > 0) TripPlannerEvidenceTone.NEUTRAL else TripPlannerEvidenceTone.UNKNOWN
            ),
            TripPlannerEvidenceChip(
                label = "Current unknown",
                count = unknownCurrent,
                tone = if (unknownCurrent > 0) TripPlannerEvidenceTone.UNKNOWN else TripPlannerEvidenceTone.POSITIVE
            ),
            TripPlannerEvidenceChip(
                label = "Needs review",
                count = incompatible,
                tone = if (incompatible > 0) TripPlannerEvidenceTone.WARNING else TripPlannerEvidenceTone.POSITIVE
            )
        )
    }

    private fun unavailableEvidenceChips(basket: TripPlannerBasketSnapshot): List<TripPlannerEvidenceChip> =
        listOf(
            TripPlannerEvidenceChip("Usable current", 0, TripPlannerEvidenceTone.UNKNOWN),
            TripPlannerEvidenceChip("History / personal", 0, TripPlannerEvidenceTone.NEUTRAL),
            TripPlannerEvidenceChip(
                "Current unknown",
                basket.totalVisibleItemCount,
                TripPlannerEvidenceTone.UNKNOWN
            ),
            TripPlannerEvidenceChip("Needs review", 0, TripPlannerEvidenceTone.WARNING)
        )

    private fun formatDistance(metres: Long): String =
        BigDecimal.valueOf(metres).movePointLeft(3).stripTrailingZeros().toPlainString() + " km entered"

    private fun formatMoney(money: com.valuepilot.core.Money): String =
        BigDecimal.valueOf(money.minorUnits)
            .movePointLeft(money.fractionDigits)
            .toPlainString() + " " + money.currencyCode
}
