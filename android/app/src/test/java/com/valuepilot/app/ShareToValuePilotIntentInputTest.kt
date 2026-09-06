package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareToValuePilotIntentInputTest {

    @Test
    fun `explicit stream uri wins over clipdata`() {
        assertEquals(
            "content://photos/extra",
            ShareToValuePilotIntentInput.chooseSingleUri(
                extraUri = "content://photos/extra",
                clipItemCount = 1,
                clipUri = "content://photos/clip"
            )
        )
    }

    @Test
    fun `single clipdata uri is accepted when stream extra is absent`() {
        assertEquals(
            "content://photos/clip-only",
            ShareToValuePilotIntentInput.chooseSingleUri(
                extraUri = null,
                clipItemCount = 1,
                clipUri = "content://photos/clip-only"
            )
        )
    }

    @Test
    fun `multi item clipdata and empty intent fail closed`() {
        assertNull(
            ShareToValuePilotIntentInput.chooseSingleUri(
                extraUri = null,
                clipItemCount = 2,
                clipUri = "content://photos/one"
            )
        )
        assertNull(
            ShareToValuePilotIntentInput.chooseSingleUri(
                extraUri = null,
                clipItemCount = 1,
                clipUri = null
            )
        )
    }
}
