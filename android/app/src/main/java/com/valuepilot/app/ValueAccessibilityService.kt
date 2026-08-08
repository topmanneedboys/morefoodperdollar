package com.valuepilot.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

class ValueAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val allItems = linkedMapOf<String, ValueItem>()
    private var overlay: OverlayController? = null
    private var mode = RankMode.SMART
    private var scanningAll = false
    private var refreshRunnable: Runnable? = null
    private var scrollSteps = 0
    private var staleRounds = 0
    private var previousCount = 0
    private var sourcePackage: String? = null
    private var lastExternalPackage: String? = null
    private var maxPrice: Double? = null
    private var foodOnly = true
    private var excludePork = false
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        LocalFoodModel.initialize(this)
        val preferences = getSharedPreferences("valuepilot_settings_v101", MODE_PRIVATE)
        maxPrice = preferences.getString("maxPrice", null)?.toDoubleOrNull()?.takeIf { it > 0 }
        foodOnly = preferences.getBoolean("foodOnly", true)
        excludePork = preferences.getBoolean("excludePork", false)
        overlay = OverlayController(
            this,
            onScanLoaded = { scanLoaded(true) },
            onScanAll = { startScanAll() },
            onOcr = { scanOcr() },
            onClear = { allItems.clear(); publish(); overlay?.setStatus("Cleared") },
            onStop = { finishScanAll(userStopped = true) },
            onMode = { mode = it; publish() },
            initialMaxPrice = maxPrice,
            initialFoodOnly = foodOnly,
            initialExcludePork = excludePork,
            onFilters = { budget, onlyFood, noPork ->
                maxPrice = budget
                foodOnly = onlyFood
                excludePork = noPork
                preferences.edit()
                    .putString("maxPrice", budget?.toString())
                    .putBoolean("foodOnly", onlyFood)
                    .putBoolean("excludePork", noPork)
                    .apply()
                publish()
                overlay?.setStatus(filterStatus())
            }
        ).also { it.show() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString()
        if (pkg == packageName) return
        if (overlay?.isPanelOpen() == true && pkg != lastExternalPackage) return
        if (!pkg.isNullOrBlank() && pkg != packageName && pkg != lastExternalPackage) {
            if (lastExternalPackage != null) allItems.clear()
            lastExternalPackage = pkg
        }
        sourcePackage = lastExternalPackage ?: pkg
        if (scanningAll) return
        refreshRunnable?.let(handler::removeCallbacks)
        refreshRunnable = Runnable { scanLoaded(false) }.also { handler.postDelayed(it, 500) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scanningAll = false
        refreshRunnable?.let(handler::removeCallbacks)
        overlay?.hide(); overlay = null
        screenshotExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun scanLoaded(userInitiated: Boolean) {
        val root = externalRoot()
        val rootPackage = root?.packageName?.toString()
        if (root == null || rootPackage == packageName) {
            if (userInitiated) overlay?.setStatus("Return to the store or menu app, then scan again")
            return
        }
        val result = NodeScanner.scan(root, sourcePackage ?: rootPackage)
        ingest(result.items)
        publish()
        if (userInitiated) overlay?.setStatus("Loaded scan · ${result.items.size} visible candidates · ${visibleItems().size}/${allItems.size} shown")
    }

    private fun ingest(items: Collection<ValueItem>) {
        for (item in items) {
            val q = item.quantity
            val qKey = q?.amountBase?.toInt() ?: 0
            val key = "${ValueEngine.canonicalName(item.name)}|${"%.2f".format(java.util.Locale.US,item.price)}|${q?.kind}|$qKey|${item.promotion.type}"
            val old = allItems[key]
            if (old == null || item.confidence > old.confidence || item.rawText.length > old.rawText.length) allItems[key] = item
        }
        if (allItems.size > 500) {
            val trimmed = allItems.entries.toList().takeLast(500).associate { it.toPair() }
            allItems.clear(); allItems.putAll(trimmed)
        }
    }

    private fun publish() {
        overlay?.setResults(ValueEngine.rank(visibleItems(), mode))
    }

    private fun visibleItems(): List<ValueItem> = ValueEngine.filterItems(
        allItems.values,
        maxPrice = maxPrice,
        foodOnly = foodOnly,
        excludePork = excludePork
    )

    private fun filterStatus(): String {
        val visible = visibleItems().size
        val filtered = allItems.size - visible
        return "$visible shown${if (filtered > 0) " · $filtered filtered" else ""}"
    }

    private fun externalRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        val activePackage = active?.packageName?.toString()
        if (active != null && activePackage != packageName && (lastExternalPackage == null || activePackage == lastExternalPackage)) return active
        return windows.asSequence()
            .mapNotNull { it.root }
            .firstOrNull { root ->
                val candidate = root.packageName?.toString()
                candidate != packageName && (lastExternalPackage == null || candidate == lastExternalPackage)
            }
    }

    private fun startScanAll() {
        if (scanningAll) return
        scanningAll = true; scrollSteps = 0; staleRounds = 0; previousCount = allItems.size
        overlay?.setScanning(true); overlay?.setStatus("Scanning lazy/off-screen content…")
        scanLoaded(false)
        handler.postDelayed(::scanAllStep, 200)
    }

    private fun scanAllStep() {
        if (!scanningAll) return
        val root = externalRoot()
        if (root == null) { stopScanAll("No active app window"); return }
        val scrollable = NodeScanner.findBestScrollable(root)
        if (scrollable == null) { finishScanAll(); return }
        val acted = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        if (!acted) { finishScanAll(); return }
        scrollSteps++
        handler.postDelayed({
            scanLoaded(false)
            if (allItems.size == previousCount) staleRounds++ else staleRounds = 0
            previousCount = allItems.size
            overlay?.setStatus("Scanning · step $scrollSteps · ${allItems.size} items")
            if (!scanningAll) return@postDelayed
            if (scrollSteps >= 90 || staleRounds >= 5) finishScanAll() else handler.postDelayed(::scanAllStep, 220)
        }, 260)
    }

    private fun finishScanAll(userStopped: Boolean = false) {
        if (!scanningAll) return
        overlay?.setStatus("${if (userStopped) "Scan stopped" else "Scan complete"} · ${allItems.size} items · returning toward start…")
        restorePosition(scrollSteps, userStopped)
    }

    private fun restorePosition(remaining: Int, userStopped: Boolean = false) {
        if (remaining <= 0 || !scanningAll) {
            stopScanAll("${if (userStopped) "Scan stopped" else "Scan complete"} · ${visibleItems().size}/${allItems.size} shown")
            return
        }
        val root = externalRoot()
        val scrollable = NodeScanner.findBestScrollable(root)
        if (scrollable == null || !scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            stopScanAll("${if (userStopped) "Scan stopped" else "Scan complete"} · ${visibleItems().size}/${allItems.size} shown")
            return
        }
        handler.postDelayed({ restorePosition(remaining - 1, userStopped) }, 90)
    }

    private fun stopScanAll(message: String) {
        scanningAll = false
        overlay?.setScanning(false); overlay?.setStatus(message); publish()
    }

    private fun scanOcr() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            overlay?.setStatus("OCR screenshot fallback needs Android 11+"); return
        }
        overlay?.setStatus("Reading visible screen with on-device OCR…")
        overlay?.setOverlayVisible(false)
        handler.postDelayed({
            val callback = object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val buffer: HardwareBuffer = screenshot.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    buffer.close()
                    if (bitmap == null) {
                        handler.post { overlay?.setOverlayVisible(true); overlay?.setStatus("Could not decode screenshot") }
                        return
                    }
                    OcrScanner.scan(bitmap, lastExternalPackage ?: sourcePackage) { items, error ->
                        bitmap.recycle()
                        handler.post {
                            overlay?.setOverlayVisible(true)
                            if (error != null) overlay?.setStatus("OCR failed: ${error.message ?: "unknown error"}")
                            else { ingest(items); publish(); overlay?.setStatus("OCR scan · ${items.size} candidates · ${visibleItems().size}/${allItems.size} shown") }
                        }
                    }
                }
                override fun onFailure(errorCode: Int) {
                    handler.post { overlay?.setOverlayVisible(true); overlay?.setStatus("Screenshot unavailable in this app (code $errorCode)") }
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
}
