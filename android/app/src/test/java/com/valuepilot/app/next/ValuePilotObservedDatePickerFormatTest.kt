package com.valuepilot.app.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValuePilotObservedDatePickerFormatTest {

    @Test
    fun `calendar selection round trips through UTC without a local zone assumption`() {
        val dateMillis = ValuePilotObservedDatePickerFormat.toEpochMillis("2024-02-29")

        assertEquals(
            "2024-02-29",
            ValuePilotObservedDatePickerFormat.fromEpochMillis(requireNotNull(dateMillis))
        )
    }

    @Test
    fun `blank and invalid typed dates do not select a default calendar date`() {
        assertNull(ValuePilotObservedDatePickerFormat.toEpochMillis(""))
        assertNull(ValuePilotObservedDatePickerFormat.toEpochMillis("2024-02-30"))
    }
}
