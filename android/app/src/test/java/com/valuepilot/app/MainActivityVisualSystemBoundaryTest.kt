package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the consumer shell on one visual system without allowing presentation changes to leak
 * into shopping or evidence authority. This is intentionally a source/resource boundary test;
 * the actual layout remains exercised by the Android resource/build checks.
 */
class MainActivityVisualSystemBoundaryTest {

    @Test
    fun `primary shell uses the warm palette and shared action styles`() {
        val layout = source("src/main/res/layout/activity_shell.xml").readText()
        val theme = source("src/main/res/values/themes.xml").readText()
        val colors = source("src/main/res/values/colors.xml").readText()

        listOf(
            "@color/vp_background",
            "@color/vp_surface",
            "@color/vp_text_primary",
            "@style/Widget.ValuePilot.Button.Outlined",
            "@style/Widget.ValuePilot.Chip",
            "app:itemIconTint=\"@color/vp_bottom_nav_tint\"",
            "app:itemTextColor=\"@color/vp_bottom_nav_tint\""
        ).forEach { required ->
            assertTrue("Expected shared consumer styling: $required", layout.contains(required))
        }

        listOf(
            "<item name=\"colorPrimary\">@color/vp_primary</item>",
            "<item name=\"materialButtonStyle\">@style/Widget.ValuePilot.Button</item>",
            "<item name=\"materialButtonOutlinedStyle\">@style/Widget.ValuePilot.Button.Outlined</item>",
            "<item name=\"chipStyle\">@style/Widget.ValuePilot.Chip</item>"
        ).forEach { required ->
            assertTrue("Expected theme binding: $required", theme.contains(required))
        }

        listOf(
            "vp_background",
            "vp_surface",
            "vp_primary",
            "vp_primary_dark",
            "vp_text_primary",
            "vp_text_secondary",
            "vp_text_muted",
            "vp_outline"
        ).forEach { required ->
            assertTrue("Expected palette color: $required", colors.contains("name=\"$required\""))
        }
    }

    @Test
    fun `standalone consumer routes share the same background and readable text colors`() {
        listOf(
            "activity_main.xml",
            "activity_good_price.xml",
            "activity_barcode_capture.xml",
            "activity_share_to_valuepilot.xml"
        ).forEach { name ->
            val layout = source("src/main/res/layout/$name").readText()
            assertTrue("$name must use the shared background", layout.contains("@color/vp_background"))
            assertFalse("$name must not retain the old cold white root", layout.contains("android:background=\"#FFFFFF\""))
        }
    }

    @Test
    fun `main activity resizes for the keyboard instead of covering the primary action`() {
        val manifest = source("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""))
    }

    private fun source(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/$relativePath")
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate $relativePath")
    }
}
