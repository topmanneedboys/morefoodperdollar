package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.Money
import com.valuepilot.core.PriceWatchState
import com.valuepilot.core.ShoppingDecisionBasketLine
import com.valuepilot.core.ShoppingDecisionEngine
import com.valuepilot.core.ShoppingDecisionInput
import com.valuepilot.core.ShoppingDecisionScenarioResult
import com.valuepilot.core.ShoppingDecisionScenarioStatus
import com.valuepilot.core.ShoppingDecisionStoreScenario
import com.valuepilot.core.ShoppingItemKey
import java.math.BigDecimal

/**
 * Small application adapter that re-evaluates the current Home/Basket facts when Trip Planner is
 * rendered. It supplies no live offers, never stores a second scenario document, and keeps the
 * shared-core engine as the only authority for evidence arithmetic.
 */
internal object ShoppingDecisionTripPlannerBridge {

    fun evaluate(
        home: PracticalShoppingHomeSession.State,
        trip: TripPlannerState,
        purchases: List<ConfirmedPurchase>,
        priceWatchState: PriceWatchState
    ): ShoppingDecisionScenarioResult? {
        val destination = trip.destination ?: return null
        val linesByKey = LinkedHashMap<String, ShoppingDecisionBasketLine>()
        home.model.ui.items.forEach { item ->
            if (!linesByKey.containsKey(item.key.value)) {
                linesByKey[item.key.value] =
                    ShoppingDecisionBasketLine(
                        lineId = "planned:${item.key.value}",
                        productIdentityKey = item.key,
                        displayName = item.name,
                        requestedQuantity = item.quantity
                    )
            }
        }
        home.privateIdentities
            .filter { it.identityKey in home.privateBasketCollectedIdentityKeys }
            .forEach { identity ->
                val key = ShoppingItemKey(identity.identityKey)
                if (!linesByKey.containsKey(identity.identityKey)) {
                    linesByKey[identity.identityKey] =
                        ShoppingDecisionBasketLine(
                            lineId = "private:${identity.identityKey}",
                            productIdentityKey = key,
                            displayName = identity.displayName,
                            requestedQuantity = null
                        )
                }
            }
        if (linesByKey.isEmpty()) return null

        return ShoppingDecisionEngine
            .evaluate(
                ShoppingDecisionInput(
                    basket = linesByKey.values.toList(),
                    stores =
                        listOf(
                            ShoppingDecisionStoreScenario(
                                storeKey = destination.storeKey,
                                tripCostInput = trip.costInputOrNull()
                            )
                        ),
                    // No current retailer source is enabled in this milestone.
                    currentOffers = emptyList(),
                    historicalRetailerObservations = emptyList(),
                    confirmedPurchases = purchases,
                    priceWatchState = priceWatchState
                )
            )
            .scenarios
            .single()
    }

    fun summary(result: ShoppingDecisionScenarioResult): String {
        val body =
            when (result.status) {
                ShoppingDecisionScenarioStatus.CURRENT_COMPLETE ->
                    "Decision evidence: ${formatMoney(requireNotNull(result.currentBasketTotal))} current basket total; no store ranking is claimed."
                ShoppingDecisionScenarioStatus.CURRENT_PARTIAL ->
                    "Decision evidence: ${result.calculableCurrentLineCount}/${result.lines.size} current amounts are calculable; missing lines remain unknown."
                ShoppingDecisionScenarioStatus.HISTORICAL_ONLY ->
                    "Decision evidence: historical/personal context only, not today's store price."
                ShoppingDecisionScenarioStatus.TARGET_ONLY ->
                    "Decision evidence: a watch target exists, but no current authorized offer is available."
                ShoppingDecisionScenarioStatus.UNKNOWN ->
                    "Decision evidence: current price coverage is unknown; no authorized live offer is connected."
            }
        val breakEven =
            result.breakEvenSavingsRequired?.let {
                " Break-even additional savings required: ${formatMoney(it)} (estimated trip cost)."
            }.orEmpty()
        return body + breakEven
    }

    private fun formatMoney(money: Money): String =
        BigDecimal.valueOf(money.minorUnits)
            .movePointLeft(money.fractionDigits)
            .toPlainString() + " " + money.currencyCode
}
