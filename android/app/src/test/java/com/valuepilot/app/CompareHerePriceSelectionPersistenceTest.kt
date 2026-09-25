package com.valuepilot.app

import com.valuepilot.core.CompareHerePriceSelection
import org.junit.Assert.assertEquals
import org.junit.Test

class CompareHerePriceSelectionPersistenceTest {

    @Test
    fun `selection encoding round trips explicit member choice`() {
        val encoded =
            CompareHerePriceSelectionPersistence.encode(
                CompareHerePriceSelection.MEMBER
            )

        assertEquals(
            CompareHerePriceSelection.MEMBER,
            CompareHerePriceSelectionPersistence.decode(encoded)
        )
    }

    @Test
    fun `missing or unknown persisted value safely defaults to current`() {
        assertEquals(
            CompareHerePriceSelection.CURRENT,
            CompareHerePriceSelectionPersistence.decode(null)
        )
        assertEquals(
            CompareHerePriceSelection.CURRENT,
            CompareHerePriceSelectionPersistence.decode("future_price_basis")
        )
    }
}
