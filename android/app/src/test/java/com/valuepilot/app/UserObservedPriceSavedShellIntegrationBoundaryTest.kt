package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedShellIntegrationBoundaryTest {

    @Test
    fun `saved lifecycle composes observed price launcher and validated selection without downstream authority`() {
        val source = activitySource().readText()
        val configureSaved = configureSavedBlock(source)

        assertTrue(source.contains("private lateinit var savedObservedPriceLaunchExperience: PracticalShoppingSavedObservedPriceLaunchView"))
        assertTrue(source.contains("private lateinit var observedPriceSavedSelectionExperience: UserObservedPriceSavedSelectionSurfaceView"))
        assertTrue(source.contains("private lateinit var observedPriceSavedSelectionCoordinator:"))
        assertTrue(source.contains("savedObservedPriceLaunchExperience = findViewById(R.id.savedObservedPriceLaunchExperience)"))
        assertTrue(source.contains("observedPriceSavedSelectionExperience = findViewById(R.id.observedPriceSavedSelectionExperience)"))

        assertTrue(configureSaved.contains("PracticalShoppingSavedObservedPriceLaunchPresenter(savedObservedPriceLaunchExperience)"))
        assertTrue(configureSaved.contains("UserObservedPriceSavedSelectionSurfacePresenter(observedPriceSavedSelectionExperience)"))
        assertTrue(configureSaved.contains("UserObservedPriceSavedSelectionCompositionCoordinator { snapshot ->"))
        assertTrue(configureSaved.contains("UserObservedPriceSavedSelectionRouteSession("))
        assertTrue(configureSaved.contains("presenter = observedPriceSelectionPresenter"))
        assertTrue(configureSaved.contains("observedPriceLaunchPresenter.render(state)"))
        assertTrue(configureSaved.contains("observedPriceSavedSelectionCoordinator.onSnapshot(snapshot)"))
        assertTrue(configureSaved.contains("snapshotObserver = savedSnapshotObserver"))
    }

    @Test
    fun `typed launcher and selection actions are wired without executing prefill`() {
        val configureSaved = configureSavedBlock(activitySource().readText())

        assertTrue(configureSaved.contains("savedObservedPriceLaunchExperience.onAction = { action ->"))
        assertTrue(
            configureSaved.contains(
                "PracticalShoppingSavedObservedPriceLaunchAction.OpenObservedPriceSavedSelection ->"
            )
        )
        assertTrue(configureSaved.contains("dispatch(AppShellIntent.OpenObservedPriceSavedSelection)"))
        assertTrue(
            configureSaved.contains(
                "observedPriceSavedSelectionExperience.onSelectionAction =\n            observedPriceSavedSelectionCoordinator::onSurfaceAction"
            )
        )
        assertTrue(configureSaved.contains("observedPriceSavedSelectionExperience.onCheckPrefillAction = null"))

        listOf(
            "requestPrefillOrNull(",
            "UserObservedPriceSavedPrefillHandoffGate",
            "UserObservedPriceSavedPrefillGate",
            "UserObservedPriceConfirmationDraft",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationExecution",
            "UserObservedPriceConfirmationAndroidSession",
            "UserProvidedPriceProof",
            "UserConfirmedObservedPrice",
            "UserProofBackedObservedPrice",
            "ProductionCurrentPrice"
        ).forEach { forbidden ->
            assertFalse(
                "Saved shell integration must not execute downstream authority $forbidden",
                configureSaved.contains(forbidden)
            )
        }
    }

    @Test
    fun `exact shell route owns observed price selection visibility and android back`() {
        val source = activitySource().readText()

        assertTrue(
            source.contains(
                "val observedPriceSelectionVisible =\n            state.route == AppRoute.OBSERVED_PRICE_SAVED_SELECTION"
            )
        )
        assertTrue(source.contains("savedObservedPriceLaunchExperience.visibility = if (savedVisible) View.VISIBLE else View.GONE"))
        assertTrue(
            source.contains(
                "observedPriceSavedSelectionExperience.visibility =\n            if (observedPriceSelectionVisible) View.VISIBLE else View.GONE"
            )
        )
        assertTrue(
            source.contains(
                "observedPriceSavedSelectionCoordinator.onRouteVisibilityChanged(observedPriceSelectionVisible)"
            )
        )
        assertTrue(source.contains("shellState.route == AppRoute.OBSERVED_PRICE_SAVED_SELECTION ||"))
        assertTrue(source.contains("dispatch(AppShellIntent.NavigateBack)"))
    }

    @Test
    fun `activity teardown disconnects observed price shell callbacks and closes selection coordinator`() {
        val source = activitySource().readText()

        assertTrue(source.contains("savedObservedPriceLaunchExperience.onAction = null"))
        assertTrue(source.contains("observedPriceSavedSelectionExperience.onSelectionAction = null"))
        assertTrue(source.contains("observedPriceSavedSelectionExperience.onCheckPrefillAction = null"))
        assertTrue(source.contains("observedPriceSavedSelectionCoordinator.close()"))
    }

    @Test
    fun `shell layout contains replaceable observed price launcher and selection surfaces hidden by default`() {
        val layout = layoutSource().readText()

        assertTrue(layout.contains("<com.valuepilot.app.PracticalShoppingSavedObservedPriceLaunchView"))
        assertTrue(layout.contains("android:id=\"@+id/savedObservedPriceLaunchExperience\""))
        assertTrue(layout.contains("<com.valuepilot.app.UserObservedPriceSavedSelectionSurfaceView"))
        assertTrue(layout.contains("android:id=\"@+id/observedPriceSavedSelectionExperience\""))
        assertTrue(hiddenByDefault(layout, "savedObservedPriceLaunchExperience"))
        assertTrue(hiddenByDefault(layout, "observedPriceSavedSelectionExperience"))
    }

    private fun configureSavedBlock(source: String): String =
        source
            .substringAfter("private fun configureSavedUi() {")
            .substringBefore("private fun configureQuickSearch")

    private fun hiddenByDefault(layout: String, id: String): Boolean =
        layout
            .substringAfter("android:id=\"@+id/$id\"")
            .substringBefore("/>")
            .contains("android:visibility=\"gone\"")

    private fun activitySource(): File {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/java/com/valuepilot/app/MainActivity.kt").also {
            assertTrue("Missing MainActivity.kt at ${it.absolutePath}", it.isFile)
        }
    }

    private fun layoutSource(): File {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/res/layout/activity_shell.xml").also {
            assertTrue("Missing activity_shell.xml at ${it.absolutePath}", it.isFile)
        }
    }
}
