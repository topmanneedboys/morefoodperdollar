package com.valuepilot.app

import com.valuepilot.core.BaseUnit
import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHereManualInputAdapterTest {

    private val intent = CompareHereComparisonIntentKey("intent:milk")

    @Test
    fun `stated manual facts become exact candidates without deriving intent or identity from names`() {
        val observations =
            capture(
                "Small Milk\nCA$4.00\n500 g",
                "Large Milk\nCA$7.00\n1 kg"
            )

        val adaptation = success(CompareHereManualInputAdapter.adapt(intent, observations))

        assertEquals(intent, adaptation.comparisonIntentKey)
        assertEquals(listOf("manual-1", "manual-2"), adaptation.candidates.map { it.candidateId })
        assertTrue(adaptation.candidates.all { it.comparisonIntentKey == intent })
        assertEquals(listOf(400L, 700L), adaptation.candidates.map { it.offer.current.minorUnits })
        assertEquals(
            listOf(500_000_000L, 1_000_000_000L),
            adaptation.candidates.map { it.quantity?.amountMicros }
        )
        assertTrue(adaptation.candidates.all { it.quantity?.unit == BaseUnit.GRAM })
        assertEquals(
            listOf("Small Milk", "Large Milk"),
            adaptation.displayMetadata.entries.map { it.displayName }
        )
        assertTrue(adaptation.issues.isEmpty())
    }

    @Test
    fun `range midpoint is omitted instead of being promoted to an exact quantity`() {
        val adaptation =
            success(
                CompareHereManualInputAdapter.adapt(
                    intent,
                    capture("Milk Range\nCA$4.00\n500-700 g")
                )
            )

        assertEquals(1, adaptation.candidates.size)
        assertNull(adaptation.candidates.single().quantity)
        assertEquals(
            listOf(CompareHereManualObservationIssue.QUANTITY_NOT_EXACT_ENOUGH),
            adaptation.issues.map { it.issue }
        )
    }

    @Test
    fun `bare dollar ambiguity rejects the observation before exact money construction`() {
        val adaptation =
            success(
                CompareHereManualInputAdapter.adapt(
                    intent,
                    capture("Milk\n$4.00\n500 g")
                )
            )

        assertTrue(adaptation.candidates.isEmpty())
        assertEquals(
            listOf(CompareHereManualObservationIssue.AMBIGUOUS_OR_MIXED_CURRENCY),
            adaptation.issues.map { it.issue }
        )
    }

    @Test
    fun `mixed currencies reject the observation even when legacy parser selects one`() {
        val adaptation =
            success(
                CompareHereManualInputAdapter.adapt(
                    intent,
                    capture("Milk\nCurrent price CA$4.00\nMember price US$3.00\n500 g")
                )
            )

        assertTrue(adaptation.candidates.isEmpty())
        assertEquals(
            listOf(CompareHereManualObservationIssue.AMBIGUOUS_OR_MIXED_CURRENCY),
            adaptation.issues.map { it.issue }
        )
    }

    @Test
    fun `unsupported multi buy promotion is rejected rather than approximated`() {
        val adaptation =
            success(
                CompareHereManualInputAdapter.adapt(
                    intent,
                    capture("Eggs\nCA$6.00\n12 count\nBuy 2 get 1 free")
                )
            )

        assertTrue(adaptation.candidates.isEmpty())
        assertEquals(
            listOf(CompareHereManualObservationIssue.UNSUPPORTED_PROMOTION),
            adaptation.issues.map { it.issue }
        )
    }

    @Test
    fun `plain bogo maps to exact two received for one paid terms`() {
        val adaptation =
            success(
                CompareHereManualInputAdapter.adapt(
                    intent,
                    capture("Eggs\nCA$6.00\n12 count\nBuy one get one free")
                )
            )

        val promotion = adaptation.candidates.single().offer.promotion
        assertEquals(2L, promotion.receivedUnits)
        assertEquals(1L, promotion.paidUnits)
        assertEquals("Buy 1, get 1", promotion.label)
        assertTrue(adaptation.issues.isEmpty())
    }

    @Test
    fun `derived pizza area is omitted from exact Compare Here quantity`() {
        val adaptation =
            success(
                CompareHereManualInputAdapter.adapt(
                    CompareHereComparisonIntentKey("intent:pizza"),
                    capture("Cheese Pizza\nCA$12.00\n12 inch pizza")
                )
            )

        assertEquals(1, adaptation.candidates.size)
        assertNull(adaptation.candidates.single().quantity)
        assertEquals(
            listOf(CompareHereManualObservationIssue.QUANTITY_NOT_EXACT_ENOUGH),
            adaptation.issues.map { it.issue }
        )
    }

    @Test
    fun `adapter fails closed above shared Compare Here candidate bound`() {
        val observations =
            (1..33).map { index ->
                ProductObservation(
                    id = ProductObservationId("manual-$index"),
                    sourceId = "manual",
                    rawText = "Milk $index\nCA$4.00\n500 g",
                    observedAtEpochMillis = 1L
                )
            }

        val result = CompareHereManualInputAdapter.adapt(intent, observations)

        assertEquals(
            CompareHereManualInputResult.Failure(
                CompareHereManualInputFailure.TOO_MANY_OBSERVATIONS
            ),
            result
        )
    }

    @Test
    fun `duplicate observation identity fails closed before metadata can become ambiguous`() {
        val repeated =
            ProductObservation(
                id = ProductObservationId("manual-1"),
                sourceId = "manual",
                rawText = "Milk\nCA$4.00\n500 g",
                observedAtEpochMillis = 1L
            )

        val result = CompareHereManualInputAdapter.adapt(intent, listOf(repeated, repeated))

        assertEquals(
            CompareHereManualInputResult.Failure(
                CompareHereManualInputFailure.DUPLICATE_OBSERVATION_IDS
            ),
            result
        )
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

    private fun success(result: CompareHereManualInputResult): CompareHereManualInputAdaptation {
        assertTrue(result is CompareHereManualInputResult.Success)
        return (result as CompareHereManualInputResult.Success).adaptation
    }
}
