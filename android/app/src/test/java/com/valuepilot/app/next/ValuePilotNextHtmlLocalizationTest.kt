package com.valuepilot.app.next

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValuePilotNextHtmlLocalizationTest {

    @Test
    fun `native bridge labels do not expose raw status enums or errors`() {
        val source = assetSource()

        assertTrue(source.contains("nativeMatchExactBarcode"))
        assertTrue(source.contains("nativeFreshnessLabel(offer.freshness)"))
        assertTrue(source.contains("nativeTripErrorDetail"))
        assertFalse(source.contains("safe(offer.freshness||'')"))
        assertFalse(source.contains("safe(payload.error||'')"))
    }

    private fun assetSource(): String {
        var directory = File(requireNotNull(System.getProperty("user.dir")))
        repeat(8) {
            val candidate =
                File(directory, "app/src/debug/assets/valuepilot_next_html/app.js")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate valuepilot_next_html/app.js")
    }
}
