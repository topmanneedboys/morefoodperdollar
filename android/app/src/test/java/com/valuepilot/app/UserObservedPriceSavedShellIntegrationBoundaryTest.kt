package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceSavedShellIntegrationBoundaryTest {

    @Test
    fun `saved lifecycle composes observed price launcher selection typed result and passive confirmation draft route`() {
        val source = activitySource().readText()
        val configureSaved = configureSavedBlock(source)

        assertTrue(source.contains("private lateinit var savedObservedPriceLaunchExperience: PracticalShoppingSavedObservedPriceLaunchView"))
        assertTrue(source.contains("private lateinit var observedPriceSavedSelectionExperience: UserObservedPriceSavedSelectionSurfaceView"))
        assertTrue(source.contains("private lateinit var observedPriceSavedPrefillResultExperience:"))
        assertTrue(source.contains("UserObservedPriceSavedPrefillHandoffSurfaceView"))
        assertTrue(source.contains("private lateinit var observedPriceConfirmationDraftExperience:"))
        assertTrue(source.contains("UserObservedPriceConfirmationDraftSurfaceView"))
        assertTrue(source.contains("private lateinit var observedPriceSavedSelectionCoordinator:"))
        assertTrue(source.contains("private lateinit var observedPriceSavedSelectionSurfaceCoordinator:"))
        assertTrue(source.contains("private lateinit var observedPriceSavedPrefillResultSurfaceBinding:"))
        assertTrue(source.contains("private lateinit var observedPriceConfirmationDraftRouteCoordinator:"))
        assertTrue(source.contains("savedObservedPriceLaunchExperience = findViewById(R.id.savedObservedPriceLaunchExperience)"))
        assertTrue(source.contains("observedPriceSavedSelectionExperience = findViewById(R.id.observedPriceSavedSelectionExperience)"))
        assertTrue(
            source.contains(
                "observedPriceSavedPrefillResultExperience =\n            findViewById(R.id.observedPriceSavedPrefillResultExperience)"
            )
        )
        assertTrue(
            source.contains(
                "observedPriceConfirmationDraftExperience =\n            findViewById(R.id.observedPriceConfirmationDraftExperience)"
            )
        )

        assertTrue(configureSaved.contains("PracticalShoppingSavedObservedPriceLaunchPresenter(savedObservedPriceLaunchExperience)"))
        assertTrue(configureSaved.contains("UserObservedPriceSavedPrefillHandoffResultSurfaceBinding("))
        assertTrue(configureSaved.contains("surface = observedPriceSavedPrefillResultExperience"))
        assertTrue(configureSaved.contains("UserObservedPriceSavedSelectionSurfacePresenter(observedPriceSelectionRenderer)"))
        assertTrue(configureSaved.contains("UserObservedPriceConfirmationDraftSurfacePresenter("))
        assertTrue(configureSaved.contains("UserObservedPriceConfirmationDraftRouteShellAdapter("))
        assertTrue(configureSaved.contains("UserObservedPriceSavedConfirmationDraftRouteCoordinator("))
        assertTrue(configureSaved.contains("UserObservedPriceConfirmationDraftRouteSession("))
        assertTrue(configureSaved.contains("UserObservedPriceSavedPrefillHandoffAttemptFanout("))
        assertTrue(configureSaved.contains("resultObserver = observedPriceSavedPrefillResultSurfaceBinding"))
        assertTrue(configureSaved.contains("confirmationDraftObserver = observedPriceConfirmationDraftRouteCoordinator"))
        assertTrue(configureSaved.contains("UserObservedPriceSavedSelectionCompositionCoordinator("))
        assertTrue(configureSaved.contains("prefillHandoffAttemptObserver = observedPricePrefillAttemptFanout"))
        assertTrue(configureSaved.contains("UserObservedPriceSavedSelectionRouteSession("))
        assertTrue(configureSaved.contains("presenter = observedPriceSelectionPresenter"))
        assertTrue(configureSaved.contains("UserObservedPriceSavedSelectionSurfaceCoordinator("))
        assertTrue(configureSaved.contains("compositionCoordinator = observedPriceSavedSelectionCoordinator"))
        assertTrue(configureSaved.contains("observedPriceLaunchPresenter.render(state)"))
        assertTrue(configureSaved.contains("observedPriceSavedSelectionCoordinator.onSnapshot(snapshot)"))
        assertTrue(configureSaved.contains("snapshotObserver = savedSnapshotObserver"))
    }

    @Test
    fun `typed launcher and selection route only identity prefill into passive confirmation draft without downstream authority`() {
        val configureSaved = configureSavedBlock(activitySource().readText())

        assertTrue(configureSaved.contains("savedObservedPriceLaunchExperience.onAction = { action ->"))
        assertTrue(
            configureSaved.contains(
                "PracticalShoppingSavedObservedPriceLaunchAction.OpenObservedPriceSavedSelection ->"
            )
        )
        assertTrue(configureSaved.contains("dispatch(AppShellIntent.OpenObservedPriceSavedSelection)"))
        assertTrue(configureSaved.contains("UserObservedPriceSavedSelectionSurfaceCoordinator("))
        assertTrue(configureSaved.contains("surface = observedPriceSavedSelectionExperience"))
        assertTrue(configureSaved.contains("compositionCoordinator = observedPriceSavedSelectionCoordinator"))
        assertFalse(configureSaved.contains("observedPriceSavedSelectionExperience.onSelectionAction"))
        assertFalse(configureSaved.contains("observedPriceSavedSelectionExperience.onCheckPrefillAction"))

        listOf(
            "requestPrefillOrNull(",
            "UserObservedPriceSavedPrefillHandoffGate",
            "UserObservedPriceSavedPrefillGate",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationExecution",
            "UserObservedPriceConfirmationAndroidSession",
            "UserProvidedPriceProof",
            "UserConfirmedObservedPrice",
            "UserProofBackedObservedPrice",
            "ProductionCurrentPrice",
            ".onArtifactReferenceChanged(",
            ".onObservationReferenceChanged(",
            ".onProductChanged(",
            ".onPriceChanged(",
            ".onStoreScopeChanged(",
            ".onObservedAtChanged(",
            ".onConfirmationChanged(",
            ".currentSubmissionOrNull("
        ).forEach { forbidden ->
            assertFalse(
                "Saved shell integration must not execute downstream authority $forbidden",
                configureSaved.contains(forbidden)
            )
        }
    }

    @Test
    fun `selection rerender clears stale handoff result before rendering current immutable selection state`() {
        val configureSaved = configureSavedBlock(activitySource().readText())
        val rendererBlock =
            configureSaved
                .substringAfter("val observedPriceSelectionRenderer =")
                .substringBefore("val observedPriceSelectionPresenter =")

        val clearIndex = rendererBlock.indexOf("observedPriceSavedPrefillResultSurfaceBinding.clear()")
        val renderIndex = rendererBlock.indexOf("observedPriceSavedSelectionExperience.render(state)")

        assertTrue(clearIndex >= 0)
        assertTrue(renderIndex > clearIndex)
    }

    @Test
    fun `exact shell routes own observed price selection result and confirmation draft visibility`() {
        val source = activitySource().readText()

        assertTrue(
            source.contains(
                "val observedPriceSelectionVisible =\n            state.route == AppRoute.OBSERVED_PRICE_SAVED_SELECTION"
            )
        )
        assertTrue(
            source.contains(
                "val observedPriceConfirmationDraftVisible =\n            state.route == AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT"
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
                "observedPriceConfirmationDraftExperience.visibility =\n            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE"
            )
        )
        assertTrue(
            source.contains(
                "observedPriceSavedSelectionCoordinator.onRouteVisibilityChanged(observedPriceSelectionVisible)"
            )
        )
        assertTrue(
            source.contains(
                "observedPriceSavedPrefillResultSurfaceBinding\n            .onRouteVisibilityChanged(observedPriceSelectionVisible)"
            )
        )
        assertTrue(
            source.contains(
                "observedPriceConfirmationDraftRouteCoordinator\n            .onRouteVisibilityChanged(observedPriceConfirmationDraftVisible)"
            )
        )
        assertFalse(source.contains("observedPriceSavedPrefillResultExperience.visibility = View.VISIBLE"))
        assertTrue(source.contains("shellState.route == AppRoute.OBSERVED_PRICE_SAVED_SELECTION ||"))
        assertTrue(source.contains("shellState.route == AppRoute.OBSERVED_PRICE_CONFIRMATION_DRAFT ||"))
        assertTrue(source.contains("dispatch(AppShellIntent.NavigateBack)"))
    }

    @Test
    fun `activity teardown closes observed price surface result selection and confirmation draft owners`() {
        val source = activitySource().readText()

        assertTrue(source.contains("savedObservedPriceLaunchExperience.onAction = null"))
        assertTrue(source.contains("observedPriceSavedSelectionSurfaceCoordinator.close()"))
        assertTrue(source.contains("observedPriceSavedPrefillResultSurfaceBinding.close()"))
        assertTrue(source.contains("observedPriceConfirmationDraftRouteCoordinator.close()"))
        assertTrue(source.contains("observedPriceSavedSelectionCoordinator.close()"))
        assertFalse(source.contains("observedPriceSavedSelectionExperience.onSelectionAction = null"))
        assertFalse(source.contains("observedPriceSavedSelectionExperience.onCheckPrefillAction = null"))
    }

    @Test
    fun `shell layout contains replaceable observed price launcher selection result and confirmation surfaces hidden by default`() {
        val layout = layoutSource().readText()

        assertTrue(layout.contains("<com.valuepilot.app.PracticalShoppingSavedObservedPriceLaunchView"))
        assertTrue(layout.contains("android:id=\"@+id/savedObservedPriceLaunchExperience\""))
        assertTrue(layout.contains("<com.valuepilot.app.UserObservedPriceSavedSelectionSurfaceView"))
        assertTrue(layout.contains("android:id=\"@+id/observedPriceSavedSelectionExperience\""))
        assertTrue(layout.contains("<com.valuepilot.app.UserObservedPriceSavedPrefillHandoffSurfaceView"))
        assertTrue(layout.contains("android:id=\"@+id/observedPriceSavedPrefillResultExperience\""))
        assertTrue(layout.contains("<com.valuepilot.app.UserObservedPriceConfirmationDraftSurfaceView"))
        assertTrue(layout.contains("android:id=\"@+id/observedPriceConfirmationDraftExperience\""))
        assertTrue(hiddenByDefault(layout, "savedObservedPriceLaunchExperience"))
        assertTrue(hiddenByDefault(layout, "observedPriceSavedSelectionExperience"))
        assertTrue(hiddenByDefault(layout, "observedPriceSavedPrefillResultExperience"))
        assertTrue(hiddenByDefault(layout, "observedPriceConfirmationDraftExperience"))
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
