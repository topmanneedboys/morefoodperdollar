package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingYouOverviewPresentationTest {

    @Test
    fun `empty personal memory stays immediately understandable`() {
        val state = PracticalShoppingYouOverviewState(0, 0, 0)

        assertEquals(0, state.boughtCount)
        assertEquals(0, state.savedCount)
        assertEquals(0, state.watchingCount)
    }

    @Test
    fun `single-purpose memories keep the other choices visibly empty`() {
        val purchasesOnly = PracticalShoppingYouOverviewState(2, 0, 0)
        assertEquals(2, purchasesOnly.boughtCount)
        assertEquals(0, purchasesOnly.savedCount)
        assertEquals(0, purchasesOnly.watchingCount)

        val savedOnly = PracticalShoppingYouOverviewState(0, 3, 0)
        assertEquals(0, savedOnly.boughtCount)
        assertEquals(3, savedOnly.savedCount)
        assertEquals(0, savedOnly.watchingCount)

        val watchingOnly = PracticalShoppingYouOverviewState(0, 0, 4)
        assertEquals(0, watchingOnly.boughtCount)
        assertEquals(0, watchingOnly.savedCount)
        assertEquals(4, watchingOnly.watchingCount)
    }

    @Test
    fun `counts are bounded summaries and preserve heavy mixed personal use`() {
        val state = PracticalShoppingYouOverviewState(
            boughtCount = 100,
            savedCount = 50,
            watchingCount = 30
        )

        assertEquals(100, state.boughtCount)
        assertEquals(50, state.savedCount)
        assertEquals(30, state.watchingCount)
        assertTrue(state.boughtCount >= 0)
        assertTrue(state.savedCount >= 0)
        assertTrue(state.watchingCount >= 0)
    }

    @Test
    fun `negative counts are rejected instead of becoming misleading empty states`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PracticalShoppingYouOverviewState(-1, 0, 0)
        }
    }

    @Test
    fun `overview actions remain separate typed routes`() {
        val actions = setOf(
            PracticalShoppingYouOverviewAction.OpenBought,
            PracticalShoppingYouOverviewAction.OpenSaved,
            PracticalShoppingYouOverviewAction.OpenWatching,
            PracticalShoppingYouOverviewAction.OpenSettings
        )
        assertEquals(4, actions.size)
        assertTrue(actions.contains(PracticalShoppingYouOverviewAction.OpenBought))
        assertTrue(actions.contains(PracticalShoppingYouOverviewAction.OpenSaved))
        assertTrue(actions.contains(PracticalShoppingYouOverviewAction.OpenWatching))
        assertTrue(actions.contains(PracticalShoppingYouOverviewAction.OpenSettings))
    }
}
