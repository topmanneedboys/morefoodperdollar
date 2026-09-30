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
            basketRevision
        ).forEach { require(it.isNotBlank()) }
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

    private fun formatDistance(metres: Long): String =
        BigDecimal.valueOf(metres).movePointLeft(3).stripTrailingZeros().toPlainString() + " km entered"

    private fun formatMoney(money: com.valuepilot.core.Money): String =
        BigDecimal.valueOf(money.minorUnits)
            .movePointLeft(money.fractionDigits)
            .toPlainString() + " " + money.currencyCode
}
