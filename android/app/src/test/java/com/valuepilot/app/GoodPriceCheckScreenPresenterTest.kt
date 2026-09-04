package com.valuepilot.app

import com.valuepilot.core.CompareHerePriceSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GoodPriceCheckScreenPresenterTest {

    @Test
    fun `message route is rendered without exposing a result`() {
        var rendered: GoodPriceCheckScreenContent? = null
        val presenter = GoodPriceCheckScreenPresenter(GoodPriceCheckScreenRenderer { rendered = it })
        val route =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = " ",
                observedAtEpochMillis = 1L,
                priceSelection = CompareHerePriceSelection.CURRENT,
                privateMemory = CompareHerePrivatePriceMemoryState.empty()
            ).state

        presenter.render(route)

        val message = rendered as GoodPriceCheckScreenContent.Message
        assertEquals(route.title, message.title)
        assertEquals(route.guidance, message.guidance)
    }

    @Test
    fun `evaluated route forwards the immutable projected state unchanged`() {
        var rendered: GoodPriceCheckScreenContent? = null
        val presenter = GoodPriceCheckScreenPresenter(GoodPriceCheckScreenRenderer { rendered = it })
        val route =
            GoodPriceCheckRouteCoordinator.checkBlock(
                rawBlock = "Whole Milk\nCA$6.49\n4 L",
                observedAtEpochMillis = 1L,
                priceSelection = CompareHerePriceSelection.CURRENT,
                privateMemory = CompareHerePrivatePriceMemoryState.empty()
            ).state

        presenter.render(route)

        val result = rendered as GoodPriceCheckScreenContent.Result
        assertSame(route.result, result.state)
    }
}
