package com.valuepilot.app.next

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

internal fun valuePilotNextWebTextZoomPercent(fontScale: Float): Int =
    if (!fontScale.isFinite()) {
        100
    } else {
        (fontScale * 100f).roundToInt().coerceIn(100, 200)
    }

internal fun valuePilotNextWebCssInsetPx(systemInsetPx: Int, density: Float): Int =
    if (systemInsetPx <= 0) {
        0
    } else if (!density.isFinite() || density <= 0f) {
        systemInsetPx
    } else {
        (systemInsetPx / density).roundToInt()
    }

/** Debug-only host for the supplied Stitch HTML prototype. */
class ValuePilotNextHtmlPreviewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var journey: ValuePilotNextJourneyViewModel
    private val handler = Handler(Looper.getMainLooper())
    private var htmlTopInsetCssPx = 0
    private val previewPreferences by lazy {
        getSharedPreferences(PREVIEW_PREFERENCES_NAME, MODE_PRIVATE)
    }
    private var googleImagesLaunchInFlight = false
    private var nativeSearchQuery: String? = null
    private var nativeSearchInitialGeneration = -1L
    private var nativeSearchDeadlineMillis = 0L

    private val nativeSearchPoll = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || !::journey.isInitialized) return
            val query = nativeSearchQuery ?: return
            val search = journey.uiState.search
            val completed =
                search.query.trim() == query &&
                    search.generation > nativeSearchInitialGeneration &&
                    search.mode != com.valuepilot.app.ArgentinaLiveSearchSurfaceMode.LOADING
            if (completed || System.currentTimeMillis() >= nativeSearchDeadlineMillis) {
                nativeSearchQuery = null
                webView.evaluateJavascript(
                    "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeSearch(${ValuePilotNextHtmlNativeSearchBridge.encode(search)})",
                    null
                )
                return
            }
            handler.postDelayed(this, NATIVE_SEARCH_POLL_INTERVAL_MS)
        }
    }

    private val googleImagesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            googleImagesLaunchInFlight = false
        }

    private val themeSync = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || isFinishing || isDestroyed) return
            webView.evaluateJavascript(
                "document.documentElement.classList.contains('dark')"
            ) { result ->
                applySystemBars(result == "true")
            }
            handler.postDelayed(this, THEME_SYNC_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        journey = ViewModelProvider(this)[ValuePilotNextJourneyViewModel::class.java]
        journey.start(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Android 15 enforces edge-to-edge for the debug target. Keep the HTML
            // prototype below the status bar and above the gesture/navigation area
            // so system chrome never obscures the approved header or bottom nav.
            clipToPadding = true
            setBackgroundColor(Color.rgb(252, 249, 242))
            configureSecureSettings(settings)
            webViewClient = secureWebViewClient()
        }
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // WindowInsets are physical pixels; WebView CSS uses density-scaled pixels.
            htmlTopInsetCssPx = valuePilotNextWebCssInsetPx(
                systemBars.top,
                resources.displayMetrics.density
            )
            view.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            applyHtmlTopInset(webView)
            insets
        }
        setContentView(webView)
        applySystemBars(darkTheme = false)
        ViewCompat.requestApplyInsets(webView)
        registerRestrictedPhotoBridge()
        installBackHandling()
        webView.loadUrl(ASSET_INDEX_URL)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(themeSync)
        handler.post(themeSync)
        if (::webView.isInitialized) {
            handler.post { synchronizeLanguage(webView) }
        }
    }

    override fun onPause() {
        handler.removeCallbacks(themeSync)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(themeSync)
        handler.removeCallbacks(nativeSearchPoll)
        nativeSearchQuery = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun configureSecureSettings(settings: WebSettings) {
        settings.javaScriptEnabled = true
        // The preview stores its language preference in same-origin local storage. Network,
        // file access, and arbitrary bridge origins remain disabled below.
        settings.domStorageEnabled = true
        settings.databaseEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.setGeolocationEnabled(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = true
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.saveFormData = false
        // Preserve the approved layout at the default scale while honoring larger Android
        // accessibility font settings instead of pinning the WebView to 100%.
        settings.textZoom = valuePilotNextWebTextZoomPercent(resources.configuration.fontScale)
    }

    private fun secureWebViewClient(): WebViewClientCompat = object : WebViewClientCompat() {
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (url != ASSET_INDEX_URL) return
            applyHtmlTopInset(view)
            synchronizeLanguage(view)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val uri = request.url
            return if (isTrustedAssetUri(uri)) {
                assetLoader.shouldInterceptRequest(uri)
            } else {
                blockedResponse()
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = !isTrustedAssetUri(request.url)
    }

    private fun registerRestrictedPhotoBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(
            webView,
            "ValuePilotHost",
            setOf(ASSET_ORIGIN),
            object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(
                    view: WebView,
                    message: WebMessageCompat,
                    sourceOrigin: Uri,
                    isMainFrame: Boolean,
                    replyProxy: JavaScriptReplyProxy
                ) {
                    if (!isMainFrame || sourceOrigin.toString() != ASSET_ORIGIN) return
                    val language = ValuePilotNextHtmlPreviewContract
                        .parseSetLanguageMessage(message.data)
                    if (language != null) {
                        previewPreferences.edit()
                            .putString(PREF_LANGUAGE, language.language)
                            .apply()
                        saveNativeLanguagePreference(language.language)
                        return
                    }
                    val nativeSearch = ValuePilotNextHtmlPreviewContract
                        .parseNativeSearchMessage(message.data)
                    if (nativeSearch != null) {
                        startNativeSearch(nativeSearch.query)
                        return
                    }
                    val nativeProductDetail = ValuePilotNextHtmlPreviewContract
                        .parseNativeProductDetailMessage(message.data)
                    if (nativeProductDetail != null) {
                        openNativeProductDetail(nativeProductDetail)
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeSavedReadMessage(message.data) != null
                    ) {
                        sendNativeSavedState()
                        return
                    }
                    val nativeSavedTools = ValuePilotNextHtmlPreviewContract
                        .parseNativeSavedToolsMessage(message.data)
                    if (nativeSavedTools != null) {
                        openNativeSavedTools(nativeSavedTools.destination)
                        return
                    }
                    val nativeJourney = ValuePilotNextHtmlPreviewContract
                        .parseNativeJourneyMessage(message.data)
                    if (nativeJourney != null) {
                        openNativeJourney(nativeJourney.destination)
                        return
                    }
                    val nativeGoodPriceFromSaved = ValuePilotNextHtmlPreviewContract
                        .parseNativeGoodPriceFromSavedMessage(message.data)
                    if (nativeGoodPriceFromSaved != null) {
                        openNativeGoodPriceFromSaved(nativeGoodPriceFromSaved)
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeBasketMessage(message.data) != null
                    ) {
                        openNativeBasket()
                        return
                    }
                    val nativeSave = ValuePilotNextHtmlPreviewContract
                        .parseNativeSaveMessage(message.data)
                    if (nativeSave != null) {
                        saveNativeIdentity(nativeSave)
                        return
                    }
                    val nativeRemove = ValuePilotNextHtmlPreviewContract
                        .parseNativeRemoveMessage(message.data)
                    if (nativeRemove != null) {
                        removeNativeIdentity(nativeRemove.identityKey)
                        return
                    }
                    val nativeTrip = ValuePilotNextHtmlPreviewContract
                        .parseNativeTripMessage(message.data)
                    if (nativeTrip != null) {
                        calculateNativeTrip(nativeTrip)
                        return
                    }
                    val nativePhoto = ValuePilotNextHtmlPreviewContract
                        .parseNativeGoogleImagesMessage(message.data)
                    if (nativePhoto != null) {
                        openGoogleImages(nativePhoto)
                        return
                    }
                    val request = ValuePilotNextHtmlPreviewContract
                        .parseOpenGoogleImagesMessage(message.data)
                        ?: return
                    openGoogleImages(request)
                }
            }
        )
    }

    private fun startNativeSearch(query: String) {
        if (!::journey.isInitialized) return
        handler.removeCallbacks(nativeSearchPoll)
        nativeSearchQuery = query
        nativeSearchInitialGeneration = journey.uiState.search.generation
        nativeSearchDeadlineMillis = System.currentTimeMillis() + NATIVE_SEARCH_TIMEOUT_MS
        journey.queryChanged(query)
        journey.submitSearch(query)
        handler.post(nativeSearchPoll)
    }

    private fun sendNativeSavedState(action: String? = null) {
        if (!::journey.isInitialized || !::webView.isInitialized) return
        journey.refreshPrivateIdentitiesFromStore()
        val payload = ValuePilotNextHtmlNativeSearchBridge.encodeSaved(
            identities = journey.uiState.privateIdentities,
            action = action,
            privateHistory = journey.privatePriceHistorySummary()
        )
        webView.evaluateJavascript(
            "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeSaved($payload)",
            null
        )
    }

    private fun saveNativeIdentity(request: ValuePilotNextHtmlNativeSaveRequest) {
        val candidate = journey.uiState.search.presentation
            ?.matches
            ?.firstOrNull { it.identityKey == request.identityKey }
        val exact = candidate?.takeIf { match ->
            match.displayName == request.displayName &&
                match.brand == request.brand &&
                match.matchLabel == request.matchLabel &&
                match.packageQuantityLabel == request.packageQuantityLabel &&
                match.sourceLabel == request.sourceLabel &&
                match.gtin == request.gtin
        }
        val result = exact?.let(journey::addSearchMatch)
            ?: AddPrivateIdentityResult.SOURCE_UNAVAILABLE
        sendNativeSavedState("SAVE_${result.name}")
    }

    private fun removeNativeIdentity(identityKey: String) {
        val removed = journey.removePrivateIdentity(identityKey)
        sendNativeSavedState(if (removed) "REMOVE_SUCCESS" else "REMOVE_MISSING")
    }

    private fun calculateNativeTrip(request: ValuePilotNextHtmlNativeTripRequest) {
        if (!::journey.isInitialized || !::webView.isInitialized) return
        journey.calculateTrip(
            oneWayDistance = request.oneWayDistance,
            fuelConsumption = request.fuelConsumption,
            fuelPrice = request.fuelPrice,
            tolls = request.tolls,
            parking = request.parking
        )
        val payload = ValuePilotNextHtmlNativeSearchBridge.encodeTrip(
            result = journey.uiState.tripResult,
            error = journey.uiState.tripError
        )
        webView.evaluateJavascript(
            "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeTrip($payload)",
            null
        )
    }

    private fun openNativeSavedTools(destination: String) {
        startActivity(
            Intent(this, ValuePilotNextActivity::class.java)
                .putExtra(ValuePilotNextActivity.EXTRA_START_DESTINATION, destination)
                .putExtra(ValuePilotNextActivity.EXTRA_START_ORIGIN, "SAVED")
        )
    }

    private fun openNativeJourney(destination: String) {
        startActivity(
            Intent(this, ValuePilotNextActivity::class.java)
                .putExtra(ValuePilotNextActivity.EXTRA_START_DESTINATION, destination)
                .putExtra(ValuePilotNextActivity.EXTRA_START_ORIGIN, "SAVED")
        )
    }

    private fun openNativeGoodPriceFromSaved(
        request: ValuePilotNextHtmlNativeGoodPriceFromSavedRequest
    ) {
        val identity = journey.uiState.privateIdentities.firstOrNull {
            it.identityKey == request.identityKey && it.displayName == request.displayName
        } ?: run {
            sendNativeActionNotice("SAVED_ITEM_STALE")
            return
        }
        startActivity(
            Intent(this, ValuePilotNextActivity::class.java)
                .putExtra(ValuePilotNextActivity.EXTRA_START_DESTINATION, "GOOD_PRICE_CHECK")
                .putExtra(ValuePilotNextActivity.EXTRA_START_ORIGIN, "SAVED")
                .putExtra(ValuePilotNextActivity.EXTRA_GOOD_PRICE_DISPLAY_NAME, identity.displayName)
        )
    }

    private fun openNativeProductDetail(
        request: ValuePilotNextHtmlNativeProductDetailRequest
    ) {
        val presentation = journey.uiState.search.presentation ?: run {
            sendNativeActionNotice("PRODUCT_DETAIL_STALE")
            return
        }
        val match = presentation.matches.firstOrNull {
            it.identityKey == request.identityKey
        } ?: run {
            sendNativeActionNotice("PRODUCT_DETAIL_STALE")
            return
        }
        if (!ValuePilotNextHtmlPreviewContract.productDetailQueryMatches(
                journey.uiState.search.query,
                request.query
            )
        ) {
            sendNativeActionNotice("PRODUCT_DETAIL_STALE")
            return
        }
        val handoff = ValuePilotNextProductDetailHandoffCodec.encodeOrNull(
            query = request.query,
            presentation = presentation,
            selectedIdentityKey = match.identityKey
        )
        val intent = Intent(this, ValuePilotNextActivity::class.java)
                .putExtra(ValuePilotNextActivity.EXTRA_START_DESTINATION, "PRODUCT_DETAIL")
                .putExtra(
                    ValuePilotNextActivity.EXTRA_START_SEARCH_QUERY,
                    request.query.trim()
                )
                .putExtra(ValuePilotNextActivity.EXTRA_START_ORIGIN, "SEARCH")
                .putExtra(ValuePilotNextActivity.EXTRA_START_IDENTITY_KEY, match.identityKey)
        handoff?.let { payload ->
            intent.putExtra(ValuePilotNextActivity.EXTRA_PRODUCT_DETAIL_HANDOFF, payload)
        }
        startActivity(intent)
    }

    private fun sendNativeActionNotice(code: String) {
        if (!::webView.isInitialized) return
        val safeCode = when (code) {
            "PRODUCT_DETAIL_STALE",
            "SAVED_ITEM_STALE" -> code
            else -> return
        }
        webView.evaluateJavascript(
            "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeActionNotice({code:'$safeCode'})",
            null
        )
    }

    private fun openNativeBasket() {
        startActivity(
            Intent(this, ValuePilotNextActivity::class.java)
                .putExtra(ValuePilotNextActivity.EXTRA_START_DESTINATION, "BASKET")
        )
    }

    /** Keeps the HTML shell and Compose journeys on the same persisted language choice. */
    private fun synchronizeLanguage(view: WebView) {
        val nativeStore = ValuePilotNextUiPreferencesStore(applicationContext)
        val nativePreferences = nativeStore.load()
        val language = when (nativePreferences.language) {
            ValuePilotNextLanguage.ENGLISH -> "en"
            ValuePilotNextLanguage.SPANISH_AR -> "es-AR"
            ValuePilotNextLanguage.SYSTEM -> {
                val resolved = ValuePilotNextUiPreferencesCodec.resolvesLanguage(
                    nativePreferences.language
                )
                if (resolved == ValuePilotNextLanguage.SPANISH_AR) "es-AR" else "en"
            }
        }
        previewPreferences.edit().apply {
            if (nativePreferences.language == ValuePilotNextLanguage.SYSTEM) {
                remove(PREF_LANGUAGE)
            } else {
                putString(PREF_LANGUAGE, language)
            }
        }
        view.evaluateJavascript(
            "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.setLanguage('$language');" +
                "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.refreshNativeSaved && " +
                "window.ValuePilotHtmlPreview.refreshNativeSaved()",
            null
        )
    }

    private fun saveNativeLanguagePreference(language: String) {
        val next = when (language) {
            "es-AR" -> ValuePilotNextLanguage.SPANISH_AR
            "en" -> ValuePilotNextLanguage.ENGLISH
            else -> return
        }
        val store = ValuePilotNextUiPreferencesStore(applicationContext)
        val current = store.load()
        if (current.language != next) store.save(current.copy(language = next))
    }

    private fun installBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!::webView.isInitialized) {
                        finish()
                        return
                    }
                    webView.evaluateJavascript(
                        "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.back()"
                    ) { result ->
                        if (result != "true") finish()
                    }
                }
            }
        )
    }

    private fun openGoogleImages(request: ValuePilotNextHtmlPhotoRequest) {
        if (googleImagesLaunchInFlight) return
        val uri = ValuePilotNextHtmlPreviewContract.urlFor(request).toUri()
        openGoogleImages(uri)
    }

    private fun openGoogleImages(request: ValuePilotNextHtmlNativePhotoRequest) {
        if (googleImagesLaunchInFlight) return
        val uri = ValuePilotNextHtmlPreviewContract.urlFor(request).toUri()
        openGoogleImages(uri)
    }

    private fun openGoogleImages(uri: Uri) {
        if (googleImagesLaunchInFlight) return
        googleImagesLaunchInFlight = true
        val customTab = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setInitialActivityHeightPx(
                initialActivityHeightPx(),
                CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE
            )
            .build()
            .also { tab ->
                tab.intent.data = uri
                CustomTabsClient.getPackageName(this, null)?.let(tab.intent::setPackage)
            }
        try {
            googleImagesLauncher.launch(customTab.intent)
        } catch (_: ActivityNotFoundException) {
            googleImagesLaunchInFlight = false
            launchFullScreenFallback(uri)
        } catch (_: SecurityException) {
            googleImagesLaunchInFlight = false
            launchFullScreenFallback(uri)
        }
    }

    private fun launchFullScreenFallback(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                valuePilotNextText(this, "No browser is available.", "No hay un navegador disponible."),
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: SecurityException) {
            Toast.makeText(
                this,
                valuePilotNextText(this, "Google Images could not be opened.", "No se pudo abrir Google Images."),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun initialActivityHeightPx(): Int = maxOf(
        resources.displayMetrics.heightPixels * INITIAL_HEIGHT_PERCENT / 100,
        (MIN_INITIAL_HEIGHT_DP * resources.displayMetrics.density).toInt()
    )

    private fun applySystemBars(darkTheme: Boolean) {
        val background = if (darkTheme) Color.rgb(22, 23, 20) else Color.rgb(252, 249, 242)
        window.statusBarColor = background
        window.navigationBarColor = background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    private fun applyHtmlTopInset(view: WebView) {
        view.evaluateJavascript(
            "document.documentElement.style.setProperty('--vp-native-top-inset', '${htmlTopInsetCssPx}px')",
            null
        )
    }

    private fun isTrustedAssetUri(uri: Uri): Boolean =
        uri.scheme == "https" &&
            uri.host == ASSET_HOST &&
            uri.path?.startsWith("/assets/valuepilot_next_html/") == true

    private fun blockedResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0))
    )

    private companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val ASSET_ORIGIN = "https://appassets.androidplatform.net"
        const val ASSET_INDEX_URL = "https://appassets.androidplatform.net/assets/valuepilot_next_html/index.html"
        const val INITIAL_HEIGHT_PERCENT = 65
        const val MIN_INITIAL_HEIGHT_DP = 320
        const val THEME_SYNC_INTERVAL_MS = 300L
        const val NATIVE_SEARCH_POLL_INTERVAL_MS = 120L
        const val NATIVE_SEARCH_TIMEOUT_MS = 20_000L
        const val PREVIEW_PREFERENCES_NAME = "valuepilot_next_html_preview"
        const val PREF_LANGUAGE = "language"
    }
}
