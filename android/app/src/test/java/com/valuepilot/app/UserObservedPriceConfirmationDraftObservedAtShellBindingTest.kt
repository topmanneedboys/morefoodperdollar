package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftObservedAtShellBindingTest {

    @Test
    fun `shell mechanically binds explicit observed time surface to route coordinator`() {
        val source = source("MainActivity.kt").readSourceText()

        listOf(
            "private lateinit var observedPriceConfirmationDraftObservedAtInputExperience:",
            "UserObservedPriceConfirmationDraftObservedAtInputSurfaceView",
            "findViewById(R.id.observedPriceConfirmationDraftObservedAtInputExperience)",
            "observedPriceConfirmationDraftObservedAtInputExperience.onCommit =\n            observedPriceConfirmationDraftRouteCoordinator::onObservedAtInput",
            "observedPriceConfirmationDraftObservedAtInputExperience.onCommit = null"
        ).forEach { required ->
            assertTrue("Expected observed-time shell binding $required", source.contains(required))
        }

        assertFalse(
            "Shell must not mutate the route session directly",
            source.contains(".onObservedAtChanged(")
        )
    }

    @Test
    fun `observed time surface is visible only on exact draft route and raw text clears on exit`() {
        val source = source("MainActivity.kt").readSourceText()
        val renderShell =
            source.substring(
                source.indexOf("private fun renderShell(state: AppShellState)"),
                source.indexOf("private fun renderSearch(state: UniversalSearchState)")
            )

        assertTrue(
            renderShell.contains(
                "observedPriceConfirmationDraftObservedAtInputExperience.visibility =\n" +
                    "            if (observedPriceConfirmationDraftVisible) View.VISIBLE else View.GONE"
            )
        )
        assertTrue(
            renderShell.contains(
                "if (!observedPriceConfirmationDraftVisible) {\n" +
                    "            observedPriceConfirmationDraftObservedAtInputExperience.clearInput()\n" +
                    "        }"
            )
        )

        assertTrue(
            "Keep the established price and proof-reference exit block source-compatible",
            renderShell.contains(
                "if (!observedPriceConfirmationDraftVisible) {\n" +
                    "            observedPriceConfirmationDraftPriceInputExperience.clearInput()\n" +
                    "            observedPriceConfirmationDraftProofReferenceInputExperience.clearInput()\n" +
                    "        }"
            )
        )
    }

    @Test
    fun `shell composition owns no observed time parsing clock identity or evidence authority`() {
        val source = source("MainActivity.kt").readSourceText()
        val configureSaved =
            source.substring(
                source.indexOf("private fun configureSavedUi()"),
                source.indexOf("private fun configureQuickSearch")
            )

        listOf(
            "UserObservedPriceConfirmationDraftObservedAtTextInputAdapter",
            "GregorianCalendar",
            "SimpleTimeZone",
            "TimeZone.getDefault",
            "Calendar.getInstance",
            "System.currentTimeMillis",
            "System.nanoTime",
            "Instant.now",
            "LocalDateTime.now",
            "UUID",
            "MessageDigest",
            "onObservationReferenceChanged(",
            "onConfirmationChanged(",
            "UserObservedPriceConfirmationDraftSubmissionHandoff",
            "UserObservedPriceConfirmationTransaction",
            "UserProvidedPriceProofArtifactLocalStore",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "ProductionCurrentPrice"
        ).forEach { forbidden ->
            assertFalse("Observed-time shell composition must not own $forbidden", configureSaved.contains(forbidden))
        }
    }

    @Test
    fun `layout hosts one hidden observed time input between price and proof reference`() {
        val layout = layout().readSourceText()
        val price = layout.indexOf("UserObservedPriceConfirmationDraftPriceInputSurfaceView")
        val observedAt = layout.indexOf("UserObservedPriceConfirmationDraftObservedAtInputSurfaceView")
        val proofReference = layout.indexOf("UserObservedPriceConfirmationDraftProofReferenceInputSurfaceView")

        assertTrue(price >= 0)
        assertTrue(observedAt > price)
        assertTrue(proofReference > observedAt)

        val observedAtBlock =
            layout.substring(
                observedAt,
                layout.indexOf("/>", observedAt) + 2
            )
        assertTrue(observedAtBlock.contains("android:id=\"@+id/observedPriceConfirmationDraftObservedAtInputExperience\""))
        assertTrue(observedAtBlock.contains("android:visibility=\"gone\""))
    }

    private fun source(fileName: String): File =
        appFile("app/src/main/java/com/valuepilot/app/$fileName")

    private fun layout(): File =
        appFile("app/src/main/res/layout/activity_shell.xml")

    private fun appFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}
