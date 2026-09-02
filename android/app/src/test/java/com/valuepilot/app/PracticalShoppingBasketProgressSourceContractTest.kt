package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingBasketProgressSourceContractTest {

    @Test
    fun progressSessionOwnsOnlyBoundedTypedForegroundState() {
        val source = source("PracticalShoppingBasketProgressSession.kt").readText()

        listOf(
            "ShoppingItemKey",
            "eligibleItemKeys",
            "collectedItemKeys",
            "MAX_BASKET_PROGRESS_ITEMS",
            "fun reconcile(",
            "fun toggle(",
            "fun restore("
        ).forEach { required ->
            assertTrue("Expected Basket progress boundary $required", source.contains(required))
        }

        listOf(
            "Money",
            "PracticalShoppingPlanner",
            "ShoppingStoreKey",
            "Evidence",
            "CURRENT_PRICE",
            "System.currentTimeMillis",
            "UUID",
            "SharedPreferences",
            "java.io",
            "java.net",
            "android."
        ).forEach { forbidden ->
            assertFalse("Basket progress must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun basketSurfaceDoesNotGainShoppingDecisionAuthority() {
        val source = source("PracticalShoppingBasketSurfaceView.kt").readText()

        listOf(
            "PracticalShoppingBasketProgressSession.reconcile",
            "PracticalShoppingBasketProgressSession.toggle",
            "PracticalShoppingBasketProgressSession.snapshot",
            "PracticalShoppingBasketProgressSession.restore"
        ).forEach { required ->
            assertTrue("Expected typed UI-session use $required", source.contains(required))
        }

        listOf(
            "Money.parse",
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy",
            "SingleStorePlanCandidate",
            "TwoStorePlanCandidate",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "System.currentTimeMillis",
            "UUID",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Basket surface must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun source(fileName: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate =
                File(directory, "app/src/main/java/com/valuepilot/app/$fileName")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $fileName")
    }
}
