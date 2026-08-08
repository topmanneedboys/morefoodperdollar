package com.valuepilot.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

class OverlayController(
    private val service: AccessibilityService,
    private val onScanLoaded: () -> Unit,
    private val onScanAll: () -> Unit,
    private val onOcr: () -> Unit,
    private val onClear: () -> Unit,
    private val onStop: () -> Unit,
    private val onMode: (RankMode) -> Unit,
    initialMaxPrice: Double?,
    initialFoodOnly: Boolean,
    initialExcludePork: Boolean,
    private val onFilters: (Double?, Boolean, Boolean) -> Unit
) {
    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var listContainer: LinearLayout? = null
    private var status: TextView? = null
    private var countBadge: TextView? = null
    private var scanning = false
    private var lastResults: List<RankedItem> = emptyList()
    private var lastStatus = "Ready"
    private var selectedMode = RankMode.SMART
    private var maxPrice = initialMaxPrice
    private var foodOnly = initialFoodOnly
    private var excludePork = initialExcludePork

    private val rankModes = listOf(
        RankMode.SMART, RankMode.MASS, RankMode.VOLUME, RankMode.CALORIE,
        RankMode.PIZZA, RankMode.UNIT, RankMode.PORTION, RankMode.MEAT
    )

    fun show() {
        if (bubble != null) return
        val holder = FrameLayout(service)
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
            text = "0"
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(0xff111827.toInt())
            setBackgroundResource(R.drawable.vp_badge)
            contentDescription = "0 matching items"
        }
        holder.addView(button, FrameLayout.LayoutParams(dp(58), dp(58)))
        holder.addView(badge, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.END or Gravity.TOP))
        countBadge = badge

        val params = WindowManager.LayoutParams(
            dp(64), dp(64), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.BOTTOM; x = dp(12); y = dp(72) }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        holder.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) moved = true
                    params.x = (startX - dx.toInt()).coerceAtLeast(0)
                    params.y = (startY - dy.toInt()).coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(holder, params) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) togglePanel(); true }
                else -> false
            }
        }
        bubble = holder
        wm.addView(holder, params)
    }

    fun hide() {
        panel?.let { runCatching { wm.removeView(it) } }
        bubble?.let { runCatching { wm.removeView(it) } }
        panel = null
        bubble = null
        listContainer = null
        status = null
    }

    fun setResults(ranked: List<RankedItem>) {
        lastResults = ranked
        countBadge?.apply {
            text = ranked.size.coerceAtMost(999).toString()
            contentDescription = "${ranked.size} matching items"
        }
        listContainer?.let { renderRows(it, ranked) }
    }

    fun setStatus(text: String) {
        lastStatus = text
        status?.text = text
    }

    fun setOverlayVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.INVISIBLE
        bubble?.visibility = visibility
        panel?.visibility = visibility
    }

    fun setScanning(value: Boolean) {
        scanning = value
        panel?.findViewWithTag<Button>("scanAll")?.text = if (value) "Stop" else "Scan all"
    }

    fun isPanelOpen(): Boolean = panel != null

    private fun togglePanel() {
        if (panel != null) { closePanel(); return }
        val outer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(10))
            setBackgroundResource(R.drawable.vp_panel)
        }
        val titleRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(service).apply {
            text = "ValuePilot"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xff111827.toInt())
        }
        val badge = TextView(service).apply {
            text = "private local AI · v101"
            textSize = 9f
            setTextColor(0xff6b7280.toInt())
            setPadding(dp(5), 0, dp(5), 0)
        }
        val clear = Button(service).apply { text = "Clear"; textSize = 10f; setOnClickListener { onClear() } }
        titleRow.addView(title, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(badge)
        titleRow.addView(clear, LinearLayout.LayoutParams(dp(66), dp(40)))
        outer.addView(titleRow)

        val controlsScroller = HorizontalScrollView(service).apply { isHorizontalScrollBarEnabled = false }
        val controls = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val spinner = Spinner(service).apply {
            adapter = ArrayAdapter(
                service,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Smart", "$/kg", "$/L", "Calories/$", "Pizza area/$", "$/unit", "AI food/$", "AI meat/$")
            )
            setSelection(rankModes.indexOf(selectedMode).coerceAtLeast(0), false)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedMode = rankModes[position]
                    onMode(selectedMode)
                }
            }
        }
        val scan = Button(service).apply { text = "Loaded"; setOnClickListener { onScanLoaded() } }
        val all = Button(service).apply {
            text = if (scanning) "Stop" else "Scan all"
            tag = "scanAll"
            setOnClickListener { if (scanning) onStop() else onScanAll() }
        }
        val ocr = Button(service).apply { text = "OCR"; setOnClickListener { onOcr() } }
        controls.addView(spinner, LinearLayout.LayoutParams(dp(150), dp(48)))
        controls.addView(scan, LinearLayout.LayoutParams(dp(76), dp(48)))
        controls.addView(all, LinearLayout.LayoutParams(dp(86), dp(48)))
        controls.addView(ocr, LinearLayout.LayoutParams(dp(64), dp(48)))
        controlsScroller.addView(controls)
        outer.addView(controlsScroller)

        val filters = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val budget = EditText(service).apply {
            hint = "$ budget"
            contentDescription = "Maximum total spend"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            textSize = 12f
            maxPrice?.let { setText(ValueEngine.fmt(it, 2)) }
        }
        val food = CheckBox(service).apply { text = "Food only"; textSize = 11f; isChecked = foodOnly }
        val pork = CheckBox(service).apply { text = "No pork"; textSize = 11f; isChecked = excludePork }
        filters.addView(budget, LinearLayout.LayoutParams(dp(108), dp(48)))
        filters.addView(food, LinearLayout.LayoutParams(0, dp(48), 1f))
        filters.addView(pork, LinearLayout.LayoutParams(0, dp(48), 1f))
        outer.addView(filters)

        budget.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                maxPrice = s?.toString()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }
                onFilters(maxPrice, foodOnly, excludePork)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        food.setOnCheckedChangeListener { _, checked ->
            foodOnly = checked
            onFilters(maxPrice, foodOnly, excludePork)
        }
        pork.setOnCheckedChangeListener { _, checked ->
            excludePork = checked
            onFilters(maxPrice, foodOnly, excludePork)
        }

        status = TextView(service).apply {
            text = lastStatus
            textSize = 11f
            setTextColor(0xff6b7280.toInt())
            setPadding(0, dp(4), 0, dp(5))
            contentDescription = "ValuePilot status"
        }
        outer.addView(status)

        val scroll = ScrollView(service)
        val rows = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        listContainer = rows
        renderRows(rows, lastResults)
        scroll.addView(rows)
        outer.addView(scroll, LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, 0, 1f))

        val foot = TextView(service).apply {
            text = "Exact measurements always beat AI estimates. AI food/meat scores and OCR stay on this device. Scan all scrolls only after you ask and then returns toward the start."
            textSize = 10f
            setTextColor(0xff6b7280.toInt())
            setPadding(0, dp(6), 0, 0)
        }
        outer.addView(foot)

        val params = WindowManager.LayoutParams(
            minOf(dp(430), service.resources.displayMetrics.widthPixels - dp(20)),
            minOf(dp(680), service.resources.displayMetrics.heightPixels - dp(130)),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.BOTTOM; x = dp(10); y = dp(145) }
        panel = outer
        wm.addView(outer, params)
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        listContainer = null
        status = null
    }

    private fun renderRows(container: LinearLayout, ranked: List<RankedItem>) {
        container.removeAllViews()
        if (ranked.isEmpty()) {
            container.addView(TextView(service).apply {
                text = "No matching product cards found yet. Open a store or menu, adjust filters if needed, and tap Scan all."
                textSize = 12f
                setTextColor(0xff6b7280.toInt())
                setPadding(4, dp(18), 4, dp(18))
            })
            return
        }
        ranked.take(60).forEach { rankedItem ->
            val item = rankedItem.item
            val box = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setBackgroundResource(if (rankedItem.rank == 1 && rankedItem.metricLabel != "price only") R.drawable.vp_row_best else R.drawable.vp_row)
            }
            val name = TextView(service).apply {
                text = "${rankedItem.rank}. ${item.name}"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xff111827.toInt())
            }
            val metric = TextView(service).apply {
                text = buildString {
                    append(rankedItem.metricLabel)
                    if (item.promotion.type != "none") append(" · ${item.promotion.label}")
                }
                textSize = 12f
                setTextColor(0xff111827.toInt())
            }
            val meta = TextView(service).apply {
                text = buildString {
                    append(ValueEngine.money(item.price, item.currency))
                    val spend = ValueEngine.minimumSpend(item)
                    if (spend > item.price + .005) append(" · deal spend ${ValueEngine.money(spend, item.currency)}")
                    item.quantity?.let { append(" · ${it.display}") }
                    item.calories?.let { append(" · ${it.toInt()} cal") }
                    append(" · parse ${(item.confidence * 100).toInt()}%")
                    if (item.ai.confidence >= .26) append(" · local AI: ${item.ai.label} ${(item.ai.confidence * 100).toInt()}%")
                }
                textSize = 10f
                setTextColor(0xff6b7280.toInt())
            }
            box.addView(name)
            box.addView(metric)
            box.addView(meta)
            container.addView(
                box,
                LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(3), 0, dp(3))
                }
            )
        }
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
}
