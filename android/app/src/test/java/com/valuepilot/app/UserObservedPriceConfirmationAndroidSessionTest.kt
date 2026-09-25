package com.valuepilot.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserObservedPriceConfirmationAndroidSessionTest {

    @Test
    fun `process runtime shares one proof store and one serial worker across sessions`() {
        val source = source("UserObservedPriceConfirmationAndroidSession.kt").readText()

        listOf(
            "@Volatile",
            "private var instance: UserObservedPriceConfirmationProcessRuntime? = null",
            "context.applicationContext ?: context",
            "Executors.newSingleThreadExecutor",
            "Thread(runnable, \"valuepilot-observed-price-proof\")",
            "proofStore = UserProvidedPriceProofArtifactLocalStore(appContext)",
            ") : UserObservedPriceConfirmationWorkScheduler"
        ).forEach { required ->
            assertTrue("Expected process runtime boundary $required", source.contains(required))
        }

        assertEquals(
            1,
            Regex("UserProvidedPriceProofArtifactLocalStore\\(appContext\\)")
                .findAll(source)
                .count()
        )
        assertFalse(source.contains("executor.shutdown"))
        assertFalse(source.contains("executor.shutdownNow"))
        assertFalse(source.contains("android.app.Activity"))
    }

    @Test
    fun `factory composes existing proof transaction and verified execution host without bypass`() {
        val source = source("UserObservedPriceConfirmationAndroidSession.kt").readText()

        val runtime = source.indexOf("UserObservedPriceConfirmationProcessRuntime.get(appContext)")
        val transaction =
            source.indexOf("UserObservedPriceConfirmationTransaction(runtime.proofStore)")
        val gateway = source.indexOf("UserObservedPriceConfirmationLocalGateway(transaction)")
        val host = source.indexOf("UserObservedPriceConfirmationExecutionHost(")

        assertTrue(runtime >= 0)
        assertTrue(transaction > runtime)
        assertTrue(gateway > transaction)
        assertTrue(host > gateway)

        listOf(
            "gateway = gateway",
            "worker = runtime",
            "UserObservedPriceConfirmationMainLooperDispatcher(",
            "Handler(Looper.getMainLooper())",
            "completionListener = completionListener"
        ).forEach { required ->
            assertTrue("Expected Android composition $required", source.contains(required))
        }

        assertFalse(source.contains("PracticalShoppingSavedProcessRuntime"))
        assertFalse(source.contains("PracticalShoppingSavedMainLooperDispatcher"))
    }

    @Test
    fun `session owns main thread lifecycle only and forwards caller submission unchanged`() {
        val source = source("UserObservedPriceConfirmationAndroidSession.kt").readText()
        val submitStart = source.indexOf("fun submit(")
        val busyStart = source.indexOf("fun isBusy()")
        val submitBody = source.substring(submitStart, busyStart)

        assertTrue(submitStart >= 0)
        assertTrue(submitBody.indexOf("requireMainThread()") < submitBody.indexOf("return host.submit("))
        listOf(
            "artifactId = artifactId",
            "proofType = proofType",
            "artifactBytes = artifactBytes",
            "fields = fields"
        ).forEach { required ->
            assertTrue("Expected exact submit forwarding $required", submitBody.contains(required))
        }

        assertFalse(submitBody.contains("copyOf()"))
        assertFalse(submitBody.contains("fill(0)"))
        assertTrue(source.contains("fun isBusy(): Boolean = host.isBusy()"))
        assertTrue(source.contains("fun isClosed(): Boolean = host.isClosed()"))
        assertTrue(source.contains("override fun close()"))
        assertTrue(source.contains("host.close()"))
        assertTrue(source.contains("Looper.myLooper() == Looper.getMainLooper()"))
    }

    @Test
    fun `Android session never acquires capture clock identity evidence ranking or UI authority`() {
        val source = source("UserObservedPriceConfirmationAndroidSession.kt").readText()

        listOf(
            "System.currentTimeMillis",
            "UUID",
            "ShoppingEvidence(",
            "EvidenceClaim(",
            "EvidenceClaimDomain.CURRENT_PRICE",
            "EvidenceBackedUnitValuePolicy",
            "ProductPackageQuantity",
            "ProductionCurrentPrice",
            "ProviderProductionAuthorization",
            "OcrScanner",
            "android.hardware",
            "Camera",
            "WorkManager",
            "NotificationManager",
            "android.view.",
            "android.widget.",
            "MainActivity",
            "UserObservedPriceUnitValueSurface",
            "java.net",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE"
        ).forEach { forbidden ->
            assertFalse("Android confirmation session must not own $forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun `process runtime is the only production composition of context backed proof store`() {
        val mainDirectory = mainSourceDirectory()
        val constructions =
            mainDirectory
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().asSequence().mapIndexedNotNull { index, line ->
                        if (
                            line.contains("UserProvidedPriceProofArtifactLocalStore(") &&
                            !line.contains("class UserProvidedPriceProofArtifactLocalStore") &&
                            !line.contains("constructor(context: Context)")
                        ) {
                            "${file.name}:${index + 1}:${line.trim()}"
                        } else {
                            null
                        }
                    }
                }
                .toList()

        assertEquals(
            listOf(
                "UserObservedPriceConfirmationAndroidSession.kt:" +
                    constructions.single().substringAfter(':')
            ),
            constructions
        )
        assertTrue(constructions.single().contains("UserProvidedPriceProofArtifactLocalStore(appContext)"))
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
