package com.valuepilot.app

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentinaSecureNetworkingBoundaryTest {

    @Test
    fun manifestDeclaresOnlyTheIntentionalNetworkPermission() {
        val manifest = moduleFile("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.INTERNET\" />"))
        assertTrue(manifest.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(manifest.contains("tools:node=\"remove\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("android:name=\".ValuePilotApplication\""))
        assertFalse(manifest.contains("android:name=\"android.permission.INTERNET\"\n        tools:node=\"remove\""))
    }

    @Test
    fun firebaseConfigMatchesTheAuthoritativeRegisteredAppWithoutServerCredentials() {
        val config = JSONObject(moduleFile("google-services.json").readText())
        val client = config.getJSONArray("client").getJSONObject(0)
        val info = client.getJSONObject("client_info")
        val android = info.getJSONObject("android_client_info")

        assertEquals("valuepilot-argentina", config.getJSONObject("project_info").getString("project_id"))
        assertEquals("17260882200", config.getJSONObject("project_info").getString("project_number"))
        assertEquals("1:17260882200:android:446eec76e5f1a175f2566d", info.getString("mobilesdk_app_id"))
        assertEquals("com.valuepilot.app", android.getString("package_name"))
        assertTrue(client.getJSONArray("api_key").getJSONObject(0).getString("current_key").isNotBlank())
        val raw = config.toString()
        assertFalse(raw.contains("private_key"))
        assertFalse(raw.contains("service_account"))
        assertFalse(raw.contains("client_email"))
    }

    @Test
    fun providerSelectionIsIsolatedByBuildVariant() {
        val release = source("src/release/java/com/valuepilot/app/ValuePilotAppCheckProvider.kt").readText()
        val debug = source("src/debug/java/com/valuepilot/app/ValuePilotAppCheckProvider.kt").readText()
        val main = source("src/main/java/com/valuepilot/app/ValuePilotApplication.kt").readText()

        assertTrue(release.contains("PlayIntegrityAppCheckProviderFactory"))
        assertFalse(release.contains("DebugAppCheckProviderFactory"))
        assertTrue(debug.contains("DebugAppCheckProviderFactory"))
        assertFalse(debug.contains("PlayIntegrityAppCheckProviderFactory"))
        assertFalse(main.contains("DebugAppCheckProviderFactory"))
        assertFalse(main.contains("PlayIntegrityAppCheckProviderFactory"))
    }

    @Test
    fun gradleWiringUsesTheFirebaseBomAndKeepsDebugProviderOutOfReleaseDependencies() {
        val appGradle = moduleFile("build.gradle.kts").readText()
        val rootGradle = moduleFile("../build.gradle.kts").readText()

        assertTrue(rootGradle.contains("id(\"com.google.gms.google-services\") version \"4.5.0\" apply false"))
        assertTrue(appGradle.contains("id(\"com.google.gms.google-services\")"))
        assertTrue(appGradle.contains("firebase-bom:34.19.0"))
        assertTrue(appGradle.contains("releaseImplementation(\"com.google.firebase:firebase-appcheck-playintegrity\")"))
        assertTrue(appGradle.contains("debugImplementation(\"com.google.firebase:firebase-appcheck-debug\")"))
    }

    @Test
    fun transportSourceHasNoPrivateOriginCredentialsOrTokenLogging() {
        val source = source("src/main/java/com/valuepilot/app/ArgentinaConsumerEdgeTransport.kt").readText()
        val application = source("src/main/java/com/valuepilot/app/ValuePilotApplication.kt").readText()

        assertTrue(source.contains("X-Firebase-AppCheck"))
        assertTrue(source.contains("getAppCheckToken(false)"))
        assertFalse(source.contains("getAppCheckToken(true)"))
        assertTrue(source.contains(DEFAULT_EDGE_HOST))
        assertTrue(source.contains("instanceFollowRedirects = false"))
        assertTrue(source.contains("maxResponseBytes"))
        assertTrue(source.contains("ArgentinaRequestCancellation"))
        assertFalse(source.contains("Authorization"))
        assertFalse(source.contains("southamerica-east1"))
        assertFalse(source.contains("println"))
        assertFalse(source.contains("Log."))
        assertFalse(source.contains("SharedPreferences"))
        assertTrue(application.contains("FirebaseApp.initializeApp"))
        assertTrue(application.contains("installValuePilotAppCheckProvider"))
        assertTrue(application.contains("CONFIGURATION_UNAVAILABLE"))
    }

    private fun source(relativePath: String): File = moduleFile(relativePath)

    private fun moduleFile(relativePath: String): File =
        File(System.getProperty("user.dir"), relativePath).also {
            assertTrue("Missing module file ${it.absolutePath}", it.isFile)
        }

    private companion object {
        const val DEFAULT_EDGE_HOST = "valuepilot-argentina-consumer-edge-m72bv57hrq-rj.a.run.app"
    }
}
