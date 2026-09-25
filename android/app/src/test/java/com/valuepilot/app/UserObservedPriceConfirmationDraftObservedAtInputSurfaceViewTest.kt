package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftObservedAtInputSurfaceViewTest {

    @Test
    fun `physical observed time editor starts inactive and delegates exact raw text through adapter`() {
        val source = source().readText()

        listOf(
            "class UserObservedPriceConfirmationDraftObservedAtInputSurfaceView",
            "visibility = View.GONE",
            "isSaveEnabled = false",
            "private val ownerBoundControls = mutableListOf<View>()",
            "var onCommit: ((Long) -> Unit)? = null",
            "ownerBoundControls.forEach { control ->",
            "control.isEnabled = value != null",
            "UserObservedPriceConfirmationDraftObservedAtTextInputAdapter.adapt(",
            "dateText = dateEditor.text.toString()",
            "timeText = timeEditor.text.toString()",
            "utcOffsetText = utcOffsetEditor.text.toString()",
            "ownerBoundControls += this",
            "onCommit?.invoke(result.observedAtEpochMillis)",
            "fun clearInput()",
            "dateEditor.setText(\"\")",
            "timeEditor.setText(\"\")",
            "utcOffsetEditor.setText(\"\")"
        ).forEach { required ->
            assertTrue("Expected explicit observed-time input binding $required", source.contains(required))
        }

        assertFalse("Date must not be prefilled", source.contains("dateEditor.setText(\"2026-"))
        assertFalse("Time must not be prefilled", source.contains("timeEditor.setText(\"12:"))
        assertFalse("Offset must not be defaulted", source.contains("utcOffsetEditor.setText(\"-04:00\")"))
        assertFalse("UTC must not be defaulted", source.contains("utcOffsetEditor.setText(\"Z\")"))
    }

    @Test
    fun `physical observed time editor owns no clock timezone inference draft or downstream authority`() {
        val source = source().readText()

        listOf(
            "System.currentTimeMillis",
            "System.nanoTime",
            "Calendar.getInstance",
            "TimeZone.getDefault",
            "ZoneId.systemDefault",
            "LocalDateTime.now",
            "Instant.now",
            "GregorianCalendar(",
            "SimpleTimeZone(",
            "PracticalShoppingStoreIdentityScope",
            "merchantKey",
            "locationKey",
            "UserObservedPriceConfirmationDraftRouteSession",
            "UserObservedPriceConfirmationDraftFinalizer",
            "UserObservedPriceConfirmationTransaction",
            "UserObservedPriceConfirmationAndroidSession",
            "UserProvidedPriceProofArtifact",
            "UUID",
            "MessageDigest",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "ProductionCurrentPrice",
            "ProviderProductionAuthorization",
            "OcrScanner",
            "Bitmap",
            "SharedPreferences",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Observed-time input surface must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `surface copy requires explicit factual offset and does not offer a now shortcut`() {
        val source = source().readText()

        assertTrue(source.contains("UTC offset: for example -04:00 or Z"))
        assertTrue(source.contains("ValuePilot does not use your device clock or timezone to fill this in."))
        assertTrue(source.contains("same observation means the same instant on every device"))

        listOf(
            "Use current time",
            "Use device time",
            "Use local timezone",
            "Set to now",
            "Today",
            "Current time"
        ).forEach { forbiddenCopy ->
            assertFalse("Surface must not offer inferred factual input: $forbiddenCopy", source.contains(forbiddenCopy))
        }
    }

    private fun source(): File =
        appFile(
            "app/src/main/java/com/valuepilot/app/UserObservedPriceConfirmationDraftObservedAtInputSurfaceView.kt"
        )

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
