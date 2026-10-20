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
import com.valuepilot.app.AndroidUserObservedPriceProofContentPicker
import com.valuepilot.app.AndroidUserObservedPriceProofContentSource
import com.valuepilot.app.UserObservedPriceProofContentReadResult
import com.valuepilot.app.UserObservedPriceSavedPrefillCheckUiAction
import com.valuepilot.app.UserObservedPriceSavedSelectionAction
import com.valuepilot.app.UserProvidedPriceProofType
import com.valuepilot.app.StapleWatchSavedIdentityHandoffUiAction
import com.valuepilot.app.StapleWatchSavedIdentitySelectionAction
import com.valuepilot.core.ShoppingItemKey
import com.valuepilot.core.ShoppingStoreKey
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
    private var htmlBottomInsetCssPx = 0
    private val previewPreferences by lazy {
        getSharedPreferences(PREVIEW_PREFERENCES_NAME, MODE_PRIVATE)
    }
    private var googleImagesLaunchInFlight = false
    private var locationPicker: ValuePilotNextLocationPicker? = null
    private var nativeSearchQuery: String? = null
    private var nativeSearchSelectionId: String? = null
    private var nativeSearchInitialGeneration = -1L
    private var nativeSearchDeadlineMillis = 0L
    private var nativeBasketPlanDeadlineMillis = 0L
    private var nativeObservedPriceDeadlineMillis = 0L
    private var nativeObservedPricePrefillDeadlineMillis = 0L
    private var nativeObservedPricePrefillRequested = false
    private var nativeStapleWatchDeadlineMillis = 0L
    private var observedPriceProofPicker: AndroidUserObservedPriceProofContentPicker? = null

    private val nativeSearchPoll = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || !::journey.isInitialized) return
            val query = nativeSearchQuery ?: return
            val search = journey.uiState.search
            val expectedSelectionId = nativeSearchSelectionId
            val completed =
                search.query.trim() == query &&
                    search.selectionId == expectedSelectionId &&
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

    private val nativeBasketPlanPoll = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || !::journey.isInitialized) return
            if (!journey.uiState.planLoading ||
                System.currentTimeMillis() >= nativeBasketPlanDeadlineMillis
            ) {
                sendNativeSavedState()
                return
            }
            handler.postDelayed(this, NATIVE_BASKET_PLAN_POLL_INTERVAL_MS)
        }
    }

    private val nativeObservedPricePoll = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || !::journey.isInitialized) return
            val ready = journey.uiState.observedPriceSelection != null
            if (ready || System.currentTimeMillis() >= nativeObservedPriceDeadlineMillis) {
                sendNativeObservedPriceState()
                return
            }
            handler.postDelayed(this, NATIVE_OBSERVED_PRICE_POLL_INTERVAL_MS)
        }
    }

    private val nativeObservedPricePrefillPoll = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || !::journey.isInitialized) return
            val attempt = journey.currentObservedPricePrefillAttemptOrNull()
            if (attempt != null) {
                nativeObservedPricePrefillRequested = false
                if (journey.prepareObservedPriceConfirmation(attempt)) {
                    journey.onObservedPriceConfirmationRouteVisibilityChanged(true)
                }
                sendNativeObservedPriceState()
                return
            }
            val result = journey.uiState.observedPricePrefillResult
            if (result != null ||
                System.currentTimeMillis() >= nativeObservedPricePrefillDeadlineMillis
            ) {
                nativeObservedPricePrefillRequested = false
                sendNativeObservedPriceState()
                return
            }
            handler.postDelayed(this, NATIVE_OBSERVED_PRICE_POLL_INTERVAL_MS)
        }
    }

    private val nativeObservedPriceConfirmationPoll = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || !::journey.isInitialized) return
            if (journey.uiState.observedPriceConfirmation?.action?.status ==
                com.valuepilot.app.UserObservedPriceConfirmationActionUiState.Status.SUBMITTING
            ) {
                handler.postDelayed(this, NATIVE_OBSERVED_PRICE_POLL_INTERVAL_MS)
            } else {
                sendNativeObservedPriceState()
            }
        }
    }

    private val nativeStapleWatchPoll = object : Runnable {
        override fun run() {
            if (!::webView.isInitialized || !::journey.isInitialized) return
            if (
                journey.uiState.stapleWatchSetup != null ||
                System.currentTimeMillis() >= nativeStapleWatchDeadlineMillis
            ) {
                sendNativeStapleWatchState()
                return
            }
            handler.postDelayed(this, NATIVE_STAPLE_WATCH_POLL_INTERVAL_MS)
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
        locationPicker = ValuePilotNextLocationPicker(this, ::locationText) { area ->
            if (journey.selectSearchArea(area)) {
                nativeSearchQuery = null
                nativeSearchSelectionId = null
                handler.removeCallbacks(nativeSearchPoll)
                webView.evaluateJavascript(
                    "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeLocation(${org.json.JSONObject.quote(area.label)},true)", null)
                sendNativeSavedState()
            } else {
                android.widget.Toast.makeText(this,
                    locationText("No se pudo guardar la ubicación. Probá de nuevo.", "The location could not be saved. Please retry."),
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
        journey.onSavedRouteVisibilityChanged(true)
        observedPriceProofPicker = AndroidUserObservedPriceProofContentPicker(
            activity = this,
            contentSource = AndroidUserObservedPriceProofContentSource(contentResolver),
            onReadResult = ::onObservedPriceProofReadResult
        ).also { picker -> journey.attachObservedPriceProofPicker(picker::launch) }
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
            // Android 15 forces edge-to-edge, so the browser's safe-area env()
            // stays zero while the system gesture bar still overlaps the page.
            // The real bottom inset is bridged into CSS instead.
            htmlBottomInsetCssPx = valuePilotNextWebCssInsetPx(
                systemBars.bottom,
                resources.displayMetrics.density
            )
            view.setPadding(systemBars.left, 0, systemBars.right, 0)
            applyHtmlInsets(webView)
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
        locationPicker?.close()
        locationPicker = null
        handler.removeCallbacks(nativeSearchPoll)
        handler.removeCallbacks(nativeBasketPlanPoll)
        handler.removeCallbacks(nativeObservedPricePoll)
        handler.removeCallbacks(nativeObservedPricePrefillPoll)
        handler.removeCallbacks(nativeObservedPriceConfirmationPoll)
        handler.removeCallbacks(nativeStapleWatchPoll)
        nativeSearchQuery = null
        nativeSearchSelectionId = null
        nativeObservedPricePrefillRequested = false
        if (::journey.isInitialized) {
            journey.onObservedPriceConfirmationRouteVisibilityChanged(false)
            journey.onObservedPriceSelectionRouteVisibilityChanged(false)
            journey.onStapleWatchSetupRouteVisibilityChanged(false)
            journey.onSavedRouteVisibilityChanged(false)
            journey.attachObservedPriceProofPicker(null)
        }
        observedPriceProofPicker?.close()
        observedPriceProofPicker = null
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
            applyHtmlInsets(view)
            synchronizeLanguage(view)
            view.evaluateJavascript(
                "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeLocation(${org.json.JSONObject.quote(journey.uiState.search.areaLabel)})", null)
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
                    if (ValuePilotNextHtmlPreviewContract.isChooseSearchLocationMessage(message.data)) {
                        locationPicker?.open()
                        return
                    }
                    val nativeSearch = ValuePilotNextHtmlPreviewContract
                        .parseNativeSearchMessage(message.data)
                    if (nativeSearch != null) {
                        startNativeSearch(nativeSearch.query, nativeSearch.selectionId, nativeSearch.language)
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeSearchLoadMoreMessage(message.data) != null
                    ) {
                        loadMoreNativeSearch()
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeSearchPreviousMessage(message.data) != null
                    ) {
                        loadMoreNativeSearch(previous = true)
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeSearchFacetMoreMessage(message.data) != null
                    ) {
                        loadMoreNativeSearchClarification()
                        return
                    }
                    val nativeProductDetail = ValuePilotNextHtmlPreviewContract
                        .parseNativeProductDetailMessage(message.data)
                    if (nativeProductDetail != null) {
                        openNativeProductDetail(nativeProductDetail)
                        return
                    }
                    val nativeMap = ValuePilotNextHtmlPreviewContract
                        .parseNativeMapMessage(message.data)
                    if (nativeMap != null) {
                        openNativeMapContext(nativeMap)
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
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeBasketPlanMessage(message.data) != null
                    ) {
                        requestNativeBasketPlan()
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeObservedPriceReadMessage(message.data) != null
                    ) {
                        startNativeObservedPrice()
                        return
                    }
                    val observedSelection = ValuePilotNextHtmlPreviewContract
                        .parseNativeObservedPriceSelectionMessage(message.data)
                    if (observedSelection != null) {
                        applyNativeObservedPriceSelection(observedSelection)
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeObservedPricePrefillMessage(message.data) != null
                    ) {
                        requestNativeObservedPricePrefill()
                        return
                    }
                    val observedAmount = ValuePilotNextHtmlPreviewContract
                        .parseNativeObservedPriceAmountMessage(message.data)
                    if (observedAmount != null) {
                        journey.onObservedPriceConfirmationPriceInput(
                            amountText = observedAmount.amount,
                            currencyCodeText = observedAmount.currency
                        )
                        sendNativeObservedPriceState()
                        return
                    }
                    val observedTime = ValuePilotNextHtmlPreviewContract
                        .parseNativeObservedPriceTimeMessage(message.data)
                    if (observedTime != null) {
                        journey.onObservedPriceConfirmationTimeInput(
                            dateText = observedTime.date,
                            timeText = observedTime.time,
                            utcOffsetText = observedTime.utcOffset
                        )
                        sendNativeObservedPriceState()
                        return
                    }
                    val observedProof = ValuePilotNextHtmlPreviewContract
                        .parseNativeObservedPriceProofMessage(message.data)
                    if (observedProof != null) {
                        journey.onObservedPriceConfirmationProofReferenceInput(
                            artifactId = observedProof.artifactId,
                            proofType = when (observedProof.proofType) {
                                "RECEIPT" -> UserProvidedPriceProofType.RECEIPT
                                "PRICE_TAG" -> UserProvidedPriceProofType.PRICE_TAG
                                else -> null
                            }
                        )
                        sendNativeObservedPriceState()
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeObservedPriceProofPickerMessage(message.data) != null
                    ) {
                        observedPriceProofPicker?.launch()
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeObservedPriceSubmitMessage(message.data) != null
                    ) {
                        submitNativeObservedPrice()
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeObservedPriceCancelMessage(message.data) != null
                    ) {
                        cancelNativeObservedPrice()
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeStapleWatchReadMessage(message.data) != null
                    ) {
                        startNativeStapleWatch()
                        return
                    }
                    val stapleWatchAction = ValuePilotNextHtmlPreviewContract
                        .parseNativeStapleWatchActionMessage(message.data)
                    if (stapleWatchAction != null) {
                        applyNativeStapleWatchAction(stapleWatchAction)
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeStapleWatchSubmitMessage(message.data) != null
                    ) {
                        submitNativeStapleWatch()
                        return
                    }
                    if (ValuePilotNextHtmlPreviewContract
                            .parseNativeStapleWatchCancelMessage(message.data) != null
                    ) {
                        cancelNativeStapleWatch()
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

    private fun locationText(spanish: String, english: String): String {
        val language = previewPreferences.getString(PREF_LANGUAGE, null)
            ?: resources.configuration.locales[0].language
        return if (language.startsWith("es")) spanish else english
    }

    private fun startNativeSearch(
        rawQuery: String,
        selectionId: String? = null,
        language: String = "es"
    ) {
        if (!::journey.isInitialized) return
        val query = normalizeNativeSearchQuery(rawQuery)
        handler.removeCallbacks(nativeSearchPoll)
        nativeSearchQuery = query
        nativeSearchSelectionId = selectionId
        nativeSearchInitialGeneration = journey.uiState.search.generation
        nativeSearchDeadlineMillis = System.currentTimeMillis() + NATIVE_SEARCH_TIMEOUT_MS
        journey.queryChanged(query)
        journey.submitSearch(query, selectionId, language)
        handler.post(nativeSearchPoll)
    }

    private fun loadMoreNativeSearch(previous: Boolean = false) {
        if (!::journey.isInitialized) return
        val query = nativeSearchQuery ?: journey.uiState.search.query.trim()
        if (query.isBlank()) return
        handler.removeCallbacks(nativeSearchPoll)
        nativeSearchQuery = query
        nativeSearchInitialGeneration = journey.uiState.search.generation
        nativeSearchDeadlineMillis = System.currentTimeMillis() + NATIVE_SEARCH_TIMEOUT_MS
        if (previous) journey.loadPreviousSearchResults() else journey.loadMoreSearchResults()
        handler.post(nativeSearchPoll)
    }

    private fun loadMoreNativeSearchClarification() {
        if (!::journey.isInitialized) return
        val query = nativeSearchQuery ?: journey.uiState.search.query.trim()
        if (query.isBlank()) return
        handler.removeCallbacks(nativeSearchPoll)
        nativeSearchQuery = query
        nativeSearchInitialGeneration = journey.uiState.search.generation
        nativeSearchDeadlineMillis = System.currentTimeMillis() + NATIVE_SEARCH_TIMEOUT_MS
        journey.loadMoreSearchClarification()
        handler.post(nativeSearchPoll)
    }

    /** Keep the English HTML labels useful against the Spanish Argentina catalog. */
    private fun normalizeNativeSearchQuery(rawQuery: String): String {
        return ValuePilotNextSearchQueryNormalizer.canonical(rawQuery)
    }

    private fun sendNativeSavedState(action: String? = null) {
        if (!::journey.isInitialized || !::webView.isInitialized) return
        journey.refreshPrivateIdentitiesFromStore()
        val payload = ValuePilotNextHtmlNativeSearchBridge.encodeSaved(
            identities = journey.uiState.privateIdentities,
            action = action,
            privateHistory = journey.privatePriceHistorySummary(),
            basket = journey.uiState.basket,
            planLoading = journey.uiState.planLoading
        )
        webView.evaluateJavascript(
            "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeSaved($payload)",
            null
        )
    }

    private fun startNativeObservedPrice() {
        if (!::journey.isInitialized || !::webView.isInitialized) return
        journey.onSavedRouteVisibilityChanged(true)
        journey.onObservedPriceSelectionRouteVisibilityChanged(true)
        nativeObservedPriceDeadlineMillis =
            System.currentTimeMillis() + NATIVE_OBSERVED_PRICE_TIMEOUT_MS
        sendNativeObservedPriceState()
        handler.removeCallbacks(nativeObservedPricePoll)
        handler.post(nativeObservedPricePoll)
    }

    private fun sendNativeObservedPriceState() {
        if (!::journey.isInitialized || !::webView.isInitialized) return
        val payload = ValuePilotNextHtmlNativeSearchBridge.encodeObservedPrice(
            selection = journey.uiState.observedPriceSelection,
            prefill = journey.uiState.observedPricePrefillResult,
            confirmation = journey.uiState.observedPriceConfirmation
        )
        webView.evaluateJavascript(
            "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeObservedPrice($payload)",
            null
        )
    }

    private fun applyNativeObservedPriceSelection(
        request: ValuePilotNextHtmlNativeObservedPriceSelectionRequest
    ) {
        if (!::journey.isInitialized) return
        val action = runCatching {
            when (request.kind) {
                "PRODUCT" -> UserObservedPriceSavedSelectionAction.SelectProduct(
                    ShoppingItemKey(requireNotNull(request.key))
                )

                "STORE" -> UserObservedPriceSavedSelectionAction.SelectStore(
                    ShoppingStoreKey(requireNotNull(request.key))
                )

                "CLEAR_PRODUCT" -> UserObservedPriceSavedSelectionAction.ClearProduct
                "CLEAR_STORE" -> UserObservedPriceSavedSelectionAction.ClearStore
                "CLEAR_SELECTION" -> UserObservedPriceSavedSelectionAction.ClearSelection
                else -> null
            }
        }.getOrNull() ?: return
        journey.onObservedPriceSelectionAction(action)
        sendNativeObservedPriceState()
    }

    private fun requestNativeObservedPricePrefill() {
        if (!::journey.isInitialized) return
        journey.onCheckObservedPricePrefill(UserObservedPriceSavedPrefillCheckUiAction.Request)
        nativeObservedPricePrefillRequested = true
        nativeObservedPricePrefillDeadlineMillis =
            System.currentTimeMillis() + NATIVE_OBSERVED_PRICE_TIMEOUT_MS
        handler.removeCallbacks(nativeObservedPricePrefillPoll)
        handler.post(nativeObservedPricePrefillPoll)
    }

    private fun submitNativeObservedPrice() {
        if (!::journey.isInitialized) return
        journey.onObservedPriceConfirmationSubmitRequested()
        sendNativeObservedPriceState()
        handler.removeCallbacks(nativeObservedPriceConfirmationPoll)
        handler.post(nativeObservedPriceConfirmationPoll)
    }

    private fun cancelNativeObservedPrice() {
        if (!::journey.isInitialized) return
        nativeObservedPricePrefillRequested = false
        handler.removeCallbacks(nativeObservedPricePrefillPoll)
        handler.removeCallbacks(nativeObservedPriceConfirmationPoll)
        journey.cancelObservedPriceConfirmation()
        journey.onObservedPriceConfirmationRouteVisibilityChanged(false)
        sendNativeObservedPriceState()
    }

    private fun startNativeStapleWatch() {
        if (!::journey.isInitialized || !::webView.isInitialized) return
        journey.onSavedRouteVisibilityChanged(true)
        journey.onStapleWatchSetupRouteVisibilityChanged(true)
        nativeStapleWatchDeadlineMillis =
            System.currentTimeMillis() + NATIVE_STAPLE_WATCH_TIMEOUT_MS
        sendNativeStapleWatchState()
        handler.removeCallbacks(nativeStapleWatchPoll)
        handler.post(nativeStapleWatchPoll)
    }

    private fun sendNativeStapleWatchState() {
        if (!::journey.isInitialized || !::webView.isInitialized) return
        val payload = ValuePilotNextHtmlNativeSearchBridge.encodeStapleWatch(
            journey.uiState.stapleWatchSetup
        )
        webView.evaluateJavascript(
            "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeStapleWatch($payload)",
            null
        )
    }

    private fun applyNativeStapleWatchAction(
        request: ValuePilotNextHtmlNativeStapleWatchActionRequest
    ) {
        if (!::journey.isInitialized) return
        val action = runCatching {
            when (request.kind) {
                "PRODUCT" -> StapleWatchSavedIdentitySelectionAction.SetProductWatched(
                    itemKey = ShoppingItemKey(requireNotNull(request.key)),
                    watched = requireNotNull(request.watched)
                )

                "STORE" -> StapleWatchSavedIdentitySelectionAction.SelectUsualStore(
                    ShoppingStoreKey(requireNotNull(request.key))
                )

                "CLEAR_STORE" -> StapleWatchSavedIdentitySelectionAction.ClearUsualStore
                "CLEAR_SELECTION" -> StapleWatchSavedIdentitySelectionAction.ClearSelection
                else -> null
            }
        }.getOrNull() ?: return
        journey.onStapleWatchSetupAction(action)
        sendNativeStapleWatchState()
    }

    private fun submitNativeStapleWatch() {
        if (!::journey.isInitialized) return
        journey.onStapleWatchSetupContinue(StapleWatchSavedIdentityHandoffUiAction.Request)
        sendNativeStapleWatchState()
    }

    private fun cancelNativeStapleWatch() {
        handler.removeCallbacks(nativeStapleWatchPoll)
        if (::journey.isInitialized) {
            journey.onStapleWatchSetupRouteVisibilityChanged(false)
        }
        if (::webView.isInitialized) {
            webView.evaluateJavascript(
                "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.returnToSaved && window.ValuePilotHtmlPreview.returnToSaved()",
                null
            )
        }
    }

    private fun onObservedPriceProofReadResult(result: UserObservedPriceProofContentReadResult) {
        if (!::journey.isInitialized) return
        journey.onObservedPriceProofContentReadResult(result)
        sendNativeObservedPriceState()
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

    private fun requestNativeBasketPlan() {
        if (!::journey.isInitialized || !::webView.isInitialized) return
        handler.removeCallbacks(nativeBasketPlanPoll)
        journey.refreshPrivateIdentitiesFromStore()
        journey.requestBasketPlan()
        nativeBasketPlanDeadlineMillis =
            System.currentTimeMillis() + NATIVE_BASKET_PLAN_TIMEOUT_MS
        sendNativeSavedState()
        if (journey.uiState.planLoading) {
            handler.post(nativeBasketPlanPoll)
        }
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

    /**
     * The Compose preview host was retired when the HTML surface became the
     * product. Deep journeys now open the existing native activities; each of
     * these remains the authoritative implementation of its own evidence flow.
     */
    private fun openNativeSavedTools(destination: String) {
        openNativeJourney(destination)
    }

    private fun openNativeJourney(destination: String) {
        val target = when (destination) {
            "GOOD_PRICE_CHECK" -> com.valuepilot.app.GoodPriceActivity::class.java
            "COMPARE" -> com.valuepilot.app.ComparisonActivity::class.java
            else -> com.valuepilot.app.MainActivity::class.java
        }
        startActivity(Intent(this, target))
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
            Intent(this, com.valuepilot.app.GoodPriceActivity::class.java)
                .putExtra(com.valuepilot.app.GoodPriceActivity.EXTRA_PRODUCT_NAME, identity.displayName)
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
        val payload = ValuePilotNextHtmlNativeSearchBridge.encodeProductDetail(
            query = request.query.trim(),
            match = match
        )
        webView.evaluateJavascript(
            "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeProductDetail($payload)",
            null
        )
    }

    private fun openNativeMapContext(
        request: ValuePilotNextHtmlNativeMapRequest
    ) {
        if (!::journey.isInitialized) return
        val presentation = journey.uiState.search.presentation
        val match = presentation?.matches?.firstOrNull { it.identityKey == request.identityKey }
        if (match == null || !ValuePilotNextHtmlPreviewContract.productDetailQueryMatches(
                journey.uiState.search.query,
                request.query
            )
        ) {
            sendNativeActionNotice("MAP_UNAVAILABLE")
            return
        }
        val offer = match.qualifiedOffers.firstOrNull { it.offerKey == request.offerKey }
        if (offer == null) {
            sendNativeActionNotice("MAP_UNAVAILABLE")
            return
        }
        val context = com.valuepilot.app.ArgentinaQualifiedStoreMapContextPresentation
            .fromOffer(offer, match.displayName)
        val latitude = context.latitude
        val longitude = context.longitude
        if (latitude == null || longitude == null) {
            sendNativeActionNotice("MAP_UNAVAILABLE")
            return
        }
        // This is the same future map-adapter seam used by Compose: source
        // coordinates only, with no route, stock, or availability inference.
        val uri = ("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(context.storeName)})").toUri()
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            sendNativeActionNotice("MAP_UNAVAILABLE")
        }
    }

    private fun sendNativeActionNotice(code: String) {
        if (!::webView.isInitialized) return
        val safeCode = when (code) {
            "PRODUCT_DETAIL_STALE",
            "SAVED_ITEM_STALE",
            "MAP_UNAVAILABLE" -> code
            else -> return
        }
        webView.evaluateJavascript(
            "window.ValuePilotHtmlPreview && window.ValuePilotHtmlPreview.receiveNativeActionNotice({code:'$safeCode'})",
            null
        )
    }

    private fun openNativeBasket() {
        startActivity(Intent(this, com.valuepilot.app.MainActivity::class.java))
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

    private fun applyHtmlInsets(view: WebView) {
        view.evaluateJavascript(
            "document.documentElement.style.setProperty('--vp-native-top-inset', '${htmlTopInsetCssPx}px');" +
                "document.documentElement.style.setProperty('--vp-native-bottom-inset', '${htmlBottomInsetCssPx}px')",
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
        const val NATIVE_BASKET_PLAN_POLL_INTERVAL_MS = 160L
        const val NATIVE_BASKET_PLAN_TIMEOUT_MS = 20_000L
        const val NATIVE_OBSERVED_PRICE_POLL_INTERVAL_MS = 120L
        const val NATIVE_OBSERVED_PRICE_TIMEOUT_MS = 20_000L
        const val NATIVE_STAPLE_WATCH_POLL_INTERVAL_MS = 120L
        const val NATIVE_STAPLE_WATCH_TIMEOUT_MS = 20_000L
        const val PREVIEW_PREFERENCES_NAME = "valuepilot_next_html_preview"
        const val PREF_LANGUAGE = "language"
    }
}
