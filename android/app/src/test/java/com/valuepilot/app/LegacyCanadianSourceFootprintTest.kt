package com.valuepilot.app

import com.valuepilot.core.Money
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the cleanup boundary without rejecting historical reports or generic CAD support. */
class LegacyCanadianSourceFootprintTest {

    @Test
    fun `obsolete Canadian runtime assets and source rails are absent`() {
        val appRoot = findAppRoot()
        assertFalse(File(appRoot, "src/main/assets/offline_catalog").exists())
        assertFalse(File(appRoot, "src/main/assets/store_directory").exists())

        val forbiddenMarkers = listOf(
            "offline_catalog",
            "BundledOfflineCatalog",
            "StoreDirectorySnapshotAssetLoader",
            "TripStoreDirectoryAdapter",
            "Jamieson",
            "Rakuten",
            "OpenPricesImportedEvidence",
            "ca-gta",
            "ca-metro-vancouver"
        )
        Files.walk(File(appRoot, "src/main").toPath()).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { path ->
                val text = String(Files.readAllBytes(path), Charsets.UTF_8)
                forbiddenMarkers.forEach { marker ->
                    assertFalse("Active Android source still contains $marker: $path", text.contains(marker))
                }
            }
        }
    }

    @Test
    fun `generic exact money still accepts CAD`() {
        val cad = Money.parse("1.00", "CAD")
        assertEquals("CAD", cad.currencyCode)
        assertEquals(100L, cad.minorUnits)
        assertTrue(cad.fractionDigits == 2)
    }

    @Test
    fun `public product copy does not advertise a Canadian source rail`() {
        val repository = findRepositoryRoot()
        val publicHome = File(repository, "docs/index.html").readText()
        val publicMethodology = File(repository, "docs/methodology.html").readText()
        assertTrue(publicHome.contains("no live Canadian catalog or retailer-offer feed is shipped"))
        assertTrue(publicMethodology.contains("The shipped app has no Canadian catalog or retailer-offer feed"))
        assertFalse(File(repository, ".gitattributes").readText().contains("offline_catalog"))
        assertFalse(File(repository, ".gitattributes").readText().contains("store_directory"))
    }

    private fun findAppRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app")
            if (File(candidate, "src/main").isDirectory) return candidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate android/app from ${System.getProperty("user.dir")}")
    }

    private fun findRepositoryRoot(): File = findAppRoot().parentFile?.parentFile
        ?: error("Could not locate repository root")
}
