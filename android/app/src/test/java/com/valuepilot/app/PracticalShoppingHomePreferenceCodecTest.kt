package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticalShoppingHomePreferenceCodecTest {

    @Test
    fun knownChoiceRoundTripsAsAnExactEnumName() {
        val choice =
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.TWENTY_FIVE_CAD

        assertEquals(
            "TWENTY_FIVE_CAD",
            PracticalShoppingHomePreferenceCodec.encode(choice)
        )
        assertEquals(choice, PracticalShoppingHomePreferenceCodec.decode("TWENTY_FIVE_CAD"))
    }

    @Test
    fun absentOrUnknownStoredValueFallsBackToTheExplicitDefault() {
        val defaultChoice =
            LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice.DEFAULT

        assertEquals(defaultChoice, PracticalShoppingHomePreferenceCodec.decode(null))
        assertEquals(defaultChoice, PracticalShoppingHomePreferenceCodec.decode("future-choice"))
        assertEquals(defaultChoice, PracticalShoppingHomePreferenceCodec.decode(""))
    }
}
