package com.valuepilot.app

import com.valuepilot.core.ConfirmedPurchase
import com.valuepilot.core.ConfirmedPurchaseConfirmation
import com.valuepilot.core.ConfirmedPurchaseConfirmationInput
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.Money
import com.valuepilot.core.QuantityNormalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedPurchaseHistoryStoreTest {

    @Test
    fun roundTripPreservesExactFieldsAndUnknowns() {
        val first = purchase("purchase-1", quantity = true, price = true)
        val second = purchase("purchase-2", quantity = false, price = false)
        val encoded =
            requireNotNull(
                ConfirmedPurchaseHistoryCodec
                    .encode(ConfirmedPurchaseHistoryState(listOf(first, second)))
                    .bytes
            )

        val decoded = ConfirmedPurchaseHistoryCodec.decode(encoded)
        assertTrue(decoded.accepted)
        assertEquals(
            ConfirmedPurchaseHistoryState(listOf(first, second)),
            requireNotNull(decoded.state)
        )
    }

    @Test
    fun malformedAndDuplicateDocumentsFailClosed() {
        val first = purchase("purchase-1", quantity = true, price = true)
        val encoded =
            requireNotNull(
                ConfirmedPurchaseHistoryCodec
                    .encode(ConfirmedPurchaseHistoryState(listOf(first)))
                    .bytes
            )
        val duplicate = encoded.toString(Charsets.US_ASCII) + "\n" +
            encoded.toString(Charsets.US_ASCII).substringAfter('\n')
        val result = ConfirmedPurchaseHistoryCodec.decode(duplicate.toByteArray(Charsets.US_ASCII))
        assertFalse(result.accepted)

        val malformed = ConfirmedPurchaseHistoryCodec.decode("not-a-history".toByteArray())
        assertFalse(malformed.accepted)
    }

    @Test
    fun invalidUtf8InsideHexFieldsFailsClosed() {
        val malformed =
            "VALUEPILOT_CONFIRMED_PURCHASES|1\n" +
                "P|ff|6974656d|MANUAL|426164|~|~|~|~|~|~|~|~|~|~|~|~"
        assertFalse(ConfirmedPurchaseHistoryCodec.decode(malformed.toByteArray()).accepted)
    }

    @Test
    fun failedWriteDoesNotReportAcceptedMutation() {
        val storage = FakeStorage(writeAccepted = false)
        val store = AndroidConfirmedPurchaseHistoryStore(storage)
        val result = store.append(purchase("purchase-1", quantity = true, price = true))
        assertFalse(result.accepted)
        assertEquals(ConfirmedPurchaseHistoryStoreIssue.WRITE_FAILED, result.issue)
    }

    @Test
    fun failedReplacementKeepsTheLastKnownGoodDocument() {
        val storage = FakeStorage(writeAccepted = true)
        val store = AndroidConfirmedPurchaseHistoryStore(storage)
        val first = purchase("purchase-1", quantity = true, price = true)
        assertTrue(store.append(first).accepted)
        storage.writeAccepted = false

        val result = store.append(purchase("purchase-2", quantity = false, price = false))
        assertFalse(result.accepted)
        assertEquals(
            listOf(first),
            requireNotNull(store.load().state).purchases
        )
    }

    @Test
    fun appendReplaceRemoveAndRestartRemainDeterministic() {
        val storage = FakeStorage(writeAccepted = true)
        val store = AndroidConfirmedPurchaseHistoryStore(storage)
        val first = purchase("purchase-1", quantity = true, price = true)
        val second = purchase("purchase-2", quantity = false, price = false)

        assertTrue(store.append(first).accepted)
        assertTrue(store.append(second).accepted)
        assertFalse(store.append(first).accepted)

        val corrected = first.copy(amountPaid = Money.parse("7.49", "CAD"))
        assertTrue(store.replace(corrected).accepted)
        assertEquals(749L, requireNotNull(store.load().state).purchases.first { it.purchaseId == "purchase-1" }.amountPaid?.minorUnits)

        val restarted = AndroidConfirmedPurchaseHistoryStore(storage)
        assertEquals(2, requireNotNull(restarted.load().state).purchases.size)
        assertTrue(restarted.remove("purchase-2").accepted)
        assertFalse(restarted.remove("purchase-2").accepted)
        assertEquals(1, requireNotNull(restarted.load().state).purchases.size)
    }

    @Test
    fun appendKeepsTheDeterministicBoundWithoutDroppingTheNewestDatedRecord() {
        val storage = FakeStorage(writeAccepted = true)
        val store = AndroidConfirmedPurchaseHistoryStore(storage)
        repeat(MAX_CONFIRMED_PURCHASES) { index ->
            assertTrue(store.append(purchase("purchase-${index.toString().padStart(3, '0')}", quantity = false, price = false)).accepted)
        }
        val newest = purchase("purchase-newest", quantity = false, price = false).copy(purchaseDateIso = "2027-01-01")
        assertTrue(store.append(newest).accepted)
        val state = requireNotNull(store.load().state)
        assertEquals(MAX_CONFIRMED_PURCHASES, state.purchases.size)
        assertTrue(state.purchases.any { it.purchaseId == "purchase-newest" })
        assertFalse(state.purchases.any { it.purchaseId == "purchase-255" })
    }

    private fun purchase(id: String, quantity: Boolean, price: Boolean): ConfirmedPurchase =
        requireNotNull(
            ConfirmedPurchaseConfirmation.confirm(
                ConfirmedPurchaseConfirmationInput(
                    purchaseId = id,
                    productIdentityKey = "product-repeat",
                    identityOrigin = ConfirmedPurchaseIdentityOrigin.HOME_BASKET,
                    displayName = "Coffee",
                    packageQuantityLabel = "340 g",
                    quantity = if (quantity) QuantityNormalization.count(1) else null,
                    amountPaid = if (price) Money.parse("6.49", "CAD") else null,
                    purchaseDateIso = "2026-09-28",
                    explicitlyConfirmed = true
                )
            ).purchase
        )

    private class FakeStorage(
        var writeAccepted: Boolean
    ) : ConfirmedPurchaseByteStorage {
        private var bytes: ByteArray? = null

        override fun read(maxBytes: Int): ConfirmedPurchaseRawReadResult =
            bytes?.let { ConfirmedPurchaseRawReadResult(it.copyOf(), true) }
                ?: ConfirmedPurchaseRawReadResult(null, false)

        override fun replace(bytes: ByteArray): Boolean {
            if (!writeAccepted) return false
            this.bytes = bytes.copyOf()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }
}
