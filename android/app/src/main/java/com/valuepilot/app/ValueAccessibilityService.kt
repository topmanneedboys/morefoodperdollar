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
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(
            this,
            onScanLoaded = { scanLoaded(true) },
            onScanAll = { startScanAll() },
            onOcr = { scanOcr() },
            onClear = { allItems.clear(); publish(); overlay?.setStatus("Cleared") },
            onStop = { finishScanAll() },
            onMode = { mode = it; publish() }
        ).also { it.show() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        sourcePackage = event?.packageName?.toString() ?: rootInActiveWindow?.packageName?.toString()
        val pkg = sourcePackage
        if (!pkg.isNullOrBlank() && pkg != packageName && pkg != lastExternalPackage) {
            if (lastExternalPackage != null) allItems.clear()
            lastExternalPackage = pkg
        }
        if (scanningAll) return
        refreshRunnable?.let(handler::removeCallbacks)
        refreshRunnable = Runnable { scanLoaded(false) }.also { handler.postDelayed(it, 500) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scanningAll = false
        overlay?.hide(); overlay = null
        screenshotExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun scanLoaded(userInitiated: Boolean) {
        val root = rootInActiveWindow
        val result = NodeScanner.scan(root, sourcePackage ?: root?.packageName?.toString())
        ingest(result.items)
        publish()
        if (userInitiated) overlay?.setStatus("Loaded scan · ${result.items.size} visible candidates · ${allItems.size} collected")
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
        overlay?.setResults(ValueEngine.rank(allItems.values, mode))
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
        val root = rootInActiveWindow
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

    private fun finishScanAll() {
        if (!scanningAll) return
        overlay?.setStatus("Scan complete · ${allItems.size} items · returning toward start…")
        restorePosition(scrollSteps)
    }

    private fun restorePosition(remaining: Int) {
        if (remaining <= 0 || !scanningAll) {
            stopScanAll("Scan complete · ${allItems.size} items")
            return
        }
        val root = rootInActiveWindow
        val scrollable = NodeScanner.findBestScrollable(root)
        if (scrollable == null || !scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            stopScanAll("Scan complete · ${allItems.size} items")
            return
        }
        handler.postDelayed({ restorePosition(remaining - 1) }, 90)
    }

    private fun stopScanAll(message: String) {
        scanningAll = false
        handler.removeCallbacksAndMessages(null)
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
                    OcrScanner.scan(bitmap, sourcePackage) { items, error ->
                        bitmap.recycle()
                        handler.post {
                            overlay?.setOverlayVisible(true)
                            if (error != null) overlay?.setStatus("OCR failed: ${error.message ?: "unknown error"}")
                            else { ingest(items); publish(); overlay?.setStatus("OCR scan · ${items.size} candidates · ${allItems.size} collected") }
                        }
                    }
                }
                override fun onFailure(errorCode: Int) {
                    handler.post { overlay?.setOverlayVisible(true); overlay?.setStatus("Screenshot unavailable in this app (code $errorCode)") }
                }
            }
            if (Build.VERSION.SDK_INT >= 34) {
                val windowId = rootInActiveWindow?.windowId
                if (windowId != null) takeScreenshotOfWindow(windowId, screenshotExecutor, callback)
                else takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, callback)
            } else {
                takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, callback)
            }
        }, 120)
    }
}
