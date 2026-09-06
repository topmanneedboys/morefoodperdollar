package com.valuepilot.app

import com.valuepilot.core.ProductObservation
import com.valuepilot.core.ProductObservationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHereManualIssuePresentationTest {

    @Test
    fun `ambiguous currency points to the submitted product position`() {
        val guidance =
            CompareHereManualIssuePresentation.forIssues(
                issues =
                    listOf(
                        CompareHereManualObservationIssueEntry(
                            observationId = "manual-2",
                            issue = CompareHereManualObservationIssue.AMBIGUOUS_OR_MIXED_CURRENCY
                        )
                    ),
                observations = observations(2)
            )

        assertEquals(
            listOf("Product 2: use one concrete currency such as CA$ or US$."),
            guidance
        )
    }

    @Test
    fun `repair copy stays useful for malformed promotion and parse failures`() {
        val guidance =
            CompareHereManualIssuePresentation.forIssues(
                issues =
                    listOf(
                        CompareHereManualObservationIssueEntry(
                            observationId = "manual-1",
                            issue = CompareHereManualObservationIssue.UNSUPPORTED_PROMOTION
                        ),
                        CompareHereManualObservationIssueEntry(
                            observationId = "manual-3",
                            issue = CompareHereManualObservationIssue.PARSE_FAILED
                        )
                    ),
                observations = observations(3)
            )

        assertEquals(
            listOf(
                "Product 1: describe a supported exact promotion or remove the promotion details.",
                "Product 3: add a product name, one concrete price and currency, and an exact package size or count."
            ),
            guidance
        )
    }

    @Test
    fun `unknown issue ids never leak technical identifiers`() {
        val guidance =
            CompareHereManualIssuePresentation.forIssues(
                issues =
                    listOf(
                        CompareHereManualObservationIssueEntry(
                            observationId = "internal-secret-id",
                            issue = CompareHereManualObservationIssue.INVALID_EXACT_FACTS
                        )
                    ),
                observations = observations(1)
            )

        assertEquals(
            listOf("One product: check the price, currency, and package details."),
            guidance
        )
        assertTrue(guidance.none { it.contains("internal-secret-id") })
    }

    private fun observations(count: Int): List<ProductObservation> =
        (1..count).map { index ->
            ProductObservation(
                id = ProductObservationId("manual-$index"),
                sourceId = "manual",
                rawText = "Product $index",
                observedAtEpochMillis = 1L
            )
        }
}
