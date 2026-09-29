package com.valuepilot.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class M128PrivateListIntegrityBoundaryTest {

    @Test
    fun lifecycleStateDistinguishesIntentionalEmptyListFromPartialBundle() {
        val source = source("src/main/java/com/valuepilot/app/MainActivity.kt")
            .readText()
            .replace("\r\n", "\n")

        assertTrue(source.contains("STATE_HOME_PRIVATE_STATE_PRESENT"))
        assertTrue(source.contains("outState.putBoolean(STATE_HOME_PRIVATE_STATE_PRESENT, true)"))
        assertTrue(source.contains("homeSessionStore.loadResult()"))
        assertTrue(source.contains("recoverPrivateIdentityState("))
        assertTrue(source.contains("if (!homeSessionPersistenceBlocked)"))
        assertTrue(source.contains("authorizePrivateListPersistence()"))
    }

    @Test
    fun durableDecodeReportsMalformedPrivateFieldsInsteadOfSilentlyAcceptingEmpty() {
        val source = source("src/main/java/com/valuepilot/app/PracticalShoppingHomeSessionStore.kt")
            .readText()
            .replace("\r\n", "\n")

        assertTrue(source.contains("INVALID_DURABLE_PRIVATE_STATE"))
        assertTrue(source.contains("decodePrivateIdentityState("))
        assertTrue(source.contains("if (!preferences.contains(KEY_QUERY))"))
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
