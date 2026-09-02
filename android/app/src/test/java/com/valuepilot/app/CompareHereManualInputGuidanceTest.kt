package com.valuepilot.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CompareHereManualInputGuidanceTest {

    @Test
    fun `visible example matches the verified current and member parser labels`() {
        val resources =
            File(
                System.getProperty("user.dir"),
                "src/main/res/values/strings.xml"
            ).readText()

        assertTrue(resources.contains("current price in the same box"))
        assertTrue(resources.contains("clearly labeled member price"))
        assertTrue(resources.contains("Current price CA$5.99"))
        assertTrue(resources.contains("Member price CA$4.99"))
        assertTrue(resources.contains("CA$ or US$ instead of a bare $"))
    }
}
