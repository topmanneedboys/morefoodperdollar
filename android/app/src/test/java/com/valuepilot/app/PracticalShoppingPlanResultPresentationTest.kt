package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticalShoppingPlanResultPresentationTest {

    @Test
    fun completeBasketKeepsConfidentGreenTreatment() {
        val style = practicalShoppingPrimaryCardStyle(primary(missingItemsText = null))

        assertEquals("#ECFDF5", style.backgroundColor)
        assertEquals("#A7F3D0", style.strokeColor)
        assertEquals("#047857", style.accentColor)
    }

    @Test
    fun knownSubtotalUsesCautionTreatmentWithoutChangingProjectedCopy() {
        val style =
            practicalShoppingPrimaryCardStyle(
                primary(missingItemsText = "Missing price: Coffee")
            )

        assertEquals("#FFFBEB", style.backgroundColor)
        assertEquals("#FDE68A", style.strokeColor)
        assertEquals("#92400E", style.accentColor)
    }

    private fun primary(missingItemsText: String?): PracticalShoppingPrimaryUiState =
        PracticalShoppingPrimaryUiState(
            badge = "BEST ONE-STORE OPTION",
            storeName = "Sample Market",
            basketCostText = "Basket 10.00 CAD",
            coverageText = "2 of 2 items priced",
            missingItemsText = missingItemsText,
            travelText = "3 min · 1 km",
            evidenceText = "Price freshness: 2 fresh · 0 stale · 0 unknown",
            whyText = "Lowest known complete basket among the one-store options compared.",
            notice = missingItemsText?.let { "This is not a complete basket total." }
        )
}
