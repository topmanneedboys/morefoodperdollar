package com.valuepilot.app

import com.valuepilot.core.Money
import com.valuepilot.core.ShoppingDecisionBlocker
import com.valuepilot.core.ShoppingDecisionScenarioResult
import com.valuepilot.core.ShoppingDecisionScenarioStatus
import com.valuepilot.core.TripCostInput
import com.valuepilot.core.TripCostCalculator
import com.valuepilot.core.TripCostResult
import com.valuepilot.core.TripDistanceBasis
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

internal enum class TripPlannerDecisionTone {
    POSITIVE,
    NEUTRAL,
    WARNING,
    UNKNOWN
}

/**
 * Consumer-ready Trip state. The projector only translates the existing trip state and the
 * already-evaluated decision result; it does not calculate, persist, or rank anything.
 */
internal data class TripPlannerSurfaceState(
    val mode: TripPlannerSurfaceMode,
    val headline: String,
    val guidance: String,
    val basketSummary: String,
    val storeSummary: String,
    val assumptionsSummary: String,
    val tripCostAmount: String,
    val tripCostSupport: String,
    val saveAtLeastAmount: String,
    val saveAtLeastSupport: String,
    val currentSavingsAmount: String,
    val currentSavingsSupport: String,
    val decisionHeadline: String,
    val decisionDetail: String,
    val decisionTone: TripPlannerDecisionTone,
    val detailLines: List<String>,
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
            basketSummary,
            storeSummary,
            assumptionsSummary,
            tripCostAmount,
            tripCostSupport,
            saveAtLeastAmount,
            saveAtLeastSupport,
            currentSavingsAmount,
            currentSavingsSupport,
            decisionHeadline,
            decisionDetail,
            basketRevision
        ).forEach { require(it.isNotBlank()) }
        require(detailLines.isNotEmpty())
        require(detailLines.size <= 8)
        require(detailLines.all { it.isNotBlank() })
        require(mode != TripPlannerSurfaceMode.CALCULATED || canCalculate)
    }
}

internal object TripPlannerProjector {
    fun project(
        state: TripPlannerState?,
        basket: TripPlannerBasketSnapshot,
        directoryAvailable: Boolean,
        unavailable: Boolean = false,
        decision: ShoppingDecisionScenarioResult? = null,
        argentinaPlan: ArgentinaBasketPlanProjection? = null
    ): TripPlannerSurfaceState {
        if (unavailable || state == null) return unavailableState(basket)

        val hasBasket = basket.totalVisibleItemCount > 0
        // The backend plan already contains the authoritative exact listed
        // prices.  Trip cost is the only value calculated here, using the
        // existing shared-core calculator and the shopper's explicit inputs.
        // No backend release timestamp is manufactured as an observation time.
        val result = decision?.tripCost
            ?: argentinaPlan?.let { state.costInputOrNull()?.let(TripCostCalculator::calculate) }
        val mode =
            when {
                !hasBasket -> TripPlannerSurfaceMode.EMPTY_BASKET
                result != null -> TripPlannerSurfaceMode.CALCULATED
                else -> TripPlannerSurfaceMode.NEEDS_INPUT
            }
        val destination = state.destination
        val input = state.costInputOrNull()
        val destinationReady = destination != null
        val originReady = state.origin != null
        val assumptionsReady = input != null
        val decisionCopy =
            argentinaPlan?.let(::argentinaDecisionCopy) ?: decisionCopy(decision)
        val costCopy = costCopy(result)
        val breakEven =
            if (argentinaPlan != null) {
                // Without a second current store scenario there is no savings
                // claim.  Trip cost remains the input needed to evaluate one.
                null
            } else {
                decision?.breakEvenSavingsRequired
            }

        return TripPlannerSurfaceState(
            mode = mode,
            headline = destination?.let { "Trip to ${it.displayName}" } ?: "Plan your trip",
            guidance = guidance(mode, destinationReady, originReady, assumptionsReady, directoryAvailable),
            basketSummary = basketSummary(basket),
            storeSummary = destination?.let { "Store · ${it.displayName}" } ?: "Store · not selected",
            assumptionsSummary = input?.let(::assumptionsSummary) ?: "Trip details · not complete",
            tripCostAmount = costCopy.amount,
            tripCostSupport = costCopy.support,
            saveAtLeastAmount = breakEven?.let(::formatMoney) ?: "Unknown",
            saveAtLeastSupport = if (breakEven != null) {
                "Another store would need to save more than this trip cost."
            } else {
                "Trip cost needed first."
            },
            currentSavingsAmount = "Unknown",
            currentSavingsSupport =
                if (argentinaPlan != null) {
                    "A second current store plan is needed to calculate savings."
                } else {
                    currentSavingsSupport(decision)
                },
            decisionHeadline = decisionCopy.headline,
            decisionDetail = decisionCopy.detail,
            decisionTone = decisionCopy.tone,
            detailLines = detailLines(state, basket, result, decision, argentinaPlan),
            destinationReady = destinationReady,
            originReady = originReady,
            assumptionsReady = assumptionsReady,
            directionsAvailable = destination?.let { it.latitudeE7 != null && it.longitudeE7 != null } == true,
            canCalculate = result != null,
            basketRevision = basket.revision
        )
    }

    private fun unavailableState(basket: TripPlannerBasketSnapshot): TripPlannerSurfaceState =
        TripPlannerSurfaceState(
            mode = TripPlannerSurfaceMode.UNAVAILABLE,
            headline = "Trip unavailable",
            guidance = "Your saved trip details could not be read safely. Nothing was replaced.",
            basketSummary = basketSummary(basket),
            storeSummary = "Store · unavailable",
            assumptionsSummary = "Trip details · unavailable",
            tripCostAmount = "Unknown",
            tripCostSupport = "Add trip details to estimate the cost.",
            saveAtLeastAmount = "Unknown",
            saveAtLeastSupport = "Trip cost needed first.",
            currentSavingsAmount = "Unknown",
            currentSavingsSupport = "Not enough current prices.",
            decisionHeadline = "Need more current prices",
            decisionDetail = "Trip details are unavailable, so ValuePilot cannot make a stronger comparison.",
            decisionTone = TripPlannerDecisionTone.UNKNOWN,
            detailLines = listOf(
                "Your saved trip details could not be read safely.",
                "Existing data was preserved."
            ),
            destinationReady = false,
            originReady = false,
            assumptionsReady = false,
            directionsAvailable = false,
            canCalculate = false,
            basketRevision = basket.revision
        )

    private fun guidance(
        mode: TripPlannerSurfaceMode,
        destinationReady: Boolean,
        originReady: Boolean,
        assumptionsReady: Boolean,
        directoryAvailable: Boolean
    ): String =
        when (mode) {
            TripPlannerSurfaceMode.EMPTY_BASKET -> "Add items in Basket first."
            TripPlannerSurfaceMode.CALCULATED -> "Distance and vehicle details come from you; this is not a road route."
            TripPlannerSurfaceMode.UNAVAILABLE -> ""
            TripPlannerSurfaceMode.NEEDS_INPUT ->
                when {
                    !destinationReady && directoryAvailable -> "Choose a store to see the trip cost."
                    !destinationReady -> "Choose a store to see the trip cost."
                    !originReady -> "Add a starting point to keep this estimate personal."
                    !assumptionsReady -> "Add trip details to estimate fuel and other costs."
                    else -> "Complete the trip details to see the estimate."
                }
        }

    private fun basketSummary(basket: TripPlannerBasketSnapshot): String =
        "Basket · ${basket.totalVisibleItemCount} ${if (basket.totalVisibleItemCount == 1) "item" else "items"}"

    private data class CostCopy(val amount: String, val support: String)

    private fun costCopy(result: TripCostResult?): CostCopy =
        result?.let {
            val components = buildList {
                add("Fuel")
                if (it.tolls.minorUnits > 0L) add("tolls")
                if (it.parking.minorUnits > 0L) add("parking")
            }
            CostCopy(formatMoney(it.total), components.joinToString(" + "))
        } ?: CostCopy("Unknown", "Add trip details to estimate the cost.")

    private data class DecisionCopy(
        val headline: String,
        val detail: String,
        val tone: TripPlannerDecisionTone
    )

    private fun argentinaDecisionCopy(plan: ArgentinaBasketPlanProjection): DecisionCopy =
        when (plan.status) {
            ArgentinaBasketPlanStatus.CURRENT_COMPLETE ->
                DecisionCopy(
                    headline = "Current listed prices available",
                    detail = "${plan.coverageText}. This plan does not establish live shelf inventory or rank another store.",
                    tone = TripPlannerDecisionTone.POSITIVE
                )
            ArgentinaBasketPlanStatus.CURRENT_PARTIAL ->
                DecisionCopy(
                    headline = "Current price coverage is incomplete",
                    detail = "${plan.coverageText}. Missing prices stay unknown; ValuePilot will not call this a complete basket.",
                    tone = TripPlannerDecisionTone.WARNING
                )
            ArgentinaBasketPlanStatus.NO_CURRENT_PLAN ->
                DecisionCopy(
                    headline = "Need more current prices",
                    detail = "No exact current price plan was returned for this Basket. Availability remains unknown.",
                    tone = TripPlannerDecisionTone.UNKNOWN
                )
        }

    private fun decisionCopy(decision: ShoppingDecisionScenarioResult?): DecisionCopy {
        if (decision == null) {
            return DecisionCopy(
                headline = "Need more current prices",
                detail = "Complete the trip details and ValuePilot will show what the available price evidence supports.",
                tone = TripPlannerDecisionTone.UNKNOWN
            )
        }
        val conflict = decision.lines.any {
            ShoppingDecisionBlocker.MULTIPLE_CURRENT_OFFERS_CONFLICT in it.blockers
        }
        if (conflict) {
            return DecisionCopy(
                headline = "Prices conflict",
                detail = "The available prices disagree, so ValuePilot will not force a winner.",
                tone = TripPlannerDecisionTone.WARNING
            )
        }
        val stale = decision.lines.any {
            ShoppingDecisionBlocker.CURRENT_OFFER_NOT_FRESH in it.blockers
        }
        if (stale) {
            return DecisionCopy(
                headline = "Need more current prices",
                detail = "Some available prices are out of date, so they are not used as current savings.",
                tone = TripPlannerDecisionTone.UNKNOWN
            )
        }
        return when (decision.status) {
            ShoppingDecisionScenarioStatus.CURRENT_COMPLETE ->
                DecisionCopy(
                    headline = "Current prices available",
                    detail = "This store has complete current amounts for the Basket. Savings still need a current comparison.",
                    tone = TripPlannerDecisionTone.POSITIVE
                )
            ShoppingDecisionScenarioStatus.CURRENT_PARTIAL,
            ShoppingDecisionScenarioStatus.HISTORICAL_ONLY,
            ShoppingDecisionScenarioStatus.TARGET_ONLY,
            ShoppingDecisionScenarioStatus.UNKNOWN ->
                DecisionCopy(
                    headline = "Need more current prices",
                    detail = when (decision.status) {
                        ShoppingDecisionScenarioStatus.HISTORICAL_ONLY ->
                            "Past or personal prices add context, but they are not today's store prices."
                        ShoppingDecisionScenarioStatus.TARGET_ONLY ->
                            "A target price is useful for watching, but it is not a current store price."
                        ShoppingDecisionScenarioStatus.CURRENT_PARTIAL ->
                            "Some Basket prices are current, but others are still missing."
                        else -> "Not enough current price evidence is available for a reliable comparison."
                    },
                    tone = TripPlannerDecisionTone.UNKNOWN
                )
        }
    }

    private fun currentSavingsSupport(decision: ShoppingDecisionScenarioResult?): String =
        when {
            decision == null -> "Not enough current prices."
            decision.lines.any { ShoppingDecisionBlocker.MULTIPLE_CURRENT_OFFERS_CONFLICT in it.blockers } -> "Prices conflict."
            decision.status == ShoppingDecisionScenarioStatus.CURRENT_COMPLETE ->
                "A second current store is needed to calculate savings."
            decision.status == ShoppingDecisionScenarioStatus.HISTORICAL_ONLY ||
                decision.status == ShoppingDecisionScenarioStatus.TARGET_ONLY ->
                "Only past or target prices are available."
            else -> "Not enough current prices."
        }

    private fun detailLines(
        state: TripPlannerState,
        basket: TripPlannerBasketSnapshot,
        result: TripCostResult?,
        decision: ShoppingDecisionScenarioResult?,
        argentinaPlan: ArgentinaBasketPlanProjection? = null
    ): List<String> = buildList {
        add(basketSummary(basket))
        add(
            state.destination?.let { destination ->
                buildString {
                    append("Store · ").append(destination.displayName)
                    destination.addressLabel?.let { append(" · ").append(it) }
                }
            } ?: "Store · not selected"
        )
        add(
            state.origin?.let { origin ->
                if (origin.kind.name == "FOREGROUND_LOCATION") {
                    "Starting point · one foreground location reading"
                } else {
                    "Starting point · ${origin.label} · entered manually"
                }
            } ?: "Starting point · not added"
        )
        add(state.costInputOrNull()?.let(::assumptionsSummary) ?: "Trip details · not complete")
        result?.let { add("Estimated total · ${formatMoney(it.total)}") }
        argentinaPlan?.let { plan ->
            add(plan.headline)
            plan.totalText?.let { add(it) }
            val trip = result?.total
            val merchandise = plan.merchandiseTotal
            if (trip != null && merchandise != null && sameMoneySpec(trip, merchandise)) {
                val totalLabel = when (plan.status) {
                    ArgentinaBasketPlanStatus.CURRENT_COMPLETE -> "Estimated shopping total"
                    ArgentinaBasketPlanStatus.CURRENT_PARTIAL -> "Estimated total for known prices"
                    ArgentinaBasketPlanStatus.NO_CURRENT_PLAN -> "Estimated shopping total"
                }
                add("$totalLabel · ${formatMoney(merchandise + trip)}")
            } else if (merchandise != null) {
                add("Products · ${formatMoney(merchandise)}")
                val totalLabel = when (plan.status) {
                    ArgentinaBasketPlanStatus.CURRENT_COMPLETE -> "Estimated shopping total"
                    ArgentinaBasketPlanStatus.CURRENT_PARTIAL -> "Known-price total"
                    ArgentinaBasketPlanStatus.NO_CURRENT_PLAN -> "Estimated shopping total"
                }
                add("$totalLabel · Unknown until trip currency matches")
            }
            plan.evaluatedPlanSummary?.let { add("Evaluated plans · $it") }
            add(plan.coverageText)
            add("Published prices only · availability unknown")
        }
        decision?.let { add(currentSavingsSupport(it)) }
    }.take(8)

    private fun assumptionsSummary(input: TripCostInput): String {
        val distance = BigDecimal.valueOf(input.drivingDistanceMetres)
            .movePointLeft(3)
            .stripTrailingZeros()
            .toPlainString()
        val consumption = BigDecimal.valueOf(input.fuelConsumptionMilliLitresPer100Km)
            .movePointLeft(3)
            .stripTrailingZeros()
            .toPlainString()
        val basis = when (input.distanceBasis) {
            TripDistanceBasis.ROUND_TRIP -> "one-way distance · round trip"
            TripDistanceBasis.ADDITIONAL_DRIVING -> "additional driving"
        }
        return "Trip details · $distance km $basis · $consumption L/100 km · ${formatMoney(input.fuelPricePerLitre)}/L"
    }

    private fun formatMoney(money: Money): String =
        "${money.currencyCode} " + BigDecimal.valueOf(money.minorUnits)
            .movePointLeft(money.fractionDigits)
            .setScale(money.fractionDigits)
            .toPlainString()

    private fun sameMoneySpec(left: Money, right: Money): Boolean =
        left.currencyCode == right.currencyCode && left.fractionDigits == right.fractionDigits
}
