package com.valuepilot.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityHomeLifecycleBoundaryTest {

    @Test
    fun destroyingActivityReleasesHomeAndBasketOwners() {
        val source = source().readText()

        listOf(
            "if (::homeExperience.isInitialized)",
            "homeExperience.onQueryChanged = null",
            "homeExperience.onSubmit = null",
            "homeExperience.onRemoveItem = null",
            "homeExperience.onRemoveUnknownItem = null",
            "homeExperience.onChickenChoice = null",
            "homeExperience.onExtraStopMinimumSavingsChoice = null",
            "homeExperience.onEditItemDetails = null",
            "homeExperience.onCompare = null",
            "if (::basketExperience.isInitialized)",
            "basketExperience.onAction = null"
        ).forEach { required ->
            assertTrue("Expected detached Home/Basket owner cleanup: $required", source.contains(required))
        }
    }

    private fun source(): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/MainActivity.kt"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}
