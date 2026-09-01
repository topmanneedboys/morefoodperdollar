package com.valuepilot.app

import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.SimpleTimeZone

/** Raw factual observation date, time, and UTC offset supplied explicitly by the user. */
internal data class UserObservedPriceConfirmationDraftObservedAtTextInput(
    val dateText: String,
    val timeText: String,
    val utcOffsetText: String
)

internal enum class UserObservedPriceConfirmationDraftObservedAtTextInputFailure {
    BLANK_DATE,
    BLANK_TIME,
    BLANK_UTC_OFFSET,
    INVALID_DATE_FORMAT,
    INVALID_TIME_FORMAT,
    INVALID_UTC_OFFSET,
    INVALID_DATE_TIME
}

internal sealed interface UserObservedPriceConfirmationDraftObservedAtTextInputResult {
    data class Success(
        val observedAtEpochMillis: Long
    ) : UserObservedPriceConfirmationDraftObservedAtTextInputResult

    data class Failure(
        val reason: UserObservedPriceConfirmationDraftObservedAtTextInputFailure
    ) : UserObservedPriceConfirmationDraftObservedAtTextInputResult
}

/**
 * Pure explicit civil-time-to-epoch boundary for one observed-price confirmation draft.
 *
 * Date, time, and UTC offset are all supplied by the user. This adapter never reads the device
 * clock, device timezone, locale timezone, store geography, saved-item metadata, or a previous
 * edit. A fixed numeric offset therefore has the same meaning on every device and avoids silently
 * guessing daylight-saving rules for a historical receipt or price-tag observation.
 *
 * Accepted date syntax is `YYYY-MM-DD`. Accepted time syntax is `HH:MM` or `HH:MM:SS` using a
 * 24-hour clock. The UTC offset must be `Z` or `+/-HH:MM`, limited to the ISO-8601 offset range
 * through +/-14:00. `-00:00` is rejected because it can represent an unknown offset rather than a
 * known UTC offset. Calendar validation is strict and uses a fixed-offset proleptic Gregorian
 * calendar, so impossible dates such as February 30 fail closed.
 *
 * This adapter owns syntax and exact civil-time conversion only. It does not decide whether an
 * observation timestamp is semantically plausible, generate observation/confirmation identifiers,
 * create a confirmation timestamp, mutate a draft/session, persist proof, create evidence, claim a
 * current price, rank offers, or perform networking.
 */
internal object UserObservedPriceConfirmationDraftObservedAtTextInputAdapter {

    fun adapt(
        input: UserObservedPriceConfirmationDraftObservedAtTextInput
    ): UserObservedPriceConfirmationDraftObservedAtTextInputResult {
        val date = input.dateText.trim()
        if (date.isEmpty()) {
            return failure(UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_DATE)
        }

        val time = input.timeText.trim()
        if (time.isEmpty()) {
            return failure(UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_TIME)
        }

        val utcOffset = input.utcOffsetText.trim()
        if (utcOffset.isEmpty()) {
            return failure(
                UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_UTC_OFFSET
            )
        }

        val dateMatch = DATE.matchEntire(date)
            ?: return failure(
                UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_DATE_FORMAT
            )
        val timeMatch = TIME.matchEntire(time)
            ?: return failure(
                UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_TIME_FORMAT
            )

        val offsetMillis = parseOffsetMillis(utcOffset)
            ?: return failure(
                UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_UTC_OFFSET
            )

        val year = dateMatch.groupValues[1].toInt()
        val month = dateMatch.groupValues[2].toInt()
        val day = dateMatch.groupValues[3].toInt()
        val hour = timeMatch.groupValues[1].toInt()
        val minute = timeMatch.groupValues[2].toInt()
        val second = timeMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0

        if (year == 0) {
            return failure(
                UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_DATE_TIME
            )
        }

        val calendar =
            GregorianCalendar(
                SimpleTimeZone(offsetMillis, EXPLICIT_OFFSET_ZONE_ID),
                Locale.ROOT
            ).apply {
                isLenient = false
                gregorianChange = Date(Long.MIN_VALUE)
                clear()
                set(year, month - 1, day, hour, minute, second)
                set(GregorianCalendar.MILLISECOND, 0)
            }

        val epochMillis =
            try {
                calendar.timeInMillis
            } catch (_: IllegalArgumentException) {
                return failure(
                    UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_DATE_TIME
                )
            }

        return UserObservedPriceConfirmationDraftObservedAtTextInputResult.Success(
            observedAtEpochMillis = epochMillis
        )
    }

    private fun parseOffsetMillis(raw: String): Int? {
        if (raw == "Z") return 0
        val match = OFFSET.matchEntire(raw) ?: return null

        val sign = if (match.groupValues[1] == "+") 1 else -1
        val hours = match.groupValues[2].toInt()
        val minutes = match.groupValues[3].toInt()
        if (hours > 14 || minutes > 59 || (hours == 14 && minutes != 0)) return null
        if (sign < 0 && hours == 0 && minutes == 0) return null

        val totalMinutes = sign * (hours * 60 + minutes)
        return totalMinutes * MILLIS_PER_MINUTE
    }

    private fun failure(
        reason: UserObservedPriceConfirmationDraftObservedAtTextInputFailure
    ): UserObservedPriceConfirmationDraftObservedAtTextInputResult.Failure =
        UserObservedPriceConfirmationDraftObservedAtTextInputResult.Failure(reason)

    private val DATE = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})")
    private val TIME = Regex("([0-9]{2}):([0-9]{2})(?::([0-9]{2}))?")
    private val OFFSET = Regex("([+-])([0-9]{2}):([0-9]{2})")
    private const val MILLIS_PER_MINUTE = 60 * 1_000
    private const val EXPLICIT_OFFSET_ZONE_ID = "valuepilot-explicit-user-offset"
}
