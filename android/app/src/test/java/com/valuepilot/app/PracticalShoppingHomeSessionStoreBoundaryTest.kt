package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PracticalShoppingHomeSessionStoreBoundaryTest {

    @Test
    fun localStoreUsesBoundedTypedEnvelopeWithoutShoppingAuthority() {
        val source = source().readText()

        listOf(
            "interface PracticalShoppingHomeSessionStore",
            "PracticalShoppingHomeSessionStoreCodec.decode(",
            "ShoppingRequestDetailsCodec.maximumEncodedBytes",
            "Base64.decode(encoded, Base64.DEFAULT)",
            "Base64.encodeToString(bytes, Base64.NO_WRAP)",
            "editor.remove(KEY_REQUEST_DETAILS)"
        ).forEach { required ->
            assertTrue("Expected local Home session boundary $required", source.contains(required))
        }

        listOf(
            "PracticalShoppingPlanner",
            "PracticalShoppingPolicy(",
            "Money.parse",
            "ProductionCurrentPrice",
            "DeterministicRankingEngine",
            "URL(",
            "HttpURLConnection"
        ).forEach { forbidden ->
            assertFalse("Home session persistence must not own $forbidden", source.contains(forbidden))
        }
    }

    private fun source(): File {
        val workingDirectory =
            requireNotNull(System.getProperty("user.dir")) {
                "Missing user.dir for source boundary test"
            }
        return File(
            workingDirectory,
            "src/main/java/com/valuepilot/app/PracticalShoppingHomeSessionStore.kt"
        ).also {
            assertTrue("Missing source at ${it.absolutePath}", it.isFile)
        }
    }
}
