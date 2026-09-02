package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingHomeDetailsAndroidBindingTest {

    @Test
    fun activityStoresHomeDetailsOnlyThroughTheTypedSessionBoundary() {
        val source = source().readText()

        listOf(
            "private var homeSessionState = PracticalShoppingHomeSession.initialState()",
            "homeSessionState = restoreHomeState(savedInstanceState)",
            "PracticalShoppingHomeSession.snapshot(homeSessionState)",
            "outState.putByteArray(STATE_HOME_REQUEST_DETAILS, encoded)",
            "savedInstanceState?.getByteArray(STATE_HOME_REQUEST_DETAILS)",
            "PracticalShoppingHomeSession.restoreState(",
            "PracticalShoppingHomeSession.queryChanged(homeSessionState, rawQuery)",
            "PracticalShoppingHomeSession.submit(homeSessionState, rawQuery)",
            "homeExperience.onEditItemDetails",
            "showHomeItemDetails(itemKey)",
            "PracticalShoppingHomeItemDetailsEditor.apply(",
            "PracticalShoppingHomeSession.withItemDetail(",
            "PracticalShoppingHomeSession.withoutItemDetail("
        ).forEach { required ->
            assertTrue("Expected Home lifecycle binding $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingRequestDetailsLifecycleCapsule",
            "ShoppingRequestDetailsCodec",
            "ShoppingItemRequestDetail(",
            "ShoppingRequestedQuantity(",
            "ShoppingBrandKey(",
            "PracticalShoppingPlanner",
            "Money.parse"
        ).forEach { forbidden ->
            assertFalse("MainActivity must not own Home item details through $forbidden", source.contains(forbidden))
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
