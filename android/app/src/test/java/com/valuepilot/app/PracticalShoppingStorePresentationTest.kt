package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingStorePresentationTest {

    @Test
    fun plannedStoreCopyKeepsTheAvailabilityBoundaryExplicit() {
        assertEquals(
            "Planned store: Sample Market",
            practicalShoppingPlannedStoreLabel("Sample Market")
        )
        assertEquals(
            "Planned store: Sample Market. " +
                "Planned stop only — product availability is not confirmed.",
            practicalShoppingPlannedStoreAccessibility("Sample Market")
        )
        assertTrue(PRACTICAL_SHOPPING_PLANNED_STORE_NOTICE.contains("not confirmed"))
    }

    @Test
    fun blankStoreNamesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            practicalShoppingPlannedStoreLabel(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            practicalShoppingPlannedStoreLabel("Sample\u0000Market")
        }
    }
}
