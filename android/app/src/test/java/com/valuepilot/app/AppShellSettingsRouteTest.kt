package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellSettingsRouteTest {

    @Test
    fun `Settings is a Saved-owned route and nested pages have one-step Back`() {
        val saved = AppShellReducer.reduce(
            AppShellState.initial(),
            AppShellIntent.SelectPrimary(AppPrimaryTab.SAVED)
        )
        val settings = AppShellReducer.reduce(saved, AppShellIntent.OpenSettings)
        val sources = AppShellReducer.reduce(settings, AppShellIntent.OpenSettingsDataSources)
        val about = AppShellReducer.reduce(settings, AppShellIntent.OpenSettingsAbout)
        val technical = AppShellReducer.reduce(about, AppShellIntent.OpenSettingsTechnicalDetails)

        assertEquals(AppRoute.SETTINGS, settings.route)
        assertEquals(AppRoute.SETTINGS_DATA_SOURCES, sources.route)
        assertEquals(AppRoute.SETTINGS_ABOUT, about.route)
        assertEquals(AppRoute.SETTINGS_TECHNICAL_DETAILS, technical.route)
        assertTrue(technical.canNavigateBack)
        assertEquals(AppRoute.SETTINGS_ABOUT, AppShellReducer.reduce(technical, AppShellIntent.NavigateBack).route)
        assertEquals(AppRoute.SETTINGS, AppShellReducer.reduce(sources, AppShellIntent.NavigateBack).route)
        assertEquals(AppRoute.SAVED, AppShellReducer.reduce(settings, AppShellIntent.NavigateBack).route)
    }

    @Test
    fun `Settings pages cannot be opened from shopping routes`() {
        val home = AppShellState.initial()
        assertSame(home, AppShellReducer.reduce(home, AppShellIntent.OpenSettings))
        val search = AppShellReducer.reduce(home, AppShellIntent.SelectPrimary(AppPrimaryTab.SEARCH))
        assertSame(search, AppShellReducer.reduce(search, AppShellIntent.OpenSettings))
        assertFalse(AppShellReducer.reduce(search, AppShellIntent.OpenSettingsDataSources).canNavigateBack)
    }

    @Test
    fun `Settings route restoration follows its explicit parent chain`() {
        val restored = AppShellStateRestoration.restore(
            AppPrimaryTab.SAVED.name,
            AppRoute.SETTINGS_LICENCES.name
        )
        assertEquals(AppRoute.SETTINGS_LICENCES, restored.route)
        assertEquals(AppPrimaryTab.SAVED, restored.selectedPrimaryTab)
        assertEquals(AppRoute.SETTINGS_LICENCES, AppShellStateRestoration.restorableRoute(restored))
    }
}
