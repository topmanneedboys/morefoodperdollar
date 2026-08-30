package com.valuepilot.app

import com.valuepilot.core.CompareHereCandidate
import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.CompareHereEvaluator
import com.valuepilot.core.CompareHerePriceSelection
import com.valuepilot.core.Money
import com.valuepilot.core.Offer
import com.valuepilot.core.QuantityNormalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CompareHereSurfacePresenterTest {

    @Test
    fun `presenter hands only projected consumer state to physical renderer`() {
        val intent = CompareHereComparisonIntentKey("intent:milk")
        val result =
            CompareHereEvaluator.evaluate(
                comparisonIntentKey = intent,
                priceSelection = CompareHerePriceSelection.CURRENT,
                candidates =
                    listOf(
                        CompareHereCandidate(
                            candidateId = "opaque-small-111111",
                            comparisonIntentKey = intent,
                            offer = Offer(current = Money.parse("4.00", "CAD")),
                            quantity = QuantityNormalization.grams(500)
                        ),
                        CompareHereCandidate(
                            candidateId = "opaque-large-222222",
                            comparisonIntentKey = intent,
                            offer = Offer(current = Money.parse("7.00", "CAD")),
                            quantity = QuantityNormalization.grams(1_000)
                        )
                    )
            )
        val projection =
            CompareHereUiProjector.project(
                result,
                CompareHereDisplayMetadata(
                    listOf(
                        CompareHereDisplayMetadataEntry(
                            candidateId = "opaque-small-111111",
                            displayName = "Small Milk"
                        ),
                        CompareHereDisplayMetadataEntry(
                            candidateId = "opaque-large-222222",
                            displayName = "Large Milk"
                        )
                    )
                )
            )

        var renderedState: CompareHereUiState? = null
        var renderCalls = 0
        val presenter =
            CompareHereSurfacePresenter(
                CompareHereSurfaceRenderer { state ->
                    renderCalls += 1
                    renderedState = state
                }
            )

        presenter.render(projection)

        assertEquals(1, renderCalls)
        assertSame(projection.state, renderedState)
        assertEquals(listOf("Large Milk", "Small Milk"), renderedState?.rows?.map { it.title })
    }
}
