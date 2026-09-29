package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchaseConfirmation
import com.valuepilot.core.ConfirmedPurchaseConfirmationInput
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedPurchaseHistoryPresentationTest {

    @Test
    fun projectorKeepsPurchaseFactsSeparateFromOffersAndPriceMemory() {
        val purchase =
            requireNotNull(
                ConfirmedPurchaseConfirmation.confirm(
                    ConfirmedPurchaseConfirmationInput(
                        purchaseId = "purchase-1",
                        productIdentityKey = "item-1",
                        identityOrigin = ConfirmedPurchaseIdentityOrigin.HOME_BASKET,
                        displayName = "Bread",
                        amountPaid = Money.parse("3.49", "CAD"),
                        explicitlyConfirmed = true
                    )
                ).purchase
            )
        val projected =
            ConfirmedPurchaseHistoryProjector.project(
                ConfirmedPurchaseHistoryState(listOf(purchase)),
                unavailable = false
            )

        assertEquals(ConfirmedPurchaseHistorySurfaceMode.CONTENT, projected.mode)
        assertEquals("Bread", projected.rows.single().title)
        assertTrue(projected.rows.single().details.contains("Paid 3.49 CAD"))
        assertTrue(projected.guidance.contains("not live offers"))
    }

    @Test
    fun unavailableAndEmptyStatesDoNotExposeActions() {
        val unavailable = ConfirmedPurchaseHistoryProjector.project(null, unavailable = true)
        assertEquals(ConfirmedPurchaseHistorySurfaceMode.UNAVAILABLE, unavailable.mode)
        assertTrue(unavailable.rows.isEmpty())

        val empty = ConfirmedPurchaseHistoryProjector.project(
            ConfirmedPurchaseHistoryState.empty(),
            unavailable = false
        )
        assertEquals(ConfirmedPurchaseHistorySurfaceMode.EMPTY, empty.mode)
        assertTrue(empty.rows.isEmpty())
    }
}
