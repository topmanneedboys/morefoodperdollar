package com.valuepilot.app

import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHereManualComparisonServiceTest {

    private val milk = CompareHereComparisonIntentKey("intent:milk")

    @Test
    fun `two exact manual observations compose to ready projected winner`() {
        val result =
            CompareHereManualComparisonService.compare(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.CURRENT,
                observations =
                    capture(
                        "Small Milk\nCA$4.00\n500 g",
                        "Large Milk\nCA$7.00\n1 kg"
                    )
            )

        val success = success(result)
        assertTrue(success.adaptationIssues.isEmpty())
        assertEquals(CompareHereUiStatus.READY, success.projection.state.status)
        assertEquals(
            listOf("Large Milk", "Small Milk"),
            success.projection.state.rows.map { it.title }
        )
        assertEquals(listOf(1, 2), success.projection.state.rows.map { it.valueRank })
        assertTrue(success.projection.state.rows.first().bestValue)
        assertFalse(success.projection.state.rows.last().bestValue)
    }

    @Test
    fun `estimated range survives as typed blocked row instead of entering exact ranking`() {
        val result =
            CompareHereManualComparisonService.compare(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.CURRENT,
                observations =
                    capture(
                        "Known Milk\nCA$4.00\n500 g",
                        "Range Milk\nCA$5.00\n500-700 g"
                    )
            )

        val success = success(result)
        val state = success.projection.state
        assertEquals(
            listOf(CompareHereManualObservationIssue.QUANTITY_NOT_EXACT_ENOUGH),
            success.adaptationIssues.map { it.issue }
        )
        assertEquals(CompareHereUiStatus.NOT_ENOUGH_DATA, state.status)
        assertEquals(listOf("Known Milk"), state.rows.map { it.title })
        assertNull(state.rows.single().valueRank)
        assertFalse(state.rows.single().bestValue)
        assertEquals(listOf("Range Milk"), state.blockedRows.map { it.title })
        assertEquals("Package quantity needed", state.blockedRows.single().reasonText)
    }

    @Test
    fun `unsupported promotion rejects whole composition instead of ranking surviving subset`() {
        val result =
            CompareHereManualComparisonService.compare(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.CURRENT,
                observations =
                    capture(
                        "Small Milk\nCA$4.00\n500 g",
                        "Large Milk\nCA$7.00\n1 kg",
                        "Promo Milk\nCA$2.00\n500 g\nBuy 2 get 1 free"
                    )
            )

        assertTrue(result is CompareHereManualComparisonResult.RejectedObservations)
        val rejected = result as CompareHereManualComparisonResult.RejectedObservations
        assertEquals(1, rejected.issues.size)
        assertEquals("manual-3", rejected.issues.single().observationId)
        assertEquals(
            CompareHereManualObservationIssue.UNSUPPORTED_PROMOTION,
            rejected.issues.single().issue
        )
    }

    @Test
    fun `ambiguous currency rejects whole composition instead of ranking surviving subset`() {
        val result =
            CompareHereManualComparisonService.compare(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.CURRENT,
                observations =
                    capture(
                        "Small Milk\nCA$4.00\n500 g",
                        "Large Milk\nCA$7.00\n1 kg",
                        "Ambiguous Milk\n$2.00\n500 g"
                    )
            )

        assertTrue(result is CompareHereManualComparisonResult.RejectedObservations)
        val rejected = result as CompareHereManualComparisonResult.RejectedObservations
        assertEquals("manual-3", rejected.issues.single().observationId)
        assertEquals(
            CompareHereManualObservationIssue.AMBIGUOUS_OR_MIXED_CURRENCY,
            rejected.issues.single().issue
        )
    }

    @Test
    fun `structural adapter failure remains typed and never reaches evaluator`() {
        val observations =
            (1..33).map { index ->
                ProductObservation(
                    id = ProductObservationId("manual-$index"),
                    sourceId = "manual",
                    rawText = "Milk $index\nCA$4.00\n500 g",
                    observedAtEpochMillis = 1L
                )
            }

        val result =
            CompareHereManualComparisonService.compare(
                comparisonIntentKey = milk,
                priceSelection = CompareHerePriceSelection.CURRENT,
                observations = observations
            )

        assertEquals(
            CompareHereManualComparisonResult.InputFailure(
                CompareHereManualInputFailure.TOO_MANY_OBSERVATIONS
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

    private fun success(
        result: CompareHereManualComparisonResult
    ): CompareHereManualComparisonResult.Success {
        assertTrue(result is CompareHereManualComparisonResult.Success)
        return result as CompareHereManualComparisonResult.Success
    }
}
