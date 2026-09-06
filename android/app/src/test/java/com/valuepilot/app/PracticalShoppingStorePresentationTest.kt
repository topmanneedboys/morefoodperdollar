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
        assertEquals(
            "Candidate store: Example Grocer",
            valuePilotCandidateStoreLabel("Example Grocer")
        )
        assertEquals(
            "Candidate store: Example Grocer. Candidate store only — " +
                "product availability is not confirmed.",
            valuePilotCandidateStoreAccessibility("Example Grocer")
        )
    }

    @Test
    fun blankStoreNamesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            practicalShoppingPlannedStoreLabel(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            practicalShoppingPlannedStoreLabel("Sample\u0000Market")
        }
        assertThrows(IllegalArgumentException::class.java) {
            valuePilotCandidateStoreLabel("Example\u0000Grocer")
        }
        assertThrows(IllegalArgumentException::class.java) {
            valuePilotCandidateStoreLabel("x".repeat(161))
        }
    }
}
