package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticalShoppingSettingsPresentationTest {

    @Test
    fun `landing stays short and exposes only low frequency destinations`() {
        val state = PracticalShoppingSettingsPresentation.page(PracticalShoppingSettingsPage.LANDING)

        assertEquals(4, state.rows.size)
        assertEquals(
            listOf("preferences", "data-privacy", "data-sources", "about"),
            state.rows.map { it.key }
        )
        assertTrue(state.rows.all { it.summary.length < 100 })
        assertTrue(state.rows.all { it.action != null })
    }

    @Test
    fun `data sources keeps public evidence personal records and web photos separate`() {
        val text =
            PracticalShoppingSettingsPresentation
                .page(PracticalShoppingSettingsPage.DATA_SOURCES)
                .rows
                .joinToString(" ") { "${it.title} ${it.summary}" }

        assertTrue(text.contains("Argentina public price and store dataset"))
        assertTrue(text.contains("private records"))
        assertTrue(text.contains("Google Images"))
        assertTrue(text.contains("not copied"))
        assertTrue(text.contains("not a live stock or price promise"))
    }

    @Test
    fun `about exposes version and licences below the landing surface`() {
        val about = PracticalShoppingSettingsPresentation.page(
            page = PracticalShoppingSettingsPage.ABOUT,
            versionName = "101.1.23"
        )

        assertTrue(about.rows.any { it.title == "Version" && it.summary == "101.1.23" })
        assertTrue(about.rows.any { it.action == PracticalShoppingSettingsAction.OpenLicences })
    }

    @Test
    fun `technical details keep stale state out of the normal landing page`() {
        val landing = PracticalShoppingSettingsPresentation.page(PracticalShoppingSettingsPage.LANDING)
        val technical = PracticalShoppingSettingsPresentation.page(
            page = PracticalShoppingSettingsPage.TECHNICAL_DETAILS,
            dataStatus = PracticalShoppingDataStatusPresentation.from(
                CompareHerePrivatePriceMemoryState.empty()
            )
        )

        assertFalse(landing.rows.joinToString { it.summary }.contains("STALE_RELEASE"))
        assertTrue(technical.detail?.contains("Product data needs an update") == true)
        assertTrue(technical.detail?.contains("Unknown prices stay unknown") == true)
    }
}
