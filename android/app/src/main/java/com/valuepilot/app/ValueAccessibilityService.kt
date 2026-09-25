package com.valuepilot.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.content.edit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ValueAccessibilityService : AccessibilityService() {
    private data class NavigationAttempt(
        val locator: ItemLocator,
        var totalSteps: Int = 0,
        var phaseSteps: Int = 0,
        var forward: Boolean = true,
        var staleRounds: Int = 0,
        var previousPageFingerprint: String? = null
    )

    private data class RankingRequest(
        val sequence: Long,
        val context: SearchContext?,
        val items: List<ValueItem>,
        val mode: RankMode,
        val maxPrice: Double?,
        val foodOnly: Boolean,
        val excludePork: Boolean,
        val useMemberPrices: Boolean
    )

    private val handler = Handler(Looper.getMainLooper())
    private val productStore = IncrementalProductStore(maxItems = 1_000)
    private val sessions = SearchSessionManager()
    private val productParser: ProductParser = EnrichingProductParser(LocalModelSemanticEnricher)
    private val workExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ValuePilot-parser").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val screenshotExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "ValuePilot-screenshot") }

    private var overlay: OverlayController? = null
    private var mode = RankMode.SMART
    private var refreshRunnable: Runnable? = null
    private var refreshDueAtMillis = 0L
    private var scanInFlight = false
    private var rescanRequested = false
    private var deferredScanCompletion: (() -> Unit)? = null
    private var publishSequence = 0L
    private val rankingLock = Any()
    private var pendingRanking: RankingRequest? = null
    private var rankingWorkerScheduled = false
    private var contextGeneration = 0L
    private var lastExternalPackage: String? = null
    private val recentEventTimes = object : LinkedHashMap<String, Long>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 128
    }
    private var lastScrollLocator: ScrollLocator? = null
    private var lastScanMetrics: ScanMetrics? = null
    private var lastPageContentFingerprint: String? = null
    private var scanningAll = false
    private var scrollSteps = 0
    private var staleRounds = 0
    private var previousScrollFingerprint: String? = null
    private var navigation: NavigationAttempt? = null

    private var maxPrice: Double? = null
    private var foodOnly = true
    private var excludePork = false
    private var useMemberPrices = false
    private var advancedMode = false
    private var hapticsEnabled = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        LocalFoodModel.initialize(this)
        val preferences = getSharedPreferences("valuepilot_settings_v101", MODE_PRIVATE)
        maxPrice = preferences.getString("maxPrice", null)?.toDoubleOrNull()?.takeIf { it > 0 }
        foodOnly = preferences.getBoolean("foodOnly", true)
        excludePork = preferences.getBoolean("excludePork", false)
        useMemberPrices = preferences.getBoolean("useMemberPrices", false)
        advancedMode = preferences.getBoolean("advancedMode", false)
        hapticsEnabled = preferences.getBoolean("hapticsEnabled", true)

        overlay = OverlayController(
            service = this,
            onRescan = { requestScan(userInitiated = true, delayMillis = 0) },
            onScanAll = { startScanAll() },
            onOcr = { scanOcr() },
            onClear = {
                productStore.clear()
                publish()
                overlay?.setStatus("Results cleared · watching this page")
            },
            onStop = { finishScanAll(userStopped = true) },
            onMode = {
                mode = it
                publish()
            },
            onItemClick = { openItem(it) },
            initialMaxPrice = maxPrice,
            initialFoodOnly = foodOnly,
            initialExcludePork = excludePork,
            initialUseMemberPrices = useMemberPrices,
            initialAdvancedMode = advancedMode,
            initialHaptics = hapticsEnabled,
            onFilters = { budget, onlyFood, noPork, memberPrices, advanced, haptics ->
                maxPrice = budget
                foodOnly = onlyFood
                excludePork = noPork
                useMemberPrices = memberPrices
                advancedMode = advanced
                hapticsEnabled = haptics
                preferences.edit {
                    putString("maxPrice", budget?.toString())
                    putBoolean("foodOnly", onlyFood)
                    putBoolean("excludePork", noPork)
                    putBoolean("useMemberPrices", memberPrices)
                    putBoolean("advancedMode", advanced)
                    putBoolean("hapticsEnabled", haptics)
                }
                publish()
            }
        ).also { it.show() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        if (packageName != lastExternalPackage) {
            lastExternalPackage = packageName
            sessions.clear()
            productStore.clear()
            contextGeneration++
            lastScrollLocator = null
            navigation = null
            overlay?.setContext(null)
            overlay?.setResults(emptyList())
        }

        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            val source = event.source
            val isSearchInput = SearchContextDetector.looksLikeSearchInput(
                viewId = runCatching { source?.viewIdResourceName }.getOrNull(),
                className = source?.className?.toString(),
                contentDescription = source?.contentDescription?.toString() ?: event.contentDescription?.toString(),
                editable = source?.isEditable == true
            )
            if (isSearchInput) {
                val query = SearchContextDetector.queryFromEvent(event.text, event.contentDescription)
                val transition = sessions.observeExplicitQuery(packageName, query, System.currentTimeMillis())
                if (transition.changed) {
                    contextGeneration++
                    productStore.beginContext(transition.context)
                    overlay?.setContext(transition.context)
                    overlay?.setResults(emptyList())
                    overlay?.setStatus(
                        if (transition.context.query == null) "Search cleared · collecting this page…"
                        else "Searching ${transition.context.displayQuery}…"
                    )
                }
            }
        }

        if (scanningAll || navigation != null || !isRelevantEvent(event)) return
        val now = System.currentTimeMillis()
        val fingerprint = eventFingerprint(event, packageName)
        val previousEventAt = recentEventTimes[fingerprint]
        val repeatWindow = when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> 90L
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> 300L
            else -> if (event?.text.isNullOrEmpty()) 900L else 350L
        }
        if (previousEventAt != null && now - previousEventAt < repeatWindow) return
        recentEventTimes[fingerprint] = now
        val delay = if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) 90L else 140L
        requestScan(userInitiated = false, delayMillis = delay)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scanningAll = false
        navigation = null
        refreshRunnable?.let(handler::removeCallbacks)
        overlay?.hide()
        overlay = null
        workExecutor.shutdownNow()
        screenshotExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun isRelevantEvent(event: AccessibilityEvent?): Boolean = when (event?.eventType) {
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_VIEW_SCROLLED,
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> true
        else -> false
    }

    private fun eventFingerprint(event: AccessibilityEvent?, packageName: String): String {
        val sourceId = runCatching { event?.source?.viewIdResourceName }.getOrNull().orEmpty()
        return StableIds.text(
            listOf(
                packageName,
                event?.eventType,
                event?.windowId,
                event?.contentChangeTypes,
                sourceId,
                event?.text?.joinToString("|"),
                event?.scrollX,
                event?.scrollY
            ).joinToString("|")
        )
    }

    private fun requestScan(userInitiated: Boolean, delayMillis: Long, completion: (() -> Unit)? = null) {
        val dueAt = SystemClock.uptimeMillis() + delayMillis
        if (!userInitiated && refreshRunnable != null && refreshDueAtMillis <= dueAt) return
        refreshRunnable?.let(handler::removeCallbacks)
        if (userInitiated) {
            overlay?.setLoading(true)
            overlay?.setStatus("Refreshing this page…")
        }
        refreshDueAtMillis = dueAt
        refreshRunnable = Runnable {
            refreshRunnable = null
            refreshDueAtMillis = 0L
            scanLoaded(userInitiated, completion)
        }.also { handler.postDelayed(it, delayMillis) }
    }

    private fun scanLoaded(userInitiated: Boolean, completion: (() -> Unit)? = null) {
        if (scanInFlight) {
            rescanRequested = true
            if (completion != null) {
                val prior = deferredScanCompletion
                deferredScanCompletion = {
                    prior?.invoke()
                    completion()
                }
            }
            return
        }
        val root = externalRoot()
        val rootPackage = root?.packageName?.toString()
        if (root == null || rootPackage == packageName) {
            overlay?.setLoading(false)
            if (userInitiated) overlay?.setStatus("Return to the shopping app, then tap Rescan")
            completion?.invoke()
            return
        }

        Trace.beginSection("ValuePilot.snapshot")
        val batch = try {
            NodeScanner.capture(root, rootPackage ?: lastExternalPackage.orEmpty())
        } finally {
            Trace.endSection()
        }
        if (batch == null) {
            overlay?.setLoading(false)
            completion?.invoke()
            return
        }
        lastScanMetrics = batch.metrics
        lastPageContentFingerprint = batch.pageContentFingerprint
        lastScrollLocator = batch.scrollLocator ?: lastScrollLocator

        val previousContext = sessions.current()
        val observation = batch.observation.copy(
            query = batch.observation.query ?: previousContext?.takeIf { it.packageName == batch.packageName }?.query,
            storeIdentity = batch.observation.storeIdentity ?: previousContext?.takeIf { it.packageName == batch.packageName }?.storeIdentity,
            pageHint = batch.observation.pageHint
        )
        val transition = sessions.observe(observation)
        if (transition.changed) {
            contextGeneration++
            productStore.beginContext(transition.context)
            overlay?.setResults(emptyList())
            overlay?.setStatus("${transition.context.displayQuery} · new search")
        } else {
            productStore.beginContext(transition.context)
        }
        overlay?.setContext(transition.context)

        val changedCards = productStore.reserveChanged(batch.cards)
        if (changedCards.isEmpty()) {
            overlay?.setLoading(false)
            updateScanStatus(batch, 0)
            completion?.invoke()
            return
        }

        val generation = contextGeneration
        val context = transition.context
        scanInFlight = true
        workExecutor.execute {
            val failedCards = mutableListOf<ProductCardSnapshot>()
            Trace.beginSection("ValuePilot.parseChangedCards")
            val parsed = try {
                changedCards.mapNotNull { card ->
                    runCatching {
                        val base = productParser.parse(card.rawText, batch.packageName)
                        val item = base?.let { parsedItem ->
                            parsedItem.copy(
                                searchSessionId = context.sessionId,
                                cardFingerprint = card.cardFingerprint,
                                locator = card.toLocator(parsedItem, context)
                            )
                        }
                        ParsedCard(card.cardFingerprint, card.contentFingerprint, item)
                    }.getOrElse {
                        failedCards += card
                        null
                    }
                }
            } finally {
                Trace.endSection()
            }
            handler.post {
                scanInFlight = false
                if (generation != contextGeneration || !sessions.isCurrent(context.sessionId)) {
                    productStore.release(changedCards)
                } else {
                    productStore.release(failedCards)
                    val result = productStore.apply(parsed)
                    if (result.changed) publish()
                    updateScanStatus(batch, result.insertedOrUpdated)
                }
                overlay?.setLoading(false)
                completion?.invoke()
                if (rescanRequested && navigation == null && (!scanningAll || deferredScanCompletion != null)) {
                    rescanRequested = false
                    val deferred = deferredScanCompletion
                    deferredScanCompletion = null
                    requestScan(userInitiated = false, delayMillis = 60, completion = deferred)
                }
            }
        }
    }

    private fun publish() {
        val request = RankingRequest(
            sequence = ++publishSequence,
            context = sessions.current(),
            items = productStore.snapshot(),
            mode = mode,
            maxPrice = maxPrice,
            foodOnly = foodOnly,
            excludePork = excludePork,
            useMemberPrices = useMemberPrices
        )
        val shouldSchedule = synchronized(rankingLock) {
            pendingRanking = request
            if (rankingWorkerScheduled) {
                false
            } else {
                rankingWorkerScheduled = true
                true
            }
        }
        if (shouldSchedule) scheduleRankingWorker()
    }

    private fun scheduleRankingWorker() {
        if (workExecutor.isShutdown) return
        runCatching { workExecutor.execute(::processLatestRanking) }.onFailure {
            synchronized(rankingLock) { rankingWorkerScheduled = false }
        }
    }

    private fun processLatestRanking() {
        val request = synchronized(rankingLock) {
            pendingRanking.also { pendingRanking = null }
        }
        if (request == null) {
            synchronized(rankingLock) { rankingWorkerScheduled = false }
            return
        }
        Trace.beginSection("ValuePilot.rankDiff")
        val ranked = try {
            runCatching {
                val filtered = ValueEngine.filterItems(
                    items = request.items,
                    maxPrice = request.maxPrice,
                    foodOnly = request.foodOnly,
                    excludePork = request.excludePork,
                    query = request.context?.query,
                    useMemberPrice = request.useMemberPrices
                )
                ValueEngine.rank(filtered, request.mode, request.useMemberPrices)
            }
        } finally {
            Trace.endSection()
        }
        handler.post {
            if (request.sequence != publishSequence) return@post
            ranked.onSuccess { results ->
                overlay?.setContext(request.context)
                overlay?.setResults(results)
                if (!scanInFlight) overlay?.setStatus(filterStatus(results.size, request.items.size))
            }.onFailure {
                overlay?.setStatus("Could not refresh rankings · collected products are preserved")
            }
        }
        val shouldContinue = synchronized(rankingLock) {
            if (pendingRanking != null) {
                true
            } else {
                rankingWorkerScheduled = false
                false
            }
        }
        if (shouldContinue) scheduleRankingWorker()
    }

    private fun filterStatus(visible: Int, total: Int): String {
        val filtered = total - visible
        return "$visible shown${if (filtered > 0) " · $filtered filtered" else ""}"
    }

    private fun updateScanStatus(batch: ScanBatch, changed: Int) {
        val stats = productStore.stats()
        val visibleEstimate = stats.itemCount
        val consumer = if (changed > 0) "$changed new or updated · $visibleEstimate collected" else "$visibleEstimate collected · page unchanged"
        val diagnostic = if (advancedMode) {
            " · ${batch.metrics.visitedNodes} nodes / ${ValueEngine.fmt(batch.metrics.captureMillis, 1)} ms" +
                " · ${stats.ignoredUnchangedCards} unchanged ignored${if (batch.metrics.truncated) " · capture bounded" else ""}"
        } else ""
        overlay?.setStatus(consumer + diagnostic)
    }

    private fun externalRoot(): AccessibilityNodeInfo? {
        val externalPackage = lastExternalPackage
        val active = rootInActiveWindow
        val activePackage = active?.packageName?.toString()
        if (active != null && activePackage != packageName && (externalPackage == null || activePackage == externalPackage)) return active
        return windows.asSequence()
            .mapNotNull { it.root }
            .firstOrNull { root ->
                val candidate = root.packageName?.toString()
                candidate != packageName && (externalPackage == null || candidate == externalPackage)
            }
    }

    private fun startScanAll() {
        if (scanningAll || navigation != null) return
        scanningAll = true
        scrollSteps = 0
        staleRounds = 0
        previousScrollFingerprint = null
        overlay?.setScanning(true)
        overlay?.setLoading(true)
        overlay?.setStatus("Collecting newly revealed items…")
        scanLoaded(userInitiated = false) { handler.postDelayed(::scanAllStep, 160) }
    }

    private fun scanAllStep() {
        if (!scanningAll) return
        val root = externalRoot()
        if (root == null) {
            stopScanAll("No active shopping app window")
            return
        }
        if (!NodeScanner.performScroll(root, lastScrollLocator, forward = true)) {
            finishScanAll()
            return
        }
        scrollSteps++
        handler.postDelayed({
            if (!scanningAll) return@postDelayed
            scanLoaded(userInitiated = false) {
                val page = lastPageContentFingerprint
                if (page == previousScrollFingerprint) staleRounds++ else staleRounds = 0
                previousScrollFingerprint = page
                overlay?.setStatus("Collecting · $scrollSteps scrolls · ${productStore.size()} products")
                if (scrollSteps >= 90 || staleRounds >= 6) finishScanAll() else handler.postDelayed(::scanAllStep, 160)
            }
        }, 230)
    }

    private fun finishScanAll(userStopped: Boolean = false) {
        if (!scanningAll) return
        overlay?.setStatus("${if (userStopped) "Collection stopped" else "Collection complete"} · ${productStore.size()} products · returning to start…")
        restorePosition(scrollSteps, userStopped)
    }

    private fun restorePosition(remaining: Int, userStopped: Boolean) {
        if (remaining <= 0 || !scanningAll) {
            stopScanAll("${if (userStopped) "Collection stopped" else "Collection complete"} · ${productStore.size()} products")
            return
        }
        val root = externalRoot()
        if (!NodeScanner.performScroll(root, lastScrollLocator, forward = false)) {
            stopScanAll("${if (userStopped) "Collection stopped" else "Collection complete"} · ${productStore.size()} products")
            return
        }
        handler.postDelayed({ restorePosition(remaining - 1, userStopped) }, 80)
    }

    private fun stopScanAll(message: String) {
        scanningAll = false
        overlay?.setScanning(false)
        overlay?.setLoading(false)
        overlay?.setStatus(message)
        publish()
    }

    private fun openItem(item: ValueItem) {
        val locator = item.locator
        val context = sessions.current()
        if (locator == null || !ItemMatcher.sessionIsCurrent(locator, context, lastExternalPackage)) {
            showNavigationMessage("Could not safely locate this item again. Reopen the same results page and retry.")
            return
        }
        if (navigation != null || scanningAll) {
            showNavigationMessage("ValuePilot is busy collecting items. Stop collection and retry.")
            return
        }
        navigation = NavigationAttempt(locator)
        overlay?.setLoading(true)
        overlay?.setStatus("Finding the exact item…")
        handler.postDelayed(::navigationStep, 140)
    }

    private fun navigationStep() {
        val attempt = navigation ?: return
        val context = sessions.current()
        if (!ItemMatcher.sessionIsCurrent(attempt.locator, context, lastExternalPackage)) {
            stopNavigation("Search or store changed. No item was clicked.")
            return
        }
        val root = externalRoot()
        val rootPackage = root?.packageName?.toString()
        if (root == null || rootPackage != attempt.locator.packageName) {
            stopNavigation("Return to the same shopping results page and retry.")
            return
        }
        val batch = NodeScanner.capture(root, rootPackage) ?: run {
            stopNavigation("Could not read the current product list. No item was clicked.")
            return
        }
        val observedQuery = SearchContextDetector.normalizeQuery(batch.observation.query)
        if (context?.query != null && observedQuery == null) {
            stopNavigation("Could not verify the current search. No item was clicked.")
            return
        }
        if (observedQuery != null && SearchContextDetector.fingerprint(observedQuery) != context?.queryFingerprint) {
            stopNavigation("Search changed. No item was clicked.")
            return
        }
        val observedStore = SearchContextDetector.cleanStore(batch.observation.storeIdentity)
        if (observedStore != null && context?.storeIdentity != null &&
            SearchContextDetector.fingerprint(observedStore) != SearchContextDetector.fingerprint(context.storeIdentity)
        ) {
            stopNavigation("Store changed. No item was clicked.")
            return
        }

        val candidates = batch.cards.mapNotNull { card ->
            val parsed = productParser.parse(card.rawText, rootPackage) ?: return@mapNotNull null
            ItemMatchCandidate(
                name = parsed.name,
                currentPrice = parsed.offer.currentPrice,
                memberPrice = parsed.offer.memberPrice,
                quantityKind = parsed.quantity?.kind,
                quantityAmount = parsed.quantity?.amountBase,
                viewId = card.locatorSeed.viewId,
                cardFingerprint = card.cardFingerprint,
                clickPath = card.locatorSeed.clickPath
            )
        }
        val decision = ItemMatcher.choose(attempt.locator, candidates)
        if (decision.ambiguous) {
            stopNavigation("Several items looked too similar. ValuePilot did not click any of them.")
            return
        }
        val matched = decision.candidateIndex?.let(candidates::getOrNull)
        if (matched != null) {
            if (NodeScanner.performClick(root, matched.clickPath)) {
                navigation = null
                overlay?.setLoading(false)
                overlay?.notifyItemOpened()
                Toast.makeText(this, "Opened exact ValuePilot item", Toast.LENGTH_SHORT).show()
            } else {
                stopNavigation("The exact item was found but could not be opened safely.")
            }
            return
        }

        val fingerprint = batch.pageContentFingerprint
        if (fingerprint == attempt.previousPageFingerprint) attempt.staleRounds++ else attempt.staleRounds = 0
        attempt.previousPageFingerprint = fingerprint
        val phaseExhausted = attempt.phaseSteps >= 90 || attempt.staleRounds >= 5
        if (!phaseExhausted && NodeScanner.performScroll(root, batch.scrollLocator, forward = attempt.forward)) {
            attempt.phaseSteps++
            attempt.totalSteps++
            overlay?.setStatus("Finding exact item · ${if (attempt.forward) "searching below" else "searching above"} · ${attempt.totalSteps} scrolls")
            handler.postDelayed(::navigationStep, 230)
            return
        }
        if (attempt.forward) {
            attempt.forward = false
            attempt.phaseSteps = 0
            attempt.staleRounds = 0
            attempt.previousPageFingerprint = null
            if (NodeScanner.performScroll(root, batch.scrollLocator, forward = false)) {
                attempt.phaseSteps++
                attempt.totalSteps++
                overlay?.setStatus("Finding exact item · searching above · ${attempt.totalSteps} scrolls")
                handler.postDelayed(::navigationStep, 230)
                return
            }
        }
        stopNavigation("Could not safely locate this item again. No item was clicked.")
    }

    private fun stopNavigation(message: String) {
        navigation = null
        overlay?.setLoading(false)
        showNavigationMessage(message)
    }

    private fun showNavigationMessage(message: String) {
        overlay?.setStatus(message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun scanOcr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            overlay?.setStatus("On-device OCR needs Android 11+")
            return
        }
        overlay?.setLoading(true)
        overlay?.setStatus("Reading the visible screen on this device…")
        overlay?.setOverlayVisible(false)
        handler.postDelayed({
            val callback = object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer: HardwareBuffer = screenshot.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()
                    if (bitmap == null) {
                        handler.post {
                            overlay?.setOverlayVisible(true)
                            overlay?.setLoading(false)
                            overlay?.setStatus("Could not decode the screenshot")
                        }
                        return
                    }
                    OcrScanner.scan(bitmap) { observations, error ->
                        bitmap.recycle()
                        handler.post {
                            overlay?.setOverlayVisible(true)
                            overlay?.setLoading(false)
                            if (error != null) {
                                overlay?.setStatus("OCR failed: ${error.message ?: "unknown error"}")
                            } else {
                                val items = ValueEngine.dedupe(observations.mapNotNull { productParser.parse(it, lastExternalPackage) })
                                ingestOcr(items)
                            }
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    handler.post {
                        overlay?.setOverlayVisible(true)
                        overlay?.setLoading(false)
                        overlay?.setStatus("This app blocks screenshots. Using Accessibility results only.")
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 34) {
                val windowId = externalRoot()?.windowId
                if (windowId != null) takeScreenshotOfWindow(windowId, screenshotExecutor, callback)
                else takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, callback)
            } else {
                takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, callback)
            }
        }, 120)
    }

    private fun ingestOcr(items: List<ValueItem>) {
        val context = sessions.current() ?: lastExternalPackage?.let { packageName ->
            sessions.observe(
                ContextObservation(
                    packageName = packageName,
                    platform = SearchContextDetector.platformFor(packageName),
                    query = null,
                    storeIdentity = null,
                    pageHint = "ocr",
                    observedAtMillis = System.currentTimeMillis()
                )
            ).context.also(productStore::beginContext)
        } ?: return
        val cards = items.map { item ->
            val fingerprint = StableIds.text("ocr|${item.rawText}")
            ProductCardSnapshot(
                cardFingerprint = fingerprint,
                contentFingerprint = fingerprint,
                rawText = item.rawText,
                locatorSeed = LocatorSeed(-1, null, null, ScreenBounds(0, 0, 0, 0), NodePath(), null),
                capturedAtMillis = System.currentTimeMillis()
            )
        }
        val changed = productStore.reserveChanged(cards)
        val byFingerprint = cards.zip(items).associate { it.first.cardFingerprint to it.second }
        val parsed = changed.map { card ->
            val item = byFingerprint[card.cardFingerprint]?.copy(
                searchSessionId = context.sessionId,
                cardFingerprint = card.cardFingerprint,
                locator = null
            )
            ParsedCard(card.cardFingerprint, card.contentFingerprint, item)
        }
        productStore.apply(parsed)
        publish()
        overlay?.setStatus("OCR added ${parsed.size} visible candidates · ${productStore.size()} collected")
    }
}
