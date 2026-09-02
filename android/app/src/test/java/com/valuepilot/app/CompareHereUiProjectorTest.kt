package com.valuepilot.app

import com.valuepilot.core.CompareHereCandidate
import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.CompareHereEvaluator
import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.Money
import com.valuepilot.core.NormalizedQuantity
import com.valuepilot.core.Offer
import com.valuepilot.core.QuantityNormalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHereUiProjectorTest {

    private val milk = CompareHereComparisonIntentKey("intent:milk")

    @Test
    fun `ready comparison renders core ranking with separately supplied human labels`() {
        val core =
            current(
                candidate("opaque-small-002", "4.00", QuantityNormalization.grams(500)),
                candidate("opaque-large-001", "7.00", QuantityNormalization.grams(1_000))
            )

        val projection =
            CompareHereUiProjector.project(
                core,
                metadata(
                    "opaque-small-002" to "Small Milk",
                    "opaque-large-001" to "Large Milk"
                )
            )

        val state = projection.state
        assertEquals(CompareHereUiStatus.READY, state.status)
        assertEquals("Best value", state.statusTitle)
        assertEquals(listOf("Large Milk", "Small Milk"), state.rows.map { it.title })
        assertEquals(listOf(1, 2), state.rows.map { it.valueRank })
        assertTrue(state.rows.first().bestValue)
        assertFalse(state.rows.last().bestValue)
        assertEquals("7.00 CAD", state.rows.first().priceText)
        assertEquals("1000 g", state.rows.first().quantityText)
        assertEquals("7 CAD/kg", state.rows.first().unitRateText)
    }

    @Test
    fun `ready exact tie preserves both best value rows from core`() {
        val core =
            current(
                candidate("b", "8.00", QuantityNormalization.grams(1_000)),
                candidate("a", "4.00", QuantityNormalization.grams(500))
            )

        val state =
            CompareHereUiProjector.project(
                core,
                metadata("a" to "Milk A", "b" to "Milk B")
            ).state

        assertEquals(CompareHereUiStatus.READY, state.status)
        assertEquals("Best value tie", state.statusTitle)
        assertEquals(listOf(1, 1), state.rows.map { it.valueRank })
        assertTrue(state.rows.all { it.bestValue })
    }

    @Test
    fun `missing rankable label suppresses all winner and rank claims`() {
        val core =
            current(
                candidate("small-private-id", "4.00", QuantityNormalization.grams(500)),
                candidate("large-private-id", "7.00", QuantityNormalization.grams(1_000))
            )

        val state =
            CompareHereUiProjector.project(
                core,
                metadata("small-private-id" to "Small Milk")
            ).state

        assertEquals(CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
        assertEquals("Product names needed", state.statusTitle)
        assertEquals(listOf("Small Milk"), state.rows.map { it.title })
        assertTrue(state.rows.all { it.valueRank == null && !it.bestValue })
        assertEquals(1, state.omittedDisplayNameCount)
        assertEquals("1 product name could not be shown safely.", state.notice)
    }

    @Test
    fun `candidate id is never a display label fallback`() {
        val core =
            current(
                candidate("technical-candidate-123456", "4.00", QuantityNormalization.grams(500)),
                candidate("safe-candidate-654321", "7.00", QuantityNormalization.grams(1_000))
            )

        val state =
            CompareHereUiProjector.project(
                core,
                metadata(
                    "technical-candidate-123456" to "technical-candidate-123456",
                    "safe-candidate-654321" to "Large Milk"
                )
            ).state

        assertEquals(CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
        assertEquals(listOf("Large Milk"), state.rows.map { it.title })
        assertEquals(1, state.omittedDisplayNameCount)
        assertTrue(allConsumerStrings(state).none { it.contains("technical-candidate-123456") })
    }

    @Test
    fun `valid GTIN and technical prefix labels are rejected as consumer names`() {
        val core =
            current(
                candidate("first-opaque-id", "4.00", QuantityNormalization.grams(500)),
                candidate("second-opaque-id", "7.00", QuantityNormalization.grams(1_000))
            )

        val state =
            CompareHereUiProjector.project(
                core,
                metadata(
                    "first-opaque-id" to "036000291452",
                    "second-opaque-id" to "Product sku:ABC12345"
                )
            ).state

        assertEquals(CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
        assertTrue(state.rows.isEmpty())
        assertEquals(2, state.omittedDisplayNameCount)
    }

    @Test
    fun `unsafe or overlong label is omitted without exposing internal candidate`() {
        val core =
            current(
                candidate("unsafe-one-123456", "4.00", QuantityNormalization.grams(500)),
                candidate("unsafe-two-654321", "7.00", QuantityNormalization.grams(1_000))
            )

        val control = "Milk\u0007"
        val state =
            CompareHereUiProjector.project(
                core,
                metadata(
                    "unsafe-one-123456" to control,
                    "unsafe-two-654321" to "M".repeat(161)
                )
            ).state

        assertEquals(CompareHereUiStatus.DISPLAY_METADATA_INCOMPLETE, state.status)
        assertTrue(state.rows.isEmpty())
        assertEquals(2, state.omittedDisplayNameCount)
        assertTrue(allConsumerStrings(state).none { it.contains("unsafe-one-123456") })
        assertTrue(allConsumerStrings(state).none { it.contains("unsafe-two-654321") })
    }

    @Test
    fun `extra metadata cannot manufacture a comparison row`() {
        val core =
            current(
                candidate("a", "4.00", QuantityNormalization.grams(500)),
                candidate("b", "7.00", QuantityNormalization.grams(1_000))
            )

        val state =
            CompareHereUiProjector.project(
                core,
                metadata("a" to "Milk A", "b" to "Milk B", "extra" to "Fake Product")
            ).state

        assertEquals(2, state.rows.size)
        assertTrue(state.rows.none { it.title == "Fake Product" })
    }

    @Test
    fun `not enough exact data shows safe exact row and typed blocked reason without ranking`() {
        val core =
            current(
                candidate("exact", "4.00", QuantityNormalization.grams(500)),
                candidate("opaque-blocked-333333", "2.00", null)
            )

        val state =
            CompareHereUiProjector.project(
                core,
                metadata("exact" to "Known Milk", "opaque-blocked-333333" to "Unknown Milk")
            ).state

        assertEquals(CompareHereUiStatus.NOT_ENOUGH_DATA, state.status)
        assertEquals("Need more exact information", state.statusTitle)
        assertEquals(
            "Add at least two products with an exact current price and package quantity.",
            state.guidance
        )
        assertEquals(listOf("Known Milk"), state.rows.map { it.title })
        assertNull(state.rows.single().valueRank)
        assertFalse(state.rows.single().bestValue)
        assertEquals(listOf("Unknown Milk"), state.blockedRows.map { it.title })
        assertEquals("Package quantity needed", state.blockedRows.single().reasonText)
    }

    @Test
    fun `missing blocked label does not hide a verified winner among fully labeled exact candidates`() {
        val core =
            current(
                candidate("a", "4.00", QuantityNormalization.grams(500)),
                candidate("b", "7.00", QuantityNormalization.grams(1_000)),
                candidate("unknown", "2.00", null)
            )

        val state =
            CompareHereUiProjector.project(
                core,
                metadata("a" to "Small Milk", "b" to "Large Milk")
            ).state

        assertEquals(CompareHereUiStatus.READY, state.status)
        assertEquals(listOf("Large Milk"), state.rows.filter { it.bestValue }.map { it.title })
        assertTrue(state.blockedRows.isEmpty())
        assertEquals(1, state.omittedDisplayNameCount)
        assertEquals("1 product name could not be shown safely.", state.notice)
    }

    @Test
    fun `member mode displays exact selected member price and never current fallback`() {
        val core =
            CompareHereEvaluator.evaluate(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.MEMBER,
                candidates =
                    listOf(
                        candidate("a", "5.00", QuantityNormalization.grams(500), member = "3.00"),
                        candidate("b", "6.00", QuantityNormalization.grams(500), member = "4.00"),
                        candidate("missing", "1.00", QuantityNormalization.grams(500))
                    )
            )

        val state =
            CompareHereUiProjector.project(
                core,
                metadata("a" to "Milk A", "b" to "Milk B", "missing" to "Milk C")
            ).state

        assertEquals(CompareHereUiStatus.READY, state.status)
        assertEquals("Member prices", state.priceModeText)
        assertEquals("3.00 CAD", state.rows.first { it.title == "Milk A" }.priceText)
        assertEquals(
            "Member price unavailable",
            state.blockedRows.single { it.title == "Milk C" }.reasonText
        )
    }

    @Test
    fun `member mode explains missing member evidence without implying current fallback`() {
        val core =
            CompareHereEvaluator.evaluate(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.MEMBER,
                candidates =
                    listOf(
                        candidate("a", "5.00", QuantityNormalization.grams(500)),
                        candidate("b", "6.00", QuantityNormalization.grams(500))
                    )
            )

        val state =
            CompareHereUiProjector.project(
                core,
                metadata("a" to "Milk A", "b" to "Milk B")
            ).state

        assertEquals(CompareHereUiStatus.NOT_ENOUGH_DATA, state.status)
        assertEquals("Member prices", state.priceModeText)
        assertEquals(
            "Add at least two products with an exact member price and package quantity. " +
                "Current prices are not used as substitutes.",
            state.guidance
        )
        assertTrue(state.rows.isEmpty())
        assertEquals(
            listOf("Member price unavailable", "Member price unavailable"),
            state.blockedRows.map { it.reasonText }
        )
    }

    @Test
    fun `incompatible dimensions display exact reference rates but never ranks or winner`() {
        val core =
            current(
                candidate("mass", "4.00", QuantityNormalization.grams(500)),
                candidate("count", "4.00", QuantityNormalization.count(2))
            )

        val state =
            CompareHereUiProjector.project(
                core,
                metadata("mass" to "Milk by Weight", "count" to "Milk Pack")
            ).state

        assertEquals(CompareHereUiStatus.INCOMPATIBLE_DIMENSIONS, state.status)
        assertEquals("Cannot rank these together", state.statusTitle)
        assertEquals("These products use incompatible quantity units.", state.guidance)
        assertEquals(2, state.rows.size)
        assertTrue(state.rows.all { it.valueRank == null && !it.bestValue })
    }

    @Test
    fun `detached metadata is bounded and duplicate keys fail closed`() {
        val entries =
            (1..33).map { index -> CompareHereDisplayMetadataEntry("id-$index", "Product $index") }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CompareHereDisplayMetadata(entries)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CompareHereDisplayMetadata(
                listOf(
                    CompareHereDisplayMetadataEntry("same", "First"),
                    CompareHereDisplayMetadataEntry("same", "Second")
                )
            )
        }
    }

    @Test
    fun `raw metadata label is bounded before projection`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            CompareHereDisplayMetadataEntry("id", "X".repeat(501))
        }
    }

    @Test
    fun `consumer state never includes opaque candidate ids`() {
        val core =
            current(
                candidate("opaque-product-111111", "4.00", QuantityNormalization.grams(500)),
                candidate("opaque-product-222222", "7.00", QuantityNormalization.grams(1_000)),
                candidate("opaque-product-333333", "2.00", null)
            )
        val state =
            CompareHereUiProjector.project(
                core,
                metadata(
                    "opaque-product-111111" to "Small Milk",
                    "opaque-product-222222" to "Large Milk",
                    "opaque-product-333333" to "Unknown Milk"
                )
            ).state

        val strings = allConsumerStrings(state)
        assertTrue(strings.none { it.contains("opaque-product-111111") })
        assertTrue(strings.none { it.contains("opaque-product-222222") })
        assertTrue(strings.none { it.contains("opaque-product-333333") })
    }

    private fun current(vararg candidates: CompareHereCandidate) =
        CompareHereEvaluator.evaluate(
            comparisonIntentKey = milk,
            priceSelection = CompareHerePriceSelection.CURRENT,
            candidates = candidates.toList()
        )

    private fun candidate(
        id: String,
        current: String,
        quantity: NormalizedQuantity?,
        member: String? = null
    ): CompareHereCandidate =
        CompareHereCandidate(
            candidateId = id,
            comparisonIntentKey = milk,
            offer =
                Offer(
                    current = Money.parse(current, "CAD"),
                    member = member?.let { Money.parse(it, "CAD") }
                ),
            quantity = quantity
        )

    private fun metadata(vararg pairs: Pair<String, String>): CompareHereDisplayMetadata =
        CompareHereDisplayMetadata(
            pairs.map { (id, label) -> CompareHereDisplayMetadataEntry(id, label) }
        )

    private fun allConsumerStrings(state: CompareHereUiState): List<String> =
        buildList {
            add(state.headline)
            add(state.priceModeText)
            add(state.statusTitle)
            add(state.guidance)
            state.notice?.let(::add)
            state.rows.forEach { row ->
                add(row.title)
                add(row.priceText)
                add(row.quantityText)
                add(row.unitRateText)
            }
            state.blockedRows.forEach { row ->
                add(row.title)
                add(row.reasonText)
            }
        }
}
