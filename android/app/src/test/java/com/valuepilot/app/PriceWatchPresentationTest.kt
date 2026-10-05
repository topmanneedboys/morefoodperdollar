package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.Money
import com.valuepilot.core.PriceObservation
import com.valuepilot.core.PriceWatch
import com.valuepilot.core.PriceWatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceWatchPresentationTest {

    @Test
    fun `same exact key joins watch purchase and personal history`() {
        val key = "gtin:000123"
        val watch = PriceWatch(key, "Coffee", packageQuantityLabel = "340 g")
        val purchase =
            ConfirmedPurchase(
                purchaseId = "purchase-1",
                productIdentityKey = key,
                identityOrigin = ConfirmedPurchaseIdentityOrigin.SEARCH_IDENTITY,
                displayName = "Coffee",
                packageQuantityLabel = "340 g",
                amountPaid = Money.parse("6.49", "CAD"),
                purchaseDateIso = "2026-09-28"
            )
        val observation =
            PriceObservation.personal(
                observationId = "observation-1",
                productIdentityKey = key,
                price = Money.parse("5.99", "CAD"),
                observedDateIso = "2026-09-29"
            )

        val state =
            PriceWatchProjector.project(
                state = PriceWatchState(watches = listOf(watch), observations = listOf(observation)),
                purchases = listOf(purchase),
                unavailable = false
            )
        val row = state.rows.single()
        assertEquals(1, row.purchaseHistory.size)
        assertEquals(1, row.personalHistory.size)
        assertEquals("No current offer", row.currentOfferText)
        assertEquals(key, row.toggleAction.productIdentityKey)
    }

    @Test
    fun `unknown values remain explicit and unavailable state hides rows`() {
        val watch = PriceWatch("provider:item-1", "Tea")
        val content =
            PriceWatchProjector.project(PriceWatchState(watches = listOf(watch)), emptyList(), false)
        assertEquals("Package quantity unknown", content.rows.single().packageText)
        assertTrue(content.rows.single().purchaseHistory.isEmpty())

        val unavailable = PriceWatchProjector.project(null, emptyList(), true)
        assertEquals(PriceWatchSurfaceMode.UNAVAILABLE, unavailable.mode)
        assertTrue(unavailable.rows.isEmpty())
    }
}
