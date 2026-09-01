package com.valuepilot.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class UserObservedPriceProofContentSourceTest {

    @Test
    fun `bounded stream reader returns exact bytes and closes caller source`() {
        val expected = byteArrayOf(4, 8, 15, 16, 23, 42)
        val input = TrackingInputStream(ByteArrayInputStream(expected))

        val result =
            UserObservedPriceProofStreamReader.read(
                input = input,
                maxBytes = 32
            )

        assertTrue(result.accepted)
        assertNull(result.issue)
        assertArrayEquals(expected, result.bytes)
        assertTrue(input.closed)
    }

    @Test
    fun `bounded stream reader accepts exact limit and rejects first byte above it`() {
        val exact = byteArrayOf(1, 2, 3, 4)
        val exactInput = TrackingInputStream(ByteArrayInputStream(exact))
        val tooLargeInput = TrackingInputStream(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)))

        val accepted = UserObservedPriceProofStreamReader.read(exactInput, maxBytes = 4)
        val rejected = UserObservedPriceProofStreamReader.read(tooLargeInput, maxBytes = 4)

        assertTrue(accepted.accepted)
        assertArrayEquals(exact, accepted.bytes)
        assertEquals(UserObservedPriceProofContentReadIssue.INPUT_TOO_LARGE, rejected.issue)
        assertNull(rejected.bytes)
        assertTrue(exactInput.closed)
        assertTrue(tooLargeInput.closed)
    }

    @Test
    fun `bounded stream reader rejects empty proof content`() {
        val input = TrackingInputStream(ByteArrayInputStream(byteArrayOf()))

        val result = UserObservedPriceProofStreamReader.read(input, maxBytes = 8)

        assertFalse(result.accepted)
        assertEquals(UserObservedPriceProofContentReadIssue.EMPTY_CONTENT, result.issue)
        assertNull(result.bytes)
        assertTrue(input.closed)
    }

    @Test
    fun `bounded stream reader fails closed on io error and closes source`() {
        val input = ThrowingInputStream()

        val result = UserObservedPriceProofStreamReader.read(input, maxBytes = 8)

        assertFalse(result.accepted)
        assertEquals(UserObservedPriceProofContentReadIssue.READ_FAILED, result.issue)
        assertNull(result.bytes)
        assertTrue(input.closed)
    }

    @Test
    fun `zero length bulk read still makes bounded progress without dropping bytes`() {
        val expected = byteArrayOf(7, 11, 13)
        val input = ZeroThenBytesInputStream(expected)

        val result = UserObservedPriceProofStreamReader.read(input, maxBytes = 3)

        assertTrue(result.accepted)
        assertArrayEquals(expected, result.bytes)
        assertTrue(input.closed)
    }

    @Test
    fun `content source is transient read only Android composition with no semantic authority`() {
        val source = source("UserObservedPriceProofContentSource.kt").readText()

        listOf(
            "private val contentResolver: ContentResolver",
            "uri.scheme != ContentResolver.SCHEME_CONTENT",
            "contentResolver.openInputStream(uri)",
            "UserObservedPriceProofStreamReader.read(",
            "maxBytes = UserProvidedPriceProofArtifact.MAX_ARTIFACT_BYTES",
            "input.use { stream ->",
            "read > maxBytes - total",
            "UserObservedPriceProofContentReadIssue.INPUT_TOO_LARGE",
            "UserObservedPriceProofContentReadIssue.EMPTY_CONTENT",
            "UserObservedPriceProofContentReadIssue.READ_FAILED"
        ).forEach { required ->
            assertTrue("Expected proof content boundary $required", source.contains(required))
        }

        assertEquals(1, Regex("contentResolver\\.openInputStream\\(uri\\)").findAll(source).count())
        assertEquals(
            UserProvidedPriceProofArtifact.MAX_ARTIFACT_BYTES,
            16 * 1024 * 1024
        )

        listOf(
            "registerForActivityResult",
            "ActivityResultContracts",
            "takePersistableUriPermission",
            "contentResolver.query",
            "contentResolver.openOutputStream",
            "readBytes()",
            "copyTo(",
            "android.content.Intent",
            "android.content.Context",
            "android.app.Activity",
            "java.io.FileOutputStream",
            "System.currentTimeMillis",
            "UUID",
            "UserProvidedPriceProofArtifact.fingerprint",
            "UserProvidedPriceProofArtifactLocalStore",
            "UserObservedPriceConfirmationTransaction",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceClaimDomain.CURRENT_PRICE",
            "EvidenceBackedUnitValuePolicy",
            "ProductPackageQuantity",
            "ProductionBestValue",
            "ProviderProductionAuthorization",
            "OcrScanner",
            "Bitmap",
            "Camera",
            "MainActivity",
            "java.net",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        ).forEach { forbidden ->
            assertFalse("Proof content source must not own $forbidden", source.contains(forbidden))
        }
    }

    private class TrackingInputStream(
        private val delegate: InputStream
    ) : InputStream() {
        var closed: Boolean = false
            private set

        override fun read(): Int = delegate.read()

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int
        ): Int = delegate.read(buffer, offset, length)

        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private class ThrowingInputStream : InputStream() {
        var closed: Boolean = false
            private set

        override fun read(): Int = throw IOException("synthetic read failure")

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int
        ): Int = throw IOException("synthetic read failure")

        override fun close() {
            closed = true
        }
    }

    private class ZeroThenBytesInputStream(
        bytes: ByteArray
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        private var returnedZero = false

        var closed: Boolean = false
            private set

        override fun read(): Int = delegate.read()

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int
        ): Int {
            if (!returnedZero) {
                returnedZero = true
                return 0
            }
            return delegate.read(buffer, offset, length)
        }

        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private fun source(fileName: String): File =
        File(mainSourceDirectory(), fileName).also {
            assertTrue("Missing source $fileName at ${it.absolutePath}", it.isFile)
        }

    private fun mainSourceDirectory(): File {
        var directory = File(System.getProperty("user.dir") ?: error("user.dir unavailable"))
        repeat(8) {
            val candidate = File(directory, "app/src/main/java/com/valuepilot/app")
            if (candidate.isDirectory) return candidate
            val directCandidate = File(directory, "src/main/java/com/valuepilot/app")
            if (directCandidate.isDirectory) return directCandidate
            directory = directory.parentFile ?: return@repeat
        }
        error("Could not locate app main source directory")
    }
}
