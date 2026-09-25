package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationDraftObservedAtTextInputAdapterTest {

    @Test
    fun `explicit negative offset converts exact civil time without device timezone inference`() {
        val result = adapt("2026-09-01", "14:30", "-04:00")

        assertEquals(
            UserObservedPriceConfirmationDraftObservedAtTextInputResult.Success(
                observedAtEpochMillis = 1_788_287_400_000L
            ),
            result
        )
    }

    @Test
    fun `Z and explicit seconds preserve exact observation instant`() {
        val result = adapt("2026-09-01", "18:30:45", "Z")

        assertEquals(
            UserObservedPriceConfirmationDraftObservedAtTextInputResult.Success(
                observedAtEpochMillis = 1_788_287_445_000L
            ),
            result
        )
    }

    @Test
    fun `positive half hour offset and leap day are validated exactly`() {
        val result = adapt("2024-02-29", "23:59", "+05:30")

        assertEquals(
            UserObservedPriceConfirmationDraftObservedAtTextInputResult.Success(
                observedAtEpochMillis = 1_709_231_340_000L
            ),
            result
        )
    }

    @Test
    fun `all three factual time components are mandatory`() {
        assertFailure(
            adapt(" ", "14:30", "-04:00"),
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_DATE
        )
        assertFailure(
            adapt("2026-09-01", " ", "-04:00"),
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_TIME
        )
        assertFailure(
            adapt("2026-09-01", "14:30", " "),
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_UTC_OFFSET
        )
    }

    @Test
    fun `locale shaped or incomplete date time text fails closed`() {
        assertFailure(
            adapt("09/01/2026", "14:30", "-04:00"),
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_DATE_FORMAT
        )
        assertFailure(
            adapt("2026-9-1", "14:30", "-04:00"),
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_DATE_FORMAT
        )
        assertFailure(
            adapt("2026-09-01", "2:30 PM", "-04:00"),
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_TIME_FORMAT
        )
        assertFailure(
            adapt("2026-09-01", "14:30.500", "-04:00"),
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_TIME_FORMAT
        )
    }

    @Test
    fun `impossible calendar values fail strict validation`() {
        listOf(
            Triple("2026-02-29", "14:30", "-04:00"),
            Triple("2024-02-30", "14:30", "-04:00"),
            Triple("2026-13-01", "14:30", "-04:00"),
            Triple("2026-09-01", "24:00", "-04:00"),
            Triple("2026-09-01", "14:60", "-04:00"),
            Triple("0000-01-01", "00:00", "Z")
        ).forEach { (date, time, offset) ->
            assertFailure(
                adapt(date, time, offset),
                UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_DATE_TIME
            )
        }
    }

    @Test
    fun `offset must be explicit bounded and semantically known`() {
        listOf(
            "UTC",
            "EST",
            "+4:00",
            "+04",
            "+14:01",
            "+15:00",
            "+05:60",
            "-00:00"
        ).forEach { offset ->
            assertFailure(
                adapt("2026-09-01", "14:30", offset),
                UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_UTC_OFFSET
            )
        }

        assertTrue(adapt("2026-09-01", "14:30", "+14:00") is
            UserObservedPriceConfirmationDraftObservedAtTextInputResult.Success)
        assertTrue(adapt("2026-09-01", "14:30", "+00:00") is
            UserObservedPriceConfirmationDraftObservedAtTextInputResult.Success)
    }

    @Test
    fun `adapter has no clock timezone identity draft proof submission or network authority`() {
        val source = source().readText()

        listOf(
            "System.currentTimeMillis",
            "System.nanoTime",
            "TimeZone.getDefault",
            "Locale.getDefault",
            "ZoneId.systemDefault",
            "Instant.now",
            "Clock.",
            "UUID",
            "MainActivity",
            "UserObservedPriceConfirmationDraftRouteSession",
            "UserObservedPriceConfirmationDraftSubmissionHandoff",
            "UserObservedPriceConfirmationTransaction",
            "UserProvidedPriceProofArtifact",
            "UserProvidedPriceProofArtifactLocalStore",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "CURRENT_PRICE",
            "android.content",
            "android.view",
            "java.net"
        ).forEach { forbidden ->
            assertFalse("Observed-at adapter must not own $forbidden", source.contains(forbidden))
        }

        assertTrue(source.contains("SimpleTimeZone(offsetMillis"))
        assertTrue(source.contains("isLenient = false"))
        assertTrue(source.contains("gregorianChange = Date(Long.MIN_VALUE)"))
    }

    private fun adapt(
        date: String,
        time: String,
        offset: String
    ): UserObservedPriceConfirmationDraftObservedAtTextInputResult =
        UserObservedPriceConfirmationDraftObservedAtTextInputAdapter.adapt(
            UserObservedPriceConfirmationDraftObservedAtTextInput(
                dateText = date,
                timeText = time,
                utcOffsetText = offset
            )
        )

    private fun assertFailure(
        result: UserObservedPriceConfirmationDraftObservedAtTextInputResult,
        expected: UserObservedPriceConfirmationDraftObservedAtTextInputFailure
    ) {
        assertEquals(
            UserObservedPriceConfirmationDraftObservedAtTextInputResult.Failure(expected),
            result
        )
    }

    private fun source(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate =
                File(
                    directory,
                    "app/src/main/java/com/valuepilot/app/" +
                        "UserObservedPriceConfirmationDraftObservedAtTextInputAdapter.kt"
                )
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate observed-at adapter source")
    }
}
