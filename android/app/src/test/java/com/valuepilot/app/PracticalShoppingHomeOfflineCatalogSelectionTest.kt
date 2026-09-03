package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticalShoppingHomeOfflineCatalogSelectionTest {

    @Test
    fun `selection replaces only the first whole unresolved token and preserves the rest`() {
        assertEquals(
            "milk Oat Milk dragonfruit",
            PracticalShoppingHomeOfflineCatalogSelection.replaceUnknownToken(
                rawQuery = "milk dragonfruit dragonfruit",
                unknownToken = "dragonfruit",
                replacementName = "Oat Milk"
            )
        )
    }

    @Test
    fun `selection is case insensitive but does not replace a token substring`() {
        assertEquals(
            "milk oat milk oatmilk",
            PracticalShoppingHomeOfflineCatalogSelection.replaceUnknownToken(
                rawQuery = "milk OAT oatmilk",
                unknownToken = "oat",
                replacementName = "oat milk"
            )
        )
    }

    @Test
    fun `selection fails closed for malformed or overlong edits`() {
        assertNull(
            PracticalShoppingHomeOfflineCatalogSelection.replaceUnknownToken(
                rawQuery = "milk dragonfruit",
                unknownToken = "dragon fruit",
                replacementName = "Dragon fruit"
            )
        )
        assertNull(
            PracticalShoppingHomeOfflineCatalogSelection.replaceUnknownToken(
                rawQuery = "milk dragonfruit",
                unknownToken = "dragonfruit",
                replacementName = "x".repeat(240)
            )
        )
        assertNull(
            PracticalShoppingHomeOfflineCatalogSelection.replaceUnknownToken(
                rawQuery = "milk dragonfruit",
                unknownToken = "dragonfruit",
                replacementName = "Fresh\u0000fruit"
            )
        )
    }
}
