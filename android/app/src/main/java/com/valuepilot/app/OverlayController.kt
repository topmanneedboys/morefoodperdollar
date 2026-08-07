package com.valuepilot.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*

class OverlayController(
    private val service: AccessibilityService,
    private val onScanLoaded: () -> Unit,
    private val onScanAll: () -> Unit,
    private val onOcr: () -> Unit,
    private val onClear: () -> Unit,
    private val onStop: () -> Unit,
    private val onMode: (RankMode) -> Unit
) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var listContainer: LinearLayout? = null
    private var status: TextView? = null
    private var countBadge: TextView? = null
    private var scanning = false

    fun show() {
        if (bubble != null) return
        val b = FrameLayout(service)
        val button = TextView(service).apply {
            text = "VP"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xffffffff.toInt())
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(R.drawable.vp_bubble)
            contentDescription = "Open ValuePilot"
        }
        val badge = TextView(service).apply {
            text = "0"; textSize = 9f; gravity = Gravity.CENTER
            setTextColor(0xff111827.toInt()); setBackgroundResource(R.drawable.vp_badge)
        }
        b.addView(button, FrameLayout.LayoutParams(dp(58), dp(58)))
        b.addView(badge, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.END or Gravity.TOP))
        countBadge = badge

        val lp = WindowManager.LayoutParams(
            dp(64), dp(64), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.BOTTOM; x = dp(12); y = dp(72) }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        b.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) moved = true
                    lp.x = (startX - dx.toInt()).coerceAtLeast(0); lp.y = (startY - dy.toInt()).coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(b, lp) }; true
                }
                MotionEvent.ACTION_UP -> { if (!moved) togglePanel(); true }
                else -> false
            }
        }
        bubble = b
        wm.addView(b, lp)
    }

    fun hide() {
        panel?.let { runCatching { wm.removeView(it) } }; panel = null
        bubble?.let { runCatching { wm.removeView(it) } }; bubble = null
    }

    fun setResults(ranked: List<RankedItem>) {
        countBadge?.text = ranked.size.coerceAtMost(999).toString()
        listContainer?.let { renderRows(it, ranked) }
    }

    fun setStatus(text: String) { status?.text = text }

    fun setOverlayVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.INVISIBLE
        bubble?.visibility = v
        panel?.visibility = v
    }

    fun setScanning(value: Boolean) {
        scanning = value
        panel?.findViewWithTag<Button>("scanAll")?.text = if (value) "Stop" else "Scan all"
    }

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        val outer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(10))
            setBackgroundResource(R.drawable.vp_panel)
        }
        val titleRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(service).apply { text = "ValuePilot"; textSize = 18f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xff111827.toInt()) }
        val clear = Button(service).apply { text = "Clear"; textSize = 10f; setOnClickListener { onClear() } }
        titleRow.addView(title, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(clear, LinearLayout.LayoutParams(dp(66), dp(40)))
        outer.addView(titleRow)

        val controls = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val spinner = Spinner(service).apply {
            adapter = ArrayAdapter(service, android.R.layout.simple_spinner_dropdown_item, listOf("Smart", "$/kg", "$/L", "Calories/$", "Pizza area/$", "$/unit", "Est. food/$"))
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    onMode(listOf(RankMode.SMART, RankMode.MASS, RankMode.VOLUME, RankMode.CALORIE, RankMode.PIZZA, RankMode.UNIT, RankMode.PORTION)[position])
                }
            }
        }
        val scan = Button(service).apply { text = "Loaded"; setOnClickListener { onScanLoaded() } }
        val all = Button(service).apply { text = if (scanning) "Stop" else "Scan all"; tag = "scanAll"; setOnClickListener { if (scanning) onStop() else onScanAll() } }
        val ocr = Button(service).apply { text = "OCR"; setOnClickListener { onOcr() } }
        controls.addView(spinner, LinearLayout.LayoutParams(0, dp(48), 1f))
        controls.addView(scan, LinearLayout.LayoutParams(dp(72), dp(48)))
        controls.addView(all, LinearLayout.LayoutParams(dp(82), dp(48)))
        controls.addView(ocr, LinearLayout.LayoutParams(dp(60), dp(48)))
        outer.addView(controls)

        status = TextView(service).apply { text = "Ready"; textSize = 11f; setTextColor(0xff6b7280.toInt()); setPadding(0, dp(4), 0, dp(5)) }
        outer.addView(status)

        val scroll = ScrollView(service)
        val rows = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        listContainer = rows
        scroll.addView(rows)
        outer.addView(scroll, LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, 0, 1f))

        val foot = TextView(service).apply {
            text = "Direct measurements beat estimates. Scan all scrolls lazy lists and then returns toward the original position."
            textSize = 10f; setTextColor(0xff6b7280.toInt()); setPadding(0, dp(6), 0, 0)
        }
        outer.addView(foot)

        val lp = WindowManager.LayoutParams(
            minOf(dp(430), service.resources.displayMetrics.widthPixels - dp(20)),
            minOf(dp(650), service.resources.displayMetrics.heightPixels - dp(130)),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.BOTTOM; x = dp(10); y = dp(145) }
        panel = outer
        wm.addView(outer, lp)
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null; listContainer = null; status = null
    }

    private fun renderRows(container: LinearLayout, ranked: List<RankedItem>) {
        container.removeAllViews()
        if (ranked.isEmpty()) {
            container.addView(TextView(service).apply { text = "No product cards found yet. Open a store/menu and tap Scan all."; textSize = 12f; setTextColor(0xff6b7280.toInt()); setPadding(4, dp(18), 4, dp(18)) })
            return
        }
        ranked.take(60).forEach { r ->
            val box = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)); setBackgroundResource(if (r.rank == 1 && r.metricLabel != "price only") R.drawable.vp_row_best else R.drawable.vp_row) }
            val name = TextView(service).apply { text = "${r.rank}. ${r.item.name}"; textSize = 13f; setTypeface(typeface, Typeface.BOLD); setTextColor(0xff111827.toInt()) }
            val metric = TextView(service).apply { text = buildString { append(r.metricLabel); if (r.item.promotion.type != "none") append(" · ${r.item.promotion.label}") }; textSize = 12f; setTextColor(0xff111827.toInt()) }
            val meta = TextView(service).apply {
                text = buildString {
                    append(ValueEngine.money(r.item.price, r.item.currency))
                    r.item.quantity?.let { append(" · ${it.display}") }
                    r.item.calories?.let { append(" · ${it.toInt()} cal") }
                    append(" · ${(r.item.confidence * 100).toInt()}%")
                }
                textSize = 10f; setTextColor(0xff6b7280.toInt())
            }
            box.addView(name); box.addView(metric); box.addView(meta)
            container.addView(box, LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(3), 0, dp(3)) })
        }
    }

    private fun dp(v: Int) = (v * service.resources.displayMetrics.density).toInt()
}
