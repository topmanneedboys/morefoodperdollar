package com.valuepilot.app

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

@SuppressLint("SetTextI18n")
class OverlayController(
    private val service: AccessibilityService,
    private val onRescan: () -> Unit,
    private val onScanAll: () -> Unit,
    private val onOcr: () -> Unit,
    private val onClear: () -> Unit,
    private val onStop: () -> Unit,
    private val onMode: (RankMode) -> Unit,
    private val onItemClick: (ValueItem) -> Unit,
    initialMaxPrice: Double?,
    initialFoodOnly: Boolean,
    initialExcludePork: Boolean,
    initialUseMemberPrices: Boolean,
    initialAdvancedMode: Boolean,
    initialHaptics: Boolean,
    private val onFilters: (Double?, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    private class AccessibleFrameLayout(context: Context) : FrameLayout(context) {
        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }

    private val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val animationsEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()
    private val resultAdapter = ResultAdapter(service) { item ->
        haptic(success = false)
        minimizeSheet()
        onItemClick(item)
    }

    private var bubble: View? = null
    private var bubbleBadge: TextView? = null
    private var sheet: View? = null
    private var sheetParams: WindowManager.LayoutParams? = null
    private var contentFrame: FrameLayout? = null
    private var mainContent: View? = null
    private var contextLabel: TextView? = null
    private var statusLabel: TextView? = null
    private var progress: ProgressBar? = null
    private var emptyState: TextView? = null
    private var recycler: RecyclerView? = null
    private var rankButton: Button? = null
    private var scanAllButton: Button? = null
    private var lastResults: List<RankedItem> = emptyList()
    private var lastContext: SearchContext? = null
    private var lastStatus = "Watching this page"
    private var loading = false
    private var scanningAll = false
    private var selectedMode = RankMode.SMART
    private var maxPrice = initialMaxPrice
    private var foodOnly = initialFoodOnly
    private var excludePork = initialExcludePork
    private var useMemberPrices = initialUseMemberPrices
    private var advancedMode = initialAdvancedMode
    private var hapticsEnabled = initialHaptics

    fun show() {
        if (bubble != null) return
        val holder = AccessibleFrameLayout(service)
        holder.contentDescription = "Open ValuePilot"
        holder.setOnClickListener { openSheet() }
        val button = TextView(service).apply {
            text = "VP"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xffffffff.toInt())
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(R.drawable.vp_bubble)
            contentDescription = "Open ValuePilot"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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
        bubbleBadge = badge

        val params = WindowManager.LayoutParams(
            dp(64),
            dp(64),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            x = dp(12)
            y = dp(72)
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        holder.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
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
                MotionEvent.ACTION_UP -> {
                    if (!moved) holder.performClick()
                    true
                }
                else -> false
            }
        }
        bubble = holder
        wm.addView(holder, params)
    }

    fun hide() {
        sheet?.let { runCatching { wm.removeView(it) } }
        bubble?.let { runCatching { wm.removeView(it) } }
        sheet = null
        sheetParams = null
        bubble = null
        bubbleBadge = null
        clearSheetReferences()
    }

    fun setResults(ranked: List<RankedItem>) {
        lastResults = ranked
        bubbleBadge?.apply {
            text = ranked.size.coerceAtMost(999).toString()
            contentDescription = "${ranked.size} matching items"
        }
        resultAdapter.submitList(ranked)
        emptyState?.visibility = if (ranked.isEmpty()) View.VISIBLE else View.GONE
        recycler?.visibility = if (ranked.isEmpty()) View.GONE else View.VISIBLE
        updateContextLabel()
    }

    fun setContext(context: SearchContext?) {
        lastContext = context
        updateContextLabel()
    }

    fun setStatus(text: String) {
        lastStatus = text
        statusLabel?.text = text
    }

    fun setLoading(value: Boolean) {
        loading = value
        progress?.visibility = if (value) View.VISIBLE else View.GONE
    }

    fun setScanning(value: Boolean) {
        scanningAll = value
        scanAllButton?.text = if (value) "Stop collection" else "Collect off-screen items"
    }

    fun setOverlayVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.INVISIBLE
        bubble?.visibility = if (sheet == null) visibility else View.INVISIBLE
        sheet?.visibility = visibility
    }

    fun isPanelOpen(): Boolean = sheet != null

    fun notifyItemOpened() {
        haptic(success = true)
        setStatus("Item opened")
    }

    private fun openSheet() {
        if (sheet != null) return
        bubble?.visibility = View.INVISIBLE
        val outer = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.vp_sheet)
            elevation = dp(12).toFloat()
        }
        val handleArea = FrameLayout(service).apply {
            contentDescription = "Drag to resize ValuePilot"
            isFocusable = true
        }
        handleArea.addView(
            View(service).apply { setBackgroundResource(R.drawable.vp_drag_handle) },
            FrameLayout.LayoutParams(dp(44), dp(5), Gravity.CENTER)
        )
        outer.addView(handleArea, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)))
        val frame = FrameLayout(service)
        contentFrame = frame
        outer.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val main = buildMainContent()
        mainContent = main
        frame.addView(main, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val displayHeight = service.resources.displayMetrics.heightPixels
        val defaultHeight = (displayHeight * .48).toInt().coerceIn(dp(320), displayHeight - dp(72))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            defaultHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        sheet = outer
        sheetParams = params
        installSheetDrag(handleArea, outer, params)
        wm.addView(outer, params)
        if (animationsEnabled) {
            outer.translationY = dp(48).toFloat()
            outer.alpha = 0f
            outer.animate().translationY(0f).alpha(1f).setDuration(180).setInterpolator(DecelerateInterpolator()).start()
        }
        setResults(lastResults)
        setStatus(lastStatus)
        setLoading(loading)
        setScanning(scanningAll)
        haptic(success = false)
    }

    private fun minimizeSheet() {
        val current = sheet ?: return
        val finish = {
            runCatching { wm.removeView(current) }
            sheet = null
            sheetParams = null
            clearSheetReferences()
            bubble?.visibility = View.VISIBLE
        }
        if (animationsEnabled) {
            current.animate().translationY(current.height.toFloat()).alpha(.3f).setDuration(150).withEndAction(finish).start()
        } else {
            finish()
        }
    }

    private fun buildMainContent(): View {
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(10))
        }
        val header = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(label("ValuePilot", 21f, 0xff111827.toInt(), Typeface.BOLD))
        contextLabel = label("Current page · 0 matches", 13f, 0xff4b5563.toInt()).also {
            it.maxLines = 2
            titleBlock.addView(it)
        }
        val minimize = Button(service).apply {
            text = "—"
            textSize = 18f
            minWidth = 0
            minimumWidth = 0
            contentDescription = "Minimize ValuePilot"
            setOnClickListener { minimizeSheet() }
        }
        header.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(minimize, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)

        val actions = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(4))
        }
        rankButton = Button(service).apply {
            text = "Smart Value ▾"
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            textSize = 13f
            contentDescription = "Choose how results are ranked"
            setOnClickListener { showRankMenu(this) }
        }
        val filters = Button(service).apply {
            text = "Filters"
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            textSize = 13f
            setOnClickListener { showFilters() }
        }
        val rescan = Button(service).apply {
            text = "Rescan"
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            textSize = 13f
            setOnClickListener {
                haptic(success = false)
                onRescan()
            }
        }
        actions.addView(rankButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))
        actions.addView(filters, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .8f))
        actions.addView(rescan, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .8f))
        root.addView(actions)

        progress = ProgressBar(service, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = if (loading) View.VISIBLE else View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        statusLabel = label(lastStatus, 11f, 0xff6b7280.toInt()).apply {
            setPadding(0, dp(5), 0, dp(5))
            maxLines = if (advancedMode) 3 else 1
            contentDescription = "ValuePilot status"
        }
        root.addView(statusLabel)

        val resultsFrame = FrameLayout(service)
        val list = RecyclerView(service).apply {
            layoutManager = LinearLayoutManager(service)
            adapter = resultAdapter
            setHasFixedSize(false)
            clipToPadding = false
            setPadding(0, 0, 0, dp(12))
            itemAnimator = if (animationsEnabled) DefaultItemAnimator().apply {
                addDuration = 130
                moveDuration = 160
                changeDuration = 120
                removeDuration = 100
            } else null
        }
        recycler = list
        val empty = label("No matching products yet. Keep the shopping page open, scroll to reveal more items, or tap Rescan.", 14f, 0xff4b5563.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        emptyState = empty
        resultsFrame.addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        resultsFrame.addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(resultsFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun showRankMenu(anchor: View) {
        val menu = PopupMenu(service, anchor)
        val available = availableModes()
        var order = 0

        fun heading(text: String) {
            menu.menu.add(0, -10_000 - order, order++, text).isEnabled = false
        }
        fun option(mode: RankMode, text: String) {
            menu.menu.add(1, mode.ordinal, order++, text).isCheckable = true
            menu.menu.findItem(mode.ordinal).isChecked = selectedMode == mode
        }

        heading("Recommended")
        option(RankMode.SMART, "Smart Value")
        val grocery = listOf(RankMode.MASS, RankMode.VOLUME, RankMode.UNIT).filter(available::contains)
        if (grocery.isNotEmpty()) {
            heading("Grocery")
            grocery.forEach { mode ->
                option(mode, when (mode) {
                    RankMode.MASS -> "Price per kg"
                    RankMode.VOLUME -> "Price per litre"
                    RankMode.UNIT -> "Price per item"
                    else -> error("unexpected")
                })
            }
        }
        val restaurant = listOf(RankMode.CALORIE, RankMode.PORTION, RankMode.MEAT, RankMode.PIZZA).filter(available::contains)
        if (restaurant.isNotEmpty()) {
            heading("Restaurant")
            restaurant.forEach { mode ->
                option(mode, when (mode) {
                    RankMode.CALORIE -> "Calories per dollar"
                    RankMode.PORTION -> "Food amount/$ · Estimate"
                    RankMode.MEAT -> "Meat value/$ · Estimate"
                    RankMode.PIZZA -> "Pizza size per dollar"
                    else -> error("unexpected")
                })
            }
        }
        menu.setOnMenuItemClickListener { item ->
            val mode = RankMode.entries.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            selectedMode = mode
            rankButton?.text = "${rankLabel(mode)} ▾"
            onMode(mode)
            haptic(success = false)
            true
        }
        menu.show()
    }

    private fun availableModes(): Set<RankMode> {
        val items = lastResults.map(RankedItem::item)
        val query = lastContext?.query.orEmpty().lowercase()
        return buildSet {
            add(RankMode.SMART)
            if (items.any { it.quantity?.kind == Quantity.Kind.MASS_G } || Regex("meat|beef|chicken|pork|steak").containsMatchIn(query)) add(RankMode.MASS)
            if (items.any { it.quantity?.kind == Quantity.Kind.VOLUME_ML } || query.contains("milk")) add(RankMode.VOLUME)
            if (items.any { it.quantity?.kind == Quantity.Kind.COUNT } || query.contains("egg")) add(RankMode.UNIT)
            if (items.any { it.calories != null }) add(RankMode.CALORIE)
            if (items.any { it.portion != null }) add(RankMode.PORTION)
            if (items.any { it.meatPointsPerDollar != null }) add(RankMode.MEAT)
            if (items.any { it.quantity?.kind == Quantity.Kind.PIZZA_AREA_SQIN } || query.contains("pizza")) add(RankMode.PIZZA)
        }
    }

    private fun showFilters() {
        val frame = contentFrame ?: return
        val filters = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(16))
        }
        val header = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("Filters", 21f, 0xff111827.toInt(), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(service).apply { text = "Done"; isAllCaps = false; setOnClickListener { showMainContent() } }, LinearLayout.LayoutParams(dp(88), dp(48)))
        filters.addView(header)
        val scroll = ScrollView(service)
        val controls = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(label("Maximum spend", 13f, 0xff374151.toInt(), Typeface.BOLD))
        val budget = EditText(service).apply {
            hint = "No budget limit"
            contentDescription = "Maximum total spend"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            minHeight = dp(48)
            maxPrice?.let { setText(ValueEngine.fmt(it, 2)) }
        }
        controls.addView(budget)
        val food = checkBox("Food products only", foodOnly)
        val pork = checkBox("Exclude pork", excludePork)
        val member = checkBox("Use member prices when shown", useMemberPrices)
        val haptics = checkBox("Interaction haptics", hapticsEnabled)
        val advanced = checkBox("Advanced/debug controls", advancedMode)
        controls.addView(food)
        controls.addView(pork)
        controls.addView(member)
        controls.addView(haptics)
        controls.addView(advanced)
        val advancedActions = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (advancedMode) View.VISIBLE else View.GONE
            addView(label("Advanced scanning", 13f, 0xff374151.toInt(), Typeface.BOLD))
        }
        scanAllButton = Button(service).apply {
            text = if (scanningAll) "Stop collection" else "Collect off-screen items"
            isAllCaps = false
            setOnClickListener {
                if (scanningAll) {
                    onStop()
                } else {
                    minimizeSheet()
                    onScanAll()
                }
            }
        }
        advancedActions.addView(scanAllButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        advancedActions.addView(Button(service).apply {
            text = "Run on-device OCR"
            isAllCaps = false
            setOnClickListener { onOcr() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        advancedActions.addView(Button(service).apply {
            text = "Clear collected results"
            isAllCaps = false
            setOnClickListener { onClear() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        controls.addView(advancedActions)
        controls.addView(Button(service).apply {
            text = "About rankings"
            isAllCaps = false
            setOnClickListener { showAboutRankings() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        scroll.addView(controls)
        filters.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        budget.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                maxPrice = s?.toString()?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }
                publishFilters()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        food.setOnCheckedChangeListener { _, checked -> foodOnly = checked; publishFilters() }
        pork.setOnCheckedChangeListener { _, checked -> excludePork = checked; publishFilters() }
        member.setOnCheckedChangeListener { _, checked -> useMemberPrices = checked; publishFilters() }
        haptics.setOnCheckedChangeListener { _, checked -> hapticsEnabled = checked; publishFilters() }
        advanced.setOnCheckedChangeListener { _, checked ->
            advancedMode = checked
            advancedActions.visibility = if (checked) View.VISIBLE else View.GONE
            statusLabel?.maxLines = if (checked) 3 else 1
            publishFilters()
        }
        showContent(frame, filters)
    }

    private fun showAboutRankings() {
        val frame = contentFrame ?: return
        val about = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(16))
        }
        val header = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("About rankings", 21f, 0xff111827.toInt(), Typeface.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(Button(service).apply { text = "Back"; isAllCaps = false; setOnClickListener { showFilters() } }, LinearLayout.LayoutParams(dp(88), dp(48)))
        about.addView(header)
        about.addView(label(
            "Smart Value chooses the strongest exact measurement available for the current products: price per kg, litre, or item; calories per dollar; or pizza area. " +
                "Food amount and meat value are clearly labeled estimates and never replace exact price, quantity, promotion, or unit mathematics. Member prices are used only when you enable that filter.",
            14f,
            0xff374151.toInt()
        ).apply { setLineSpacing(0f, 1.15f); setPadding(0, dp(12), 0, 0) })
        showContent(frame, about)
    }

    private fun showMainContent() {
        val frame = contentFrame ?: return
        val main = mainContent ?: return
        showContent(frame, main)
        setResults(lastResults)
    }

    private fun showContent(frame: FrameLayout, view: View) {
        frame.removeAllViews()
        frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (animationsEnabled) {
            view.alpha = 0f
            view.translationX = dp(24).toFloat()
            view.animate().alpha(1f).translationX(0f).setDuration(140).start()
        }
    }

    private fun installSheetDrag(handle: View, outer: View, params: WindowManager.LayoutParams) {
        var downY = 0f
        var startHeight = 0
        var moved = false
        handle.setOnClickListener {
            val displayHeight = service.resources.displayMetrics.heightPixels
            val target = if (params.height > displayHeight * .66) {
                (displayHeight * .48).toInt()
            } else {
                (displayHeight * .88).toInt()
            }
            params.height = target.coerceIn(dp(320), displayHeight - dp(48))
            runCatching { wm.updateViewLayout(outer, params) }
        }
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    startHeight = params.height
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val displayHeight = service.resources.displayMetrics.heightPixels
                    if (kotlin.math.abs(event.rawY - downY) > dp(5)) moved = true
                    params.height = (startHeight + (downY - event.rawY).toInt()).coerceIn(dp(220), displayHeight - dp(48))
                    runCatching { wm.updateViewLayout(outer, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        handle.performClick()
                    } else {
                        val displayHeight = service.resources.displayMetrics.heightPixels
                        if (params.height < displayHeight * .32) {
                            minimizeSheet()
                        } else {
                            val target = if (params.height > displayHeight * .66) (displayHeight * .88).toInt() else (displayHeight * .48).toInt()
                            params.height = target.coerceIn(dp(320), displayHeight - dp(48))
                            runCatching { wm.updateViewLayout(outer, params) }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun publishFilters() {
        onFilters(maxPrice, foodOnly, excludePork, useMemberPrices, advancedMode, hapticsEnabled)
        haptic(success = false)
    }

    private fun updateContextLabel() {
        val context = lastContext
        val title = context?.displayQuery ?: "Current page"
        contextLabel?.text = "$title · ${lastResults.size} ${if (lastResults.size == 1) "match" else "matches"}"
    }

    private fun rankLabel(mode: RankMode): String = when (mode) {
        RankMode.SMART -> "Smart Value"
        RankMode.MASS -> "Price per kg"
        RankMode.VOLUME -> "Price per litre"
        RankMode.UNIT -> "Price per item"
        RankMode.CALORIE -> "Calories per dollar"
        RankMode.PIZZA -> "Pizza size per dollar"
        RankMode.PORTION -> "Food amount/$ · Estimate"
        RankMode.MEAT -> "Meat value/$ · Estimate"
    }

    private fun haptic(success: Boolean) {
        if (!hapticsEnabled) return
        val target = sheet ?: bubble ?: return
        val constant = if (success && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        target.performHapticFeedback(constant)
    }

    private fun checkBox(text: String, checked: Boolean): CheckBox = CheckBox(service).apply {
        this.text = text
        isChecked = checked
        minHeight = dp(48)
        textSize = 14f
    }

    private fun label(text: String, size: Float, color: Int, style: Int = Typeface.NORMAL): TextView = TextView(service).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        setTypeface(typeface, style)
    }

    private fun clearSheetReferences() {
        contentFrame = null
        mainContent = null
        contextLabel = null
        statusLabel = null
        progress = null
        emptyState = null
        recycler = null
        rankButton = null
        scanAllButton = null
    }

    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density).toInt()
}
