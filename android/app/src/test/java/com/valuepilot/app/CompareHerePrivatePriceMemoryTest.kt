package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.CompareHereExactCandidate
import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId
import com.valuepilot.core.RateUnit
import com.valuepilot.core.UnitRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHerePrivatePriceMemoryTest {

    @Test
    fun `ready confirmed comparison produces exact local memory with promotion facts`() {
        val result =
            CompareHereManualComparisonService.compare(
                comparisonIntentKey = CompareHereComparisonIntentKey("intent:eggs"),
                priceSelection = CompareHerePriceSelection.CURRENT,
                observations =
                    capture(
                        "Promo Eggs\nCA$6.00\n12 count\nBuy one get one free",
                        "Regular Eggs\nCA$8.00\n12 count"
                    )
            )
        assertTrue(result is CompareHereManualComparisonResult.Success)

        val success = result as CompareHereManualComparisonResult.Success
        val capture =
            requireNotNull(
                CompareHerePrivatePriceMemoryAssembler.from(
                    success = success,
                    observedAtEpochMillis = 42L
                )
            )

        assertEquals(2, capture.entries.size)
        val promo = capture.entries.single { it.displayName == "Promo Eggs" }
        assertEquals(6_00L, promo.price.minorUnits)
        assertEquals(NormalizedQuantity(12_000_000L, BaseUnit.COUNT), promo.quantity)
        assertEquals(RateUnit.ITEM, promo.rate.unit)
        assertEquals("Buy 1, get 1", promo.promotionLabel)
        assertEquals(2L, promo.promotionReceivedUnits)
        assertEquals(1L, promo.promotionPaidUnits)
        assertEquals(42L, promo.observedAtEpochMillis)
        assertEquals(
            CompareHerePrivatePriceMemorySource.CONFIRMED_COMPARE_HERE,
            promo.source
        )
        assertEquals(64, promo.observationId.length)
    }

    @Test
    fun `blocked or incomplete comparison never creates private memory`() {
        val result =
            CompareHereManualComparisonService.compare(
                comparisonIntentKey = CompareHereComparisonIntentKey("intent:milk"),
                priceSelection = CompareHerePriceSelection.CURRENT,
                observations =
                    capture(
                        "Known Milk\nCA$4.00\n500 g",
                        "Range Milk\nCA$5.00\n500-700 g"
                    )
            )
        val success = result as CompareHereManualComparisonResult.Success
        assertEquals(CompareHereUiStatus.NOT_ENOUGH_DATA, success.projection.state.status)
        assertNull(
            CompareHerePrivatePriceMemoryAssembler.from(
                success = success,
                observedAtEpochMillis = 42L
            )
        )
    }

    @Test
    fun `codec is deterministic and rejects tampered exact facts`() {
        val success =
            CompareHereManualComparisonService.compare(
                comparisonIntentKey = CompareHereComparisonIntentKey("intent:milk"),
                priceSelection = CompareHerePriceSelection.CURRENT,
                observations = capture(
                    "Small Milk\nCA$4.00\n500 g",
                    "Large Milk\nCA$7.00\n1 kg"
                )
            ) as CompareHereManualComparisonResult.Success
        val capture = requireNotNull(CompareHerePrivatePriceMemoryAssembler.from(success, 7L))
        val state = CompareHerePrivatePriceMemoryState(capture.entries.reversed())

        val encoded = CompareHerePrivatePriceMemoryCodec.encode(state)
        assertTrue(encoded.accepted)
        val encodedAgain = CompareHerePrivatePriceMemoryCodec.encode(state)
        assertTrue(encoded.bytes!!.contentEquals(encodedAgain.bytes))
        val decoded = CompareHerePrivatePriceMemoryCodec.decode(encoded.bytes)
        assertTrue(decoded.accepted)
        assertEquals(
            state.entries.sortedWith(
                compareByDescending<CompareHerePrivatePriceMemoryEntry> { it.observedAtEpochMillis }
                    .thenBy { it.observationId }
            ),
            decoded.state!!.entries
        )

        val tampered =
            String(encoded.bytes, Charsets.US_ASCII)
                .replace("|400|CAD|", "|401|CAD|")
                .toByteArray(Charsets.US_ASCII)
        val rejected = CompareHerePrivatePriceMemoryCodec.decode(tampered)
        assertFalse(rejected.accepted)
        assertNotNull(rejected.issue)
    }

    @Test
    fun `good price source is retained and round trips through the integrity codec`() {
        val entry =
            CompareHerePrivatePriceMemoryEntry.fromExactCandidate(
                candidate = exactCandidate("good-price-candidate"),
                displayName = "Good Price Product",
                priceSelection = CompareHerePriceSelection.CURRENT,
                promotionLabel = null,
                promotionReceivedUnits = 1L,
                promotionPaidUnits = 1L,
                observedAtEpochMillis = 88L,
                source = CompareHerePrivatePriceMemorySource.CONFIRMED_GOOD_PRICE_CHECK
            )
        assertEquals(
            CompareHerePrivatePriceMemorySource.CONFIRMED_GOOD_PRICE_CHECK,
            entry.source
        )

        val encoded = CompareHerePrivatePriceMemoryCodec.encode(
            CompareHerePrivatePriceMemoryState(listOf(entry))
        )
        assertTrue(encoded.accepted)
        val decoded = CompareHerePrivatePriceMemoryCodec.decode(requireNotNull(encoded.bytes))
        assertTrue(decoded.accepted)
        assertEquals(entry, requireNotNull(decoded.state).entries.single())
    }

    @Test
    fun `state manager keeps newest 256 snapshots with stable order`() {
        var state = CompareHerePrivatePriceMemoryState.empty()
        (0 until 9).forEach { batch ->
            val entries =
                (0 until 32).map { index ->
                    CompareHerePrivatePriceMemoryEntry.fromExactCandidate(
                        candidate = exactCandidate("candidate-$batch-$index"),
                        displayName = "Product $batch-$index",
                        priceSelection = CompareHerePriceSelection.CURRENT,
                        promotionLabel = null,
                        promotionReceivedUnits = 1L,
                        promotionPaidUnits = 1L,
                        observedAtEpochMillis = (batch * 32 + index).toLong()
                    )
                }
            state =
                CompareHerePrivatePriceMemoryStateManager.append(
                    state,
                    CompareHerePrivatePriceMemoryCapture(entries)
                )
        }
        assertEquals(MAX_COMPARE_HERE_PRIVATE_MEMORY_ENTRIES, state.entries.size)
        assertEquals(287L, state.entries.first().observedAtEpochMillis)
        assertEquals(32L, state.entries.last().observedAtEpochMillis)
        assertTrue(
            state.entries.zipWithNext().all { (left, right) ->
                left.observedAtEpochMillis >= right.observedAtEpochMillis
            }
        )
    }

    @Test
    fun `route evaluation exposes capture only for persisted user action`() {
        val evaluation =
            CompareHereManualRouteCoordinator.evaluateBlocks(
                rawBlocks = listOf("Small Milk\nCA$4.00\n500 g", "Large Milk\nCA$7.00\n1 kg"),
                observedAtEpochMillis = 100L,
                userConfirmedLikeForLike = true
            )
        assertEquals(CompareHereManualRouteStatus.EVALUATED, evaluation.state.status)
        assertEquals(2, evaluation.privateMemoryCapture!!.entries.size)

        val notConfirmed =
            CompareHereManualRouteCoordinator.evaluateBlocks(
                rawBlocks = listOf("Small Milk\nCA$4.00\n500 g", "Large Milk\nCA$7.00\n1 kg"),
                observedAtEpochMillis = 100L,
                userConfirmedLikeForLike = false
            )
        assertNull(notConfirmed.privateMemoryCapture)
    }

    @Test
    fun `local store fails closed on corruption and clear recovers`() {
        val storage = FakeMemoryStorage()
        val store = CompareHerePrivatePriceMemoryAndroidStore(storage)
        val success =
            CompareHereManualComparisonService.compare(
                comparisonIntentKey = CompareHereComparisonIntentKey("intent:milk"),
                priceSelection = CompareHerePriceSelection.CURRENT,
                observations = capture(
                    "Small Milk\nCA$4.00\n500 g",
                    "Large Milk\nCA$7.00\n1 kg"
                )
            ) as CompareHereManualComparisonResult.Success
        val capture = requireNotNull(CompareHerePrivatePriceMemoryAssembler.from(success, 9L))

        assertTrue(store.append(capture).accepted)
        assertEquals(2, store.load().state!!.entries.size)

        storage.bytes = storage.bytes!!.clone().also { bytes -> bytes[0] = 'X'.code.toByte() }
        val blocked = store.append(capture)
        assertFalse(blocked.accepted)
        assertEquals(CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_INVALID, blocked.issue)

        assertTrue(store.clear().accepted)
        assertEquals(0, store.load().state!!.entries.size)
    }

    private fun capture(vararg blocks: String): List<ProductObservation> {
        val result =
            ManualProductObservationAdapter.captureBlocks(
                rawBlocks = blocks.toList(),
                observedAtEpochMillis = 1L
            )
        assertTrue(result is ManualCaptureResult.Success)
        return (result as ManualCaptureResult.Success).observations
    }

    private fun exactCandidate(id: String): CompareHereExactCandidate =
        CompareHereExactCandidate(
            candidateId = id,
            comparisonIntentKey = CompareHereComparisonIntentKey("intent:test"),
            selectedPrice = Money(100L, "CAD"),
            quantity = NormalizedQuantity(1_000_000L, BaseUnit.COUNT),
            rate = UnitRate("CAD", 100_000_000L, RateUnit.ITEM)
        )

    private class FakeMemoryStorage : MemoryByteStorage {
        var bytes: ByteArray? = null

        override fun read(maxBytes: Int): RawMemoryReadResult =
            bytes?.let { stored ->
                if (stored.size > maxBytes) {
                    RawMemoryReadResult(
                        bytes = null,
                        found = true,
                        issue = CompareHerePrivatePriceMemoryStoreIssue.STORED_DATA_TOO_LARGE
                    )
                } else {
                    RawMemoryReadResult(bytes = stored.clone(), found = true)
                }
            } ?: RawMemoryReadResult(bytes = null, found = false)

        override fun replace(bytes: ByteArray): Boolean {
            this.bytes = bytes.clone()
            return true
        }

        override fun delete(): Boolean {
            bytes = null
            return true
        }
    }
}
