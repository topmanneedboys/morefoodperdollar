package com.valuepilot.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the normal shopping surfaces in the Stage 9 vocabulary boundary.
 * Provider names and diagnostic terminology remain allowed in the deeper
 * Settings/licence and internal contract layers; this test only protects the
 * copy that a shopper sees in the primary flow.
 */
class FinalConsumerCopyAuditTest {

    @Test
    fun `primary copy avoids retired technical and prototype labels`() {
        val strings = source("src/main/res/values/strings.xml").readText()
        val normalised = strings.lowercase()

        listOf(
            "argentina live search",
            "qualified argentina identity release",
            "qualified argentina release",
            "offline product identities",
            "scan &amp; compare",
            "private comparison history",
            "remembered prices",
            "quick answers",
            "plan ready",
            "planned item",
            "evidence below",
            "📍"
        ).forEach { retired ->
            assertFalse("Retired shopper-facing copy remains: $retired", normalised.contains(retired))
        }
    }

    @Test
    fun `search and basket copy expose the calm primary vocabulary`() {
        val strings = source("src/main/res/values/strings.xml").readText()

        listOf(
            "<string name=\"search_eyebrow\">PRODUCT SEARCH • IDENTITY ONLY</string>",
            "<string name=\"search_identity_badge\">PUBLIC ARGENTINA CATALOG</string>",
            "<string name=\"search_identity_view_map\">Nearby</string>",
            "<string name=\"search_identity_view_photos\">Photos</string>",
            "<string name=\"search_identity_open_compare\">Open Compare</string>",
            "<string name=\"basket_body\">Review the shopping list from Home, including coverage, travel, what is known, and any optional extra stop.</string>"
        ).forEach { expected ->
            assertTrue("Expected consumer copy is missing: $expected", strings.contains(expected))
        }
    }

    private fun source(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/$relativePath")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}
