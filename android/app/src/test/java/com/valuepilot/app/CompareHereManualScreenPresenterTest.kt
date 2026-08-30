package com.valuepilot.app

import com.valuepilot.core.CompareHereComparisonIntentKey
import com.valuepilot.core.CompareHerePriceSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareHereManualScreenPresenterTest {

    @Test
    fun `non evaluated route becomes generic message content only`() {
        val routeState =
            CompareHereManualRouteState(
                status = CompareHereManualRouteStatus.PRODUCTS_REJECTED,
                title = "Some products need clearer information",
                guidance = "Check currency, promotion, price, and package details, then try again.",
                rejectedProductCount = 2
            )
        var rendered: CompareHereManualScreenContent? = null
        val presenter =
            CompareHereManualScreenPresenter(
                CompareHereManualScreenRenderer { content -> rendered = content }
            )

        presenter.render(routeState)

        assertEquals(
            CompareHereManualScreenContent.Message(
                title = routeState.title,
                guidance = routeState.guidance,
                rejectedProductCount = 2
            ),
            rendered
        )
        assertTrue(allContentStrings(requireNotNull(rendered)).none { it.contains("manual-") })
    }

    @Test
    fun `evaluated route hands the exact immutable comparison state to renderer`() {
        val routeState =
            CompareHereManualRouteCoordinator.compareBlocks(
                rawBlocks =
                    listOf(
                        "Small Milk\nCA$4.00\n500 g",
                        "Large Milk\nCA$7.00\n1 kg"
                    ),
                observedAtEpochMillis = 1L,
                userConfirmedLikeForLike = true,
                priceSelection = CompareHerePriceSelection.CURRENT
            )
        val comparisonState = requireNotNull(routeState.comparisonState)
        var rendered: CompareHereManualScreenContent? = null
        val presenter =
            CompareHereManualScreenPresenter(
                CompareHereManualScreenRenderer { content -> rendered = content }
            )

        presenter.render(routeState)

        assertTrue(rendered is CompareHereManualScreenContent.Comparison)
        assertSame(
            comparisonState,
            (rendered as CompareHereManualScreenContent.Comparison).state
        )
        assertTrue(allContentStrings(requireNotNull(rendered)).none { it.contains("manual-") })
    }

    @Test
    fun `screen content cannot carry route status or comparison intent key`() {
        val fields = CompareHereManualScreenContent.Message::class.java.declaredFields.map { it.name }
        assertTrue(fields.none { it.contains("status", ignoreCase = true) })
        assertTrue(fields.none { it.contains("intent", ignoreCase = true) })
        assertTrue(fields.none { it.contains("observation", ignoreCase = true) })
        assertTrue(fields.none { it.contains("candidate", ignoreCase = true) })
        assertTrue(
            CompareHereManualScreenContent.Message::class.java.declaredFields.none {
                it.type == CompareHereComparisonIntentKey::class.java
            }
        )
    }

    private fun allContentStrings(content: CompareHereManualScreenContent): List<String> =
        when (content) {
            is CompareHereManualScreenContent.Message ->
                listOf(content.title, content.guidance)
            is CompareHereManualScreenContent.Comparison ->
                buildList {
                    val state = content.state
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
}
