package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaSearchAreaPolicyTest {

    @Test
    fun defaultsToTheQualifiedBuenosAiresArea() {
        val area = ArgentinaSearchAreaPolicy.selectedOrDefault(null)
        assertEquals("Buenos Aires (CABA)", area.label)
        assertTrue(area is ArgentinaQualifiedLaunchArea)
    }

    @Test
    fun keepsTheShoppersExplicitArea() {
        val cordoba = ArgentinaUserSearchArea("Córdoba", "-31.42", "-64.19", "30")
        val area = ArgentinaSearchAreaPolicy.selectedOrDefault(cordoba)
        assertEquals("Córdoba", area.label)
        assertEquals("30", area.radiusKm)
        assertEquals(ArgentinaSearchContextSource.EXPLICIT_USER_CONTEXT, area.context().source)
    }

    @Test
    fun emptyResultsExplainTheAreaAndRadius() {
        val hint = ArgentinaSearchAreaPolicy.emptyResultHint(
            ArgentinaUserSearchArea("Salta", "-24.78", "-65.42", "50")
        )
        assertTrue(hint.contains("Salta"))
        assertTrue(hint.contains("50 km"))
        assertTrue(hint.contains("nearby store"))
    }
}
