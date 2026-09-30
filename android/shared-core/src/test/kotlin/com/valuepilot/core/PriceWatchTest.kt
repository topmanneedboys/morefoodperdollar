package com.valuepilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PriceWatchTest {

    @Test
    fun `watch key is the exact deduplication identity`() {
        val first = PriceWatch("gtin:000123", "Coffee", packageQuantityLabel = "340 g")
        val replacement = first.copy(targetPrice = Money.parse("5.49", "CAD"))
        val state = PriceWatchState(watches = listOf(replacement))

        assertEquals("gtin:000123", state.watches.single().productIdentityKey)
        assertEquals(549L, state.watches.single().targetPrice?.minorUnits)
        assertTrue(
            PriceObservation.personal(
                observationId = "observation-1",
                productIdentityKey = "gtin:000123",
                price = Money.parse("6.49", "CAD"),
                observedDateIso = "2026-09-29"
            ).matchesExactProduct("gtin:000123")
        )
    }

    @Test
    fun `personal observation retains exact fields and unknowns`() {
        val observation =
            PriceObservation.personal(
                observationId = "observation-1",
                productIdentityKey = "provider:item-1",
                packageQuantity = NormalizedQuantity(340_000_000L, BaseUnit.GRAM),
                price = Money.parse("6.49", "CAD"),
                observedDateIso = "2026-09-29",
                store = ConfirmedPurchaseStore(displayName = "Local market")
            )

        assertEquals(340_000_000L, observation.packageQuantity?.amountMicros)
        assertEquals("CAD", observation.price.currencyCode)
        assertEquals("Local market", observation.store?.displayName)
        assertEquals(PriceObservationSourceKind.PERSONAL_ENTRY, observation.provenance.sourceKind)
        assertEquals(PriceObservationPermission.PRIVATE_DEVICE_ONLY, observation.provenance.permission)
        assertEquals(PriceObservationFreshness.UNKNOWN, observation.freshness)
    }

    @Test
    fun `date-less personal observation stays freshness unknown`() {
        val observation =
            PriceObservation.personal(
                observationId = "observation-2",
                productIdentityKey = "provider:item-2",
                price = Money.parse("4.00", "CAD"),
                observedDateIso = "   "
            )

        assertEquals(null, observation.observedDateIso)
        assertEquals(PriceObservationFreshness.UNKNOWN, observation.freshness)
    }

    @Test
    fun `unapproved authorized provenance cannot be represented`() {
        assertFailsWith<IllegalArgumentException> {
            PriceObservationProvenance(
                sourceId = "unapproved",
                sourceKind = PriceObservationSourceKind.AUTHORIZED_RETAILER_OFFER,
                permission = PriceObservationPermission.PRIVATE_DEVICE_ONLY
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PriceObservationProvenance(
                sourceId = "unqualified-personal-source",
                sourceKind = PriceObservationSourceKind.PERSONAL_ENTRY,
                permission = PriceObservationPermission.PRIVATE_DEVICE_ONLY
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PriceObservationProvenance(
                sourceId = "shopper-entered-v1",
                sourceKind = PriceObservationSourceKind.PERSONAL_ENTRY,
                rightsRecordId = "invented-rights",
                permission = PriceObservationPermission.PRIVATE_DEVICE_ONLY
            )
        }
    }

    @Test
    fun `unknown quantity is not invented and mixed money remains explicit`() {
        val observation =
            PriceObservation.personal(
                observationId = "observation-3",
                productIdentityKey = "gtin:000123",
                price = Money.parse("5.00", "USD")
            )
        assertEquals(null, observation.packageQuantity)
        assertEquals("USD", observation.price.currencyCode)
        assertFalse(observation.matchesExactProduct("gtin:000999"))
        assertNotNull(observation.provenance)
    }
}
