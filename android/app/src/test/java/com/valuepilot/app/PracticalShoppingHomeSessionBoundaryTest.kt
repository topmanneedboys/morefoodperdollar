package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingHomeSessionBoundaryTest {

    @Test
    fun homeSessionDelegatesDetailsAndKeepsPlanningOutsideItsBoundary() {
        val source = source().readText()

        listOf(
            "PracticalShoppingRequestDetailsSession.initial",
            "PracticalShoppingRequestDetailsSession.reconcileTo",
            "PracticalShoppingRequestDetailsSession.withItemDetail",
            "PracticalShoppingRequestDetailsSession.withoutItemDetail",
            "encodedOrNull(state.requestDetails)",
            "let(::ShoppingRequest)"
        ).forEach { required ->
            assertTrue("Expected Home details boundary $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy(",
            "Money.parse",
            "ValueEngine",
            "DeterministicRankingEngine",
            "ProductionCurrentPrice",
            "EvidenceAcceptancePolicy"
        ).forEach { forbidden ->
            assertFalse("Home session must not own planning through $forbidden", source.contains(forbidden))
        }
    }

    private fun source(): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/PracticalShoppingHomeSession.kt"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}
