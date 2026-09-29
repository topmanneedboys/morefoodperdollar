package com.valuepilot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedPurchaseTest {

    @Test
    fun explicitConfirmationKeepsExactMoneyQuantityAndKnownFields() {
        val result = ConfirmedPurchaseConfirmation.confirm(
            ConfirmedPurchaseConfirmationInput(
                purchaseId = "purchase-1",
                productIdentityKey = "gtin:012345678905",
                identityOrigin = ConfirmedPurchaseIdentityOrigin.SEARCH_IDENTITY,
                displayName = "Whole milk",
                brand = "Example",
                packageQuantityLabel = "4 L",
                quantity = QuantityNormalization.count(2),
                amountPaid = Money.parse("6.49", "CAD"),
                purchaseDateIso = "2026-09-28",
                store = ConfirmedPurchaseStore(displayName = "Example Market"),
                explicitlyConfirmed = true
            )
        )

        assertTrue(result.accepted)
        val purchase = requireNotNull(result.purchase)
        assertEquals(649L, purchase.amountPaid?.minorUnits)
        assertEquals(2_000_000L, purchase.quantity?.amountMicros)
        assertEquals("2026-09-28", purchase.purchaseDateIso)
        assertEquals("Example Market", purchase.store?.displayName)
    }

    @Test
    fun purchaseCannotBeMintedWithoutExplicitConfirmation() {
        val result = ConfirmedPurchaseConfirmation.confirm(
            ConfirmedPurchaseConfirmationInput(
                purchaseId = "purchase-1",
                productIdentityKey = "item-1",
                identityOrigin = ConfirmedPurchaseIdentityOrigin.HOME_BASKET,
                displayName = "Bread",
                explicitlyConfirmed = false
            )
        )

        assertFalse(result.accepted)
        assertTrue(
            ConfirmedPurchaseConfirmationFailure.EXPLICIT_CONFIRMATION_REQUIRED in result.failures
        )
    }

    @Test
    fun unknownFactsRemainUnknownAndMalformedDatesAreRejected() {
        val accepted = ConfirmedPurchaseConfirmation.confirm(
            ConfirmedPurchaseConfirmationInput(
                purchaseId = "purchase-unknown",
                productIdentityKey = "item-unknown",
                identityOrigin = ConfirmedPurchaseIdentityOrigin.MANUAL,
                displayName = "Unknown details",
                quantity = null,
                amountPaid = null,
                purchaseDateIso = null,
                store = null,
                explicitlyConfirmed = true
            )
        )
        assertTrue(accepted.accepted)
        assertNull(accepted.purchase?.quantity)
        assertNull(accepted.purchase?.amountPaid)
        assertNull(accepted.purchase?.purchaseDateIso)
        assertNull(accepted.purchase?.store)

        val malformed = ConfirmedPurchaseConfirmation.confirm(
            ConfirmedPurchaseConfirmationInput(
                purchaseId = "purchase-bad-date",
                productIdentityKey = "item-1",
                identityOrigin = ConfirmedPurchaseIdentityOrigin.MANUAL,
                displayName = "Bad date",
                purchaseDateIso = "2026-02-30",
                explicitlyConfirmed = true
            )
        )
        assertFalse(malformed.accepted)
        assertTrue(ConfirmedPurchaseConfirmationFailure.INVALID_RECORD in malformed.failures)
    }

    @Test
    fun repeatedPurchasesMayShareIdentityButRemainDistinctRecords() {
        val first = purchase("p-1")
        val second = purchase("p-2")

        assertTrue(first.hasSameProductIdentity(second))
        assertFalse(first.purchaseId == second.purchaseId)
    }

    private fun purchase(id: String): ConfirmedPurchase =
        requireNotNull(
            ConfirmedPurchaseConfirmation.confirm(
                ConfirmedPurchaseConfirmationInput(
                    purchaseId = id,
                    productIdentityKey = "item-repeat",
                    identityOrigin = ConfirmedPurchaseIdentityOrigin.HOME_BASKET,
                    displayName = "Coffee",
                    explicitlyConfirmed = true
                )
            ).purchase
        )
}
