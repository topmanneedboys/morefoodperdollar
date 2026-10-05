package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchaseConfirmation
import com.valuepilot.core.ConfirmedPurchaseConfirmationInput
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.Money
import com.valuepilot.core.QuantityNormalization
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
                        quantity = QuantityNormalization.count(1),
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
        assertTrue(projected.rows.single().details.contains("Quantity 1 count"))
        assertTrue(projected.rows.single().details.contains("Paid 3.49 CAD"))
        assertTrue(projected.guidance.contains("Not live offers"))
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

    @Test
    fun `mixed currencies stay explicit in personal memory`() {
        val cad = ConfirmedPurchase(
            purchaseId = "purchase-cad",
            productIdentityKey = "item-cad",
            identityOrigin = ConfirmedPurchaseIdentityOrigin.SEARCH_IDENTITY,
            displayName = "Coffee",
            amountPaid = Money.parse("6.49", "CAD"),
            purchaseDateIso = "2026-09-28"
        )
        val ars = ConfirmedPurchase(
            purchaseId = "purchase-ars",
            productIdentityKey = "item-ars",
            identityOrigin = ConfirmedPurchaseIdentityOrigin.SEARCH_IDENTITY,
            displayName = "Yerba mate",
            amountPaid = Money.parse("1499", "ARS"),
            purchaseDateIso = "2026-09-29"
        )

        val projected = ConfirmedPurchaseHistoryProjector.project(
            ConfirmedPurchaseHistoryState(listOf(cad, ars)),
            unavailable = false
        )

        assertTrue(projected.rows.any { it.details.contains("6.49 CAD") })
        assertTrue(projected.rows.any { it.details.contains("1499.00 ARS") })
    }
}
