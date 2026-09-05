package com.valuepilot.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingHomeDisclosureTest {

    @Test
    fun shopAgainActionCannotSoundLikeLiveRetailerPlanning() {
        val resources =
            File(
                requireNotNull(System.getProperty("user.dir")),
                "src/main/res/values/strings.xml"
            ).readText()

        assertTrue(
            resources.contains(
                "<string name=\"home_shop_again_description\">Plan this same list again " +
                    "using the offline fictional demo data; this is not a live retailer " +
                    "plan.</string>"
            )
        )
        assertFalse(
            "The repeat action must not describe the fictional result as current evidence",
            resources.contains(
                """<string name="home_shop_again_description">Plan this same shopping list again using the current available evidence"""
            )
        )
    }
}
