package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingSavedAndroidSessionSnapshotObserverBoundaryTest {

    @Test
    fun androidSessionFactoryExposesOptionalGenericSnapshotObserverAndPassesItToHost() {
        val source = source("PracticalShoppingSavedAndroidSession.kt").readText()

        assertTrue(
            source.contains(
                "snapshotObserver: PracticalShoppingSavedValidatedSnapshotObserver ="
            )
        )
        assertTrue(source.contains("PracticalShoppingSavedValidatedSnapshotObserver { }"))
        assertTrue(source.contains("snapshotObserver = snapshotObserver"))
        assertTrue(source.contains("renderer = renderer"))
        assertTrue(source.indexOf("renderer: PracticalShoppingSavedLifecycleRenderer") < source.indexOf("snapshotObserver: PracticalShoppingSavedValidatedSnapshotObserver"))
    }

    @Test
    fun androidSessionObserverExposureStaysSavedGenericAndOwnsNoShoppingDecisionAuthority() {
        val source = source("PracticalShoppingSavedAndroidSession.kt").readText()

        listOf(
            "StapleWatch",
            "StapleWatchEconomicEvaluator",
            "StapleWatchEconomicDecision",
            "ShoppingRequest",
            "ShoppingItemKey",
            "ShoppingStoreKey",
            "Money(",
            "WorkManager",
            "NotificationManager",
            "System.currentTimeMillis",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        ).forEach { forbidden ->
            assertFalse("Saved Android session must not own $forbidden", source.contains(forbidden))
        }

        assertTrue(source.contains("PracticalShoppingSavedProcessRuntime"))
        assertTrue(source.contains("PracticalShoppingSavedMainLooperDispatcher"))
        assertTrue(source.contains("PracticalShoppingSavedLocalExperienceGateway"))
    }

    @Test
    fun routeSessionAndPhysicalSavedRendererContractsRemainUnchanged() {
        val routeMethods =
            PracticalShoppingSavedRouteSession::class.java.declaredMethods
                .map { method -> method.name }
                .toSet()
        assertEquals(setOf("refresh", "selectAction", "close"), routeMethods)

        val renderMethods =
            PracticalShoppingSavedLifecycleRenderer::class.java.declaredMethods
                .filter { method -> method.name == "render" }
        assertEquals(1, renderMethods.size)
        assertEquals(
            listOf(PracticalShoppingSavedLifecycleState::class.java),
            renderMethods.single().parameterTypes.toList()
        )

        val routeSource = source("PracticalShoppingSavedRouteCoordinator.kt").readText()
        assertFalse(routeSource.contains("PracticalShoppingSavedValidatedSnapshot"))
        assertFalse(routeSource.contains("StapleWatch"))
    }

    private fun source(name: String): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) { "Missing user.dir for source boundary test" }
        return File(workingDirectory, "src/main/java/com/valuepilot/app/$name").also {
            assertTrue("Missing source $name at ${it.absolutePath}", it.isFile)
        }
    }
}
