package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class M127PrivateShoppingListBoundaryTest {

    @Test
    fun searchHandoffCarriesTheTypedMatchAndHomeRendersASeparatePrivateList() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val renderer = source("src/main/java/com/valuepilot/app/PracticalShoppingHomeRenderer.kt").readText()
        val surface = source("src/main/java/com/valuepilot/app/PracticalShoppingHomeSurfaceView.kt").readText()

        assertTrue(activity.contains("onAddToHome: (PracticalShoppingSearchIdentityPresentation.Match) -> Unit"))
        assertTrue(activity.contains("PracticalShoppingPrivateIdentity.fromSearchMatch(match)"))
        assertTrue(activity.contains("PracticalShoppingHomeSession.addPrivateIdentity"))
        assertTrue(activity.contains("privateIdentities = homeSessionState.privateIdentities"))
        assertTrue(activity.contains("onRemovePrivateIdentity"))
        assertTrue(renderer.contains("privateIdentityItems"))
        assertTrue(renderer.contains("PRIVATE_IDENTITY_NOTICE"))
        assertTrue(surface.contains("renderPrivateIdentities(state.privateIdentityItems)"))
        assertTrue(surface.contains("onRemovePrivateIdentity?.invoke(identity.identityKey)"))
        assertFalse(surface.contains("PracticalShoppingPlanner"))
        assertFalse(surface.contains("Money.parse"))
    }

    @Test
    fun privateListPersistenceIsSeparateFromSamplePlannerStorage() {
        val session = source("src/main/java/com/valuepilot/app/PracticalShoppingHomeSession.kt").readText()
            .replace("\r\n", "\n")
        val store = source("src/main/java/com/valuepilot/app/PracticalShoppingHomeSessionStore.kt").readText()
            .replace("\r\n", "\n")

        assertTrue(session.contains("val privateIdentities: List<PracticalShoppingPrivateIdentity>"))
        assertTrue(session.contains("if (state.privateIdentities.any { it.identityKey == identity.identityKey }) return state"))
        assertTrue(session.contains("privateIdentities = normalizedPrivateIdentities"))
        assertTrue(store.contains("KEY_PRIVATE_IDENTITIES"))
        assertTrue(store.contains("encodePrivateIdentities("))
        assertTrue(store.contains("EMPTY_PRIVATE_IDENTITIES_ENCODING"))
        assertTrue(store.contains("Preserve an unreadable record"))
    }

    @Test
    fun partialLifecycleBundleCannotTurnDurablePrivateListIntoEmptyState() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        assertTrue(activity.contains("savedInstanceState?.containsKey(STATE_HOME_PRIVATE_IDENTITIES) == true"))
        assertTrue(activity.contains("homeSessionStore.hasPrivateIdentityRecord()"))
        assertTrue(activity.contains("resolvePrivateIdentities("))
        assertTrue(activity.contains("record is authoritative"))
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
