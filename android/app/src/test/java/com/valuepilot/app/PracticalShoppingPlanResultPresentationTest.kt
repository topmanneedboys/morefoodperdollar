package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun completeBasketWithNonFreshEvidenceUsesCautionTreatmentAndExplainsWhy() {
        val state =
            primary(missingItemsText = null).copy(
                hasPriceFreshnessCaution = true,
                freshnessNotice = "Some price evidence is not fully fresh. Verify before buying.",
                evidenceText = "Price freshness: 1 fresh · 1 aging · 0 stale · 0 unknown"
            )

        val style = practicalShoppingPrimaryCardStyle(state)

        assertEquals("#FFFBEB", style.backgroundColor)
        assertEquals("#FDE68A", style.strokeColor)
        assertEquals("#92400E", style.accentColor)
        assertEquals(
            "BEST ONE-STORE OPTION. Store: Sample Market. Basket 10.00 CAD. " +
                "2 of 2 items priced. 3 min · 1 km. Price freshness: 1 fresh · 1 aging · 0 stale · 0 unknown. " +
                "Lowest known complete basket among the one-store options compared. " +
                "Some price evidence is not fully fresh. Verify before buying.",
            practicalShoppingPrimaryCardContentDescription(state)
        )
    }

    @Test
    fun primaryCardAccessibilitySummaryIncludesEveryProjectedStateAndNotice() {
        assertEquals(
            "BEST ONE-STORE OPTION. Store: Sample Market. Basket 10.00 CAD. " +
                "2 of 2 items priced. 3 min · 1 km. Price freshness: 2 fresh · 0 stale · 0 unknown. " +
                "Lowest known complete basket among the one-store options compared.",
            practicalShoppingPrimaryCardContentDescription(primary(null))
        )

        val incomplete = primary("Missing price: Coffee")
        assertEquals(
            "BEST COVERAGE FOUND. Store: Sample Market. Known subtotal 10.00 CAD. " +
                "1 of 2 items priced. Missing price: Coffee. 3 min · 1 km. " +
                "Price freshness: 2 fresh · 0 stale · 0 unknown. " +
                "No complete basket is priced yet; this option covers the most requested items. " +
                "This is not a complete basket total.",
            practicalShoppingPrimaryCardContentDescription(incomplete)
        )
    }

    @Test
    fun secondStopAccessibilitySummaryIncludesAllocationSavingsTravelAndEvidence() {
        val state =
            PracticalShoppingSecondStopUiState(
                badge = "OPTIONAL EXTRA STOP",
                storeName = "Example Grocer",
                baseItemsText = "Buy at Sample Market: Eggs",
                addedItemsText = "Then buy at Example Grocer: Milk",
                combinedBasketCostText = "Combined basket 20.00 CAD",
                savingsText = "Could save 3.00 CAD",
                additionalTravelText = "Adds 4 min · 2 km",
                evidenceText = "Price freshness: 2 fresh · 0 stale · 0 unknown"
            )

        assertEquals(
            "OPTIONAL EXTRA STOP. Store: Example Grocer. Buy at Sample Market: Eggs. " +
                "Then buy at Example Grocer: Milk. Combined basket 20.00 CAD. Could save 3.00 CAD. " +
                "Adds 4 min · 2 km. Price freshness: 2 fresh · 0 stale · 0 unknown.",
            practicalShoppingSecondStopCardContentDescription(state)
        )
    }

    @Test
    fun sampleDisclosureIsIncludedWhenTheOwningSurfaceProvidesIt() {
        val notice = "Fictional sample data only — not live retailer prices or availability."

        assertEquals(
            "BEST ONE-STORE OPTION. Store: Sample Market. Basket 10.00 CAD. " +
                "2 of 2 items priced. 3 min · 1 km. Price freshness: 2 fresh · 0 stale · 0 unknown. " +
                "Lowest known complete basket among the one-store options compared. " +
                "Fictional sample data only — not live retailer prices or availability.",
            practicalShoppingPrimaryCardContentDescription(primary(null), notice)
        )

        val secondStop =
            PracticalShoppingSecondStopUiState(
                badge = "OPTIONAL EXTRA STOP",
                storeName = "Example Grocer",
                baseItemsText = "Buy at Sample Market: Eggs",
                addedItemsText = "Then buy at Example Grocer: Milk",
                combinedBasketCostText = "Combined basket 20.00 CAD",
                savingsText = "Could save 3.00 CAD",
                additionalTravelText = "Adds 4 min · 2 km",
                evidenceText = "Price freshness: 2 fresh · 0 stale · 0 unknown"
            )
        assertEquals(
            "OPTIONAL EXTRA STOP. Store: Example Grocer. Buy at Sample Market: Eggs. " +
                "Then buy at Example Grocer: Milk. Combined basket 20.00 CAD. Could save 3.00 CAD. " +
                "Adds 4 min · 2 km. Price freshness: 2 fresh · 0 stale · 0 unknown. " +
                "Fictional sample data only — not live retailer prices or availability.",
            practicalShoppingSecondStopCardContentDescription(secondStop, notice)
        )
    }

    @Test
    fun blankSampleDisclosureFailsClosedInsteadOfCreatingAnEmptySummarySegment() {
        assertThrows(IllegalArgumentException::class.java) {
            practicalShoppingPrimaryCardContentDescription(primary(null), " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            practicalShoppingSecondStopCardContentDescription(
                PracticalShoppingSecondStopUiState(
                    badge = "OPTIONAL EXTRA STOP",
                    storeName = "Example Grocer",
                    baseItemsText = "Buy at Sample Market: Eggs",
                    addedItemsText = "Then buy at Example Grocer: Milk",
                    combinedBasketCostText = "Combined basket 20.00 CAD",
                    savingsText = "Could save 3.00 CAD",
                    additionalTravelText = "Adds 4 min · 2 km",
                    evidenceText = "Price freshness: 2 fresh · 0 stale · 0 unknown"
                ),
                ""
            )
        }
    }

    private fun primary(missingItemsText: String?): PracticalShoppingPrimaryUiState =
        PracticalShoppingPrimaryUiState(
            badge = if (missingItemsText == null) "BEST ONE-STORE OPTION" else "BEST COVERAGE FOUND",
            storeName = "Sample Market",
            basketCostText =
                if (missingItemsText == null) "Basket 10.00 CAD"
                else "Known subtotal 10.00 CAD",
            coverageText = if (missingItemsText == null) "2 of 2 items priced" else "1 of 2 items priced",
            missingItemsText = missingItemsText,
            travelText = "3 min · 1 km",
            evidenceText = "Price freshness: 2 fresh · 0 stale · 0 unknown",
            whyText =
                if (missingItemsText == null) {
                    "Lowest known complete basket among the one-store options compared."
                } else {
                    "No complete basket is priced yet; this option covers the most requested items."
                },
            notice = missingItemsText?.let { "This is not a complete basket total." }
        )
}
