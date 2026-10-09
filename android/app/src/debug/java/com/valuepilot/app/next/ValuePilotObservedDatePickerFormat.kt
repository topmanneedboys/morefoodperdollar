package com.valuepilot.app.next

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** DatePicker conversion that preserves the calendar date without inferring the shopper's zone. */
internal object ValuePilotObservedDatePickerFormat {
    fun toEpochMillis(dateText: String): Long? {
        if (!DATE_PATTERN.matches(dateText)) return null
        val formatter = formatter()
        val date = runCatching { formatter.parse(dateText) }.getOrNull() ?: return null
        return date.takeIf { formatter.format(it) == dateText }?.time
    }

    fun fromEpochMillis(dateMillis: Long): String =
        formatter().format(Date(dateMillis))

    private fun formatter() =
        SimpleDateFormat(DATE_PATTERN_TEXT, Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }

    private const val DATE_PATTERN_TEXT = "yyyy-MM-dd"
    private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
}
