package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticalShoppingHomeOfflineCatalogComparisonSelectionTest {

    @Test
    fun `selected identity is trimmed and handed off as untrusted text`() {
        val matches =
            listOf(
                PracticalShoppingHomeOfflineCatalogPresentation.Match(
                    displayName = "  Oat Milk  ",
                    brand = null,
                    matchLabel = "name match"
                )
            )

        assertEquals(
            "Oat Milk",
            PracticalShoppingHomeOfflineCatalogComparisonSelection.displayNameFor(
                matches = matches,
                selectedIndex = 0
            )
        )
    }

    @Test
    fun `invalid index control text and oversized identity fail closed`() {
        val matches =
            listOf(
                PracticalShoppingHomeOfflineCatalogPresentation.Match(
                    displayName = "Safe",
                    brand = null,
                    matchLabel = "name match"
                ),
                PracticalShoppingHomeOfflineCatalogPresentation.Match(
                    displayName = "Unsafe\u0000name",
                    brand = null,
                    matchLabel = "name match"
                ),
                PracticalShoppingHomeOfflineCatalogPresentation.Match(
                    displayName = "x".repeat(ShareToValuePilotInput.MAX_CHARS + 1),
                    brand = null,
                    matchLabel = "name match"
                )
            )

        assertNull(
            PracticalShoppingHomeOfflineCatalogComparisonSelection.displayNameFor(
                matches,
                selectedIndex = -1
            )
        )
        assertNull(
            PracticalShoppingHomeOfflineCatalogComparisonSelection.displayNameFor(
                matches,
                selectedIndex = 1
            )
        )
        assertNull(
            PracticalShoppingHomeOfflineCatalogComparisonSelection.displayNameFor(
                matches,
                selectedIndex = 2
            )
        )
    }
}
