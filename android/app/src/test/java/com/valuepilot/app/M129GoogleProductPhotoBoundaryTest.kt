package com.valuepilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class M129GoogleProductPhotoBoundaryTest {

    @Test
    fun viewerUsesOnlyAnExplicitCustomTabHandoff() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        val viewer = source("src/main/java/com/valuepilot/app/GoogleProductPhotoViewer.kt").readText()

        listOf(
            "CustomTabsIntent.Builder()",
            "setInitialActivityHeightPx",
            "ACTIVITY_HEIGHT_ADJUSTABLE",
            "googlePhotoViewerLaunchInFlight",
            "search_identity_browser_unavailable"
        ).forEach { required -> assertTrue("Missing photo viewer boundary $required", activity.contains(required)) }
        listOf(
            "https://$",
            "tbm=isch",
            "URLEncoder.encode",
            "MAX_QUERY_LENGTH"
        ).forEach { required -> assertTrue("Missing bounded URL rule $required", viewer.contains(required)) }
        assertFalse(activity.contains("mayLaunchUrl"))
        assertFalse(activity.contains("WebView"))
        assertFalse(activity.contains("DownloadManager"))
        assertFalse(activity.contains("MediaStore"))
        assertFalse(viewer.contains(".identityKey"))
        assertFalse(viewer.contains("sourceLabel"))
    }

    @Test
    fun partialViewerStartsNearSixtyFivePercentAndRemainsAdjustable() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt")
            .readText()
            .replace("\r\n", "\n")

        assertTrue(activity.contains("googlePhotoInitialHeightPx()"))
        assertTrue(activity.contains("resources.displayMetrics.heightPixels"))
        assertTrue(activity.contains("GOOGLE_PHOTO_INITIAL_HEIGHT_PERCENT = 65"))
        assertTrue(activity.contains("CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE"))
        assertTrue(activity.contains("GOOGLE_PHOTO_MIN_INITIAL_HEIGHT_DP = 320"))
    }

    @Test
    fun unsupportedPartialViewerRequiresAnExplicitFullScreenChoice() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt")
            .readText()
            .replace("\r\n", "\n")
        val fallbackStart = activity.indexOf("private fun showGooglePhotoBrowserFallback(")
        val fallbackEnd = activity.indexOf("private fun showGooglePhotoLaunchError(", fallbackStart)
        assertTrue(fallbackStart >= 0)
        assertTrue(fallbackEnd > fallbackStart)
        val fallback = activity.substring(fallbackStart, fallbackEnd)

        assertTrue(fallback.contains("search_identity_browser_fallback_title"))
        assertTrue(fallback.contains("search_identity_browser_fallback_message"))
        assertTrue(fallback.contains("search_identity_browser_fallback_cancel"))
        assertTrue(fallback.contains("search_identity_browser_fallback_open"))
        assertTrue(fallback.contains("launchGooglePhotoFullScreen(uri)"))
        assertTrue(activity.contains("showGooglePhotoBrowserFallback(uri)"))
        assertTrue(activity.contains("private fun launchGooglePhotoFullScreen(uri:"))
    }

    @Test
    fun allThreeIdentitySurfacesDiscloseUnverifiedPhotos() {
        val main = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        val home = source("src/main/java/com/valuepilot/app/PracticalShoppingHomeSurfaceView.kt").readText()
        val basket = source("src/main/java/com/valuepilot/app/PracticalShoppingBasketSurfaceView.kt").readText()
        val strings = source("src/main/res/values/strings.xml").readText()

        assertTrue(main.contains("search_identity_view_photos_description"))
        assertTrue(home.contains("onOpenPrivateIdentityPhotos"))
        assertTrue(home.contains("home_private_identity_photos_description"))
        assertTrue(basket.contains("onOpenPrivateIdentityPhotos"))
        assertTrue(basket.contains("search_identity_view_photos"))
        assertTrue(strings.contains("Google Images results are unverified"))
        assertTrue(strings.contains("does not verify or download"))
    }

    @Test
    fun launchFailureReportsOnTheCurrentSurfaceAndKeepsBrowserFallback() {
        val main = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        val home = source("src/main/java/com/valuepilot/app/PracticalShoppingHomeSurfaceView.kt").readText()
        val basket = source("src/main/java/com/valuepilot/app/PracticalShoppingBasketSurfaceView.kt").readText()

        listOf(
            "showGooglePhotoLaunchError()",
            "homeExperience.showPhotoError(message)",
            "basketExperience.showPhotoError(message)",
            "searchStatus.text = message",
            "Intent(Intent.ACTION_VIEW, uri)",
            "catch (_: SecurityException)"
        ).forEach { required ->
            assertTrue("Missing current-surface photo failure boundary $required", main.contains(required))
        }
        assertTrue(home.contains("fun showPhotoError(error: String)"))
        assertTrue(basket.contains("fun showPhotoError(message: String)"))
    }

    @Test
    fun searchCardPhotoAndSaveActionsUseSeparateVerticalTouchTargets() {
        val activity = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
            .replace("\r\n", "\n")
        val cardStart = activity.indexOf("private fun createSearchResultView(")
        val cardEnd = activity.indexOf("private fun openGoogleProductPhotos(", cardStart)
        assertTrue(cardStart >= 0)
        assertTrue(cardEnd > cardStart)
        val card = activity.substring(cardStart, cardEnd)

        assertTrue("Search card must wrap its body and actions", card.contains("val content = LinearLayout(this)"))
        assertTrue("Search card must use the shared progressive action menu", card.contains("ValuePilotProductContextMenu(this)"))
        assertTrue("Photo action must be represented by a typed menu action", card.contains("ValuePilotProductContextActionKind.PHOTO"))
        assertTrue("Photo callback must remain independent", card.contains("ValuePilotProductContextActionKind.PHOTO -> onOpenPhotos(match)"))
        assertTrue("Save action must remain a direct primary action", card.contains("content.addView(addHomeButton)"))
        assertTrue("Wrapped card content must be attached once", card.contains("card.addView(content)"))
        assertFalse("Save action must not overlap as a direct FrameLayout child", card.contains("card.addView(addHomeButton)"))
        assertFalse("Legacy direct photo button must not be recreated", card.contains("val photoButton ="))
    }

    @Test
    fun basketCollectionTapUsesTheIncrementalSurfaceUpdate() {
        val main = source("src/main/java/com/valuepilot/app/MainActivity.kt").readText()
        val start = main.indexOf("basketExperience.onPrivateIdentityToggle = {")
        val end = main.indexOf("basketExperience.onOpenPrivateIdentityPhotos", start)

        assertTrue("Expected Basket toggle owner callback", start >= 0)
        assertTrue("Expected Basket photo callback after toggle", end > start)
        val callback = main.substring(start, end)
        listOf(
            "homeSessionStore.save(PracticalShoppingHomeSession.snapshot(nextState))",
            "basketExperience.updatePrivateIdentityCollected(",
            "if (!updatedInPlace || shellState.selectedPrimaryTab != AppPrimaryTab.BASKET)"
        ).forEach { required ->
            assertTrue("Missing incremental Basket toggle boundary $required", callback.contains(required))
        }
    }

    @Test
    fun noNewStorageOrBroadPermissionIsAddedForTheViewer() {
        val manifest = source("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"))
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

    private companion object {
        const val GOOGLE_IMAGES_HOST = "www.google.com"
    }
}
