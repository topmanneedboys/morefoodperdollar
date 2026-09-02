package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingRequestDetailsSessionBoundaryTest {

    @Test
    fun sessionDelegatesIntentEditsAndLifecycleToExistingBoundaries() {
        val source = source("PracticalShoppingRequestDetailsSession.kt").readText()

        listOf(
            "PracticalShoppingRequestDetailsLifecycleCapsule",
            ".restore(encodedLifecycleState)",
            "detailsForExactRequest(request)",
            "ShoppingRequestDetails(request = request)",
            ".reconciledTo(request)",
            ".withItemDetail(detail)",
            ".withoutItemDetail(itemKey)",
            ".encodedOrNull()"
        ).forEach { required ->
            assertTrue("Expected session delegation $required", source.contains(required))
        }
    }

    @Test
    fun sessionOwnsNoShoppingAuthorityOrPlatformState() {
        val source = source("PracticalShoppingRequestDetailsSession.kt").readText()

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy",
            "Money",
            "NormalizedQuantity",
            "Production",
            "EvidenceClaim",
            "System.currentTimeMillis",
            "System.nanoTime",
            "SharedPreferences",
            "Bundle",
            "android.",
            "java.net",
            "ValueEngine",
            "RankingMode"
        ).forEach { forbidden ->
            assertFalse(
                "Request details session must not own $forbidden",
                source.contains(forbidden, ignoreCase = true)
            )
        }
    }

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/$name"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}
