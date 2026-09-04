package com.valuepilot.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.valuepilot.core.ShoppingItemKey

/**
 * Gives assistive technology the same context as the visible extra-stop summary,
 * including whether the progressive settings panel is currently open.
 *
 * This is fixed presentation copy. It does not interpret the threshold or make
 * a second-stop decision.
 */
internal fun practicalShoppingExtraStopSettingsContentDescription(
    summary: String,
    expanded: Boolean,
    notice: String? = null
): String {
    require(summary.isNotBlank())
    require(notice == null || notice.isNotBlank())
    val action = if (expanded) "Hide" else "Show"
    return if (notice == null) {
        "$action extra-stop rule settings. $summary"
    } else {
        "$action extra-stop rule settings. $summary. $notice."
    }
}

/**
 * Replaceable Android renderer for the fictional Practical Shopping Home proof.
 * It receives immutable render state and forwards typed actions only.
 */
class PracticalShoppingHomeSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val queryOwnerControls = mutableListOf<View>()
    private val submitOwnerControls = mutableListOf<View>()
    private val itemRemovalOwnerControls = mutableListOf<View>()
    private val unknownRemovalOwnerControls = mutableListOf<View>()
    private val offlineCatalogOwnerControls = mutableListOf<View>()
    private val itemDetailsOwnerControls = mutableListOf<View>()
    private val chickenChoiceOwnerControls = mutableListOf<View>()
    private val extraStopOwnerControls = mutableListOf<View>()
    private val extraStopChoiceOwnerControls = mutableListOf<View>()
    private val goodPriceOwnerControls = mutableListOf<View>()

    private var hasRenderedState = false
    private var lastRenderedSubmitEnabled = false

    var onQueryChanged: ((String) -> Unit)? = null
        set(value) {
            field = value
            queryOwnerControls.forEach { control ->
                control.isEnabled = value != null && hasRenderedState
            }
        }
    var onSubmit: ((String) -> Unit)? = null
        set(value) {
            field = value
            submitOwnerControls.forEach { control ->
                control.isEnabled = value != null && lastRenderedSubmitEnabled
            }
        }
    var onRemoveItem: ((ShoppingItemKey) -> Unit)? = null
        set(value) {
            field = value
            itemRemovalOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onRemoveUnknownItem: ((String) -> Unit)? = null
        set(value) {
            field = value
            unknownRemovalOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onFindOfflineCatalogMatch: ((String) -> Unit)? = null
        set(value) {
            field = value
            offlineCatalogOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onChickenChoice: ((LocalSamplePracticalShoppingDemo.ChickenChoice) -> Unit)? = null
        set(value) {
            field = value
            chickenChoiceOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onExtraStopMinimumSavingsChoice:
        ((LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice) -> Unit)? = null
        set(value) {
            field = value
            extraStopOwnerControls.forEach { control ->
                control.isEnabled =
                    value != null && hasRenderedState && extraStopSettingsButton.visibility == VISIBLE
            }
            extraStopChoiceOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
            if (value == null) {
                // A detached owner must not leave a previously expanded settings
                // panel visible with stale choices while the surface remains mounted.
                extraStopSettingsExpanded = false
                syncExtraStopSettingsVisibility()
            }
        }
    var onEditItemDetails: ((ShoppingItemKey) -> Unit)? = null
        set(value) {
            field = value
            itemDetailsOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onCompare: (() -> Unit)? = null
        set(value) {
            field = value
            compareActionButton.isEnabled = value != null && hasRenderedState
        }
    var onGoodPrice: (() -> Unit)? = null
        set(value) {
            field = value
            goodPriceOwnerControls.forEach { control ->
                control.isEnabled = value != null && hasRenderedState
            }
        }

    private var suppressInputCallback = false
    private var extraStopSettingsExpanded = false
    private var currentExtraStopSettingsNotice: String? = null

    private val input = TextInputEditText(context).apply {
        maxLines = 3
        imeOptions = EditorInfo.IME_ACTION_DONE
        inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isSaveEnabled = false
        // A detached Home surface must not accept edits that have no owner to consume them.
        isEnabled = false
    }

    private val inputLayout = TextInputLayout(context).apply {
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        hint = context.getString(R.string.home_list_hint)
        setBoxBackgroundColor(Color.WHITE)
        setBoxCornerRadii(
            dp(18).toFloat(),
            dp(18).toFloat(),
            dp(18).toFloat(),
            dp(18).toFloat()
        )
        endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        addView(input)
    }

    private var appliedQueryCharacterLimit: Int? = null

    private val submitButton = MaterialButton(context).apply {
        text = context.getString(R.string.home_plan_action)
        // Fail closed until the first immutable render state supplies readiness.
        isEnabled = false
        isAllCaps = false
        textSize = 16f
        cornerRadius = dp(16)
        layoutParams = fullWidth(dp(54), 12)
    }

    private val message = line("", 14f, "#6B7280").apply {
        setPadding(dp(2), dp(14), dp(2), 0)
        // Refinement and validation feedback changes after user actions. Let
        // assistive technology hear the new immutable message without making
        // the View infer any shopping state.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private val itemsHeading = heading(context.getString(R.string.home_items_title))
    private val itemsContainer = bareColumn()

    private val refinementBody = column()
    private val refinementCard = card("#FFFBEB", "#FDE68A", 16, refinementBody)

    private val unknownBody = column()
    private val unknownCard = card("#FFF7ED", "#FED7AA", 12, unknownBody)

    private val resultContainer = PracticalShoppingPlanResultSurfaceView(context)
    private val extraStopSettingsButton = MaterialButton(context).apply {
        isAllCaps = false
        textSize = 14f
        cornerRadius = dp(16)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(Color.parseColor("#D1D5DB"))
        setTextColor(Color.parseColor("#374151"))
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        layoutParams = fullWidth(dp(50), 12)
        setOnClickListener {
            extraStopSettingsExpanded = !extraStopSettingsExpanded
            syncExtraStopSettingsAccessibility()
            syncExtraStopSettingsVisibility()
        }
    }
    private val extraStopSettingsBody = column()
    private val extraStopSettingsCard =
        card("#F9FAFB", "#E5E7EB", 8, extraStopSettingsBody)
    private val privateMemorySummary = line("", 13f, "#6B7280").apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = GONE
    }
    private val privateMemoryNotice = line("", 13f, "#92400E").apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = GONE
    }
    private val sampleNotice = line("", 13f, "#374151")
    private val goodPriceActionButton = goodPriceButton()
    private val compareActionButton = compareButton()

    init {
        orientation = VERTICAL
        isSaveEnabled = false

        queryOwnerControls += inputLayout
        queryOwnerControls += input
        submitOwnerControls += submitButton
        extraStopOwnerControls += extraStopSettingsButton

        addView(inputLayout)
        addView(submitButton)
        addView(message)
        addView(sampleCard())
        addView(privateMemorySummary)
        addView(privateMemoryNotice)
        addView(itemsHeading)
        addView(itemsContainer)
        addView(refinementCard)
        addView(unknownCard)
        addView(resultContainer)
        addView(extraStopSettingsButton)
        addView(extraStopSettingsCard)
        addView(goodPriceActionButton)
        addView(compareActionButton)

        itemsHeading.visibility = GONE
        refinementCard.visibility = GONE
        unknownCard.visibility = GONE
        extraStopSettingsCard.visibility = GONE

        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!suppressInputCallback) onQueryChanged?.invoke(s?.toString().orEmpty())
                }
            }
        )
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        submitButton.setOnClickListener { submit() }
    }

    fun render(state: PracticalShoppingHomeRenderState) {
        itemRemovalOwnerControls.clear()
        unknownRemovalOwnerControls.clear()
        offlineCatalogOwnerControls.clear()
        itemDetailsOwnerControls.clear()
        chickenChoiceOwnerControls.clear()
        extraStopChoiceOwnerControls.clear()
        hasRenderedState = true

        syncQueryCharacterLimit(state.queryCharacterLimit)
        syncQuery(state.query)
        // The clear-text end icon belongs to the TextInputLayout wrapper, so
        // gate the wrapper as well as the editor when no owner can consume
        // query changes.
        inputLayout.isEnabled = onQueryChanged != null
        input.isEnabled = onQueryChanged != null
        lastRenderedSubmitEnabled = state.submitEnabled
        submitButton.isEnabled = state.submitEnabled && onSubmit != null
        compareActionButton.isEnabled = onCompare != null
        renderMessage(state)
        renderItems(state.items)
        renderRefinement(state.refinement)
        renderUnknown(state.unknownItems)
        resultContainer.render(state.result, state.sampleNotice)
        renderExtraStopSettings(state.extraStopSettings)
        renderPrivateMemory(state.privateMemoryStatus, state.privateMemorySummary)
        sampleNotice.text = state.sampleNotice
        goodPriceOwnerControls.forEach { control ->
            control.isEnabled = onGoodPrice != null && hasRenderedState
        }
    }

    private fun submit() {
        // The IME action can arrive independently of the visible button click. Keep
        // both entry points on the same immutable readiness boundary.
        if (!submitButton.isEnabled || onSubmit == null) {
            hideKeyboard()
            return
        }
        onSubmit?.invoke(input.text?.toString().orEmpty())
        hideKeyboard()
    }

    private fun syncQuery(query: String) {
        if (input.text?.toString() == query) return
        suppressInputCallback = true
        input.setText(query)
        input.setSelection(query.length)
        suppressInputCallback = false
    }

    private fun syncQueryCharacterLimit(limit: Int) {
        if (appliedQueryCharacterLimit == limit) return

        input.filters = arrayOf(InputFilter.LengthFilter(limit + 1))
        inputLayout.counterMaxLength = limit
        inputLayout.isCounterEnabled = true
        appliedQueryCharacterLimit = limit
    }

    private fun renderMessage(state: PracticalShoppingHomeRenderState) {
        val value = state.message
        if (value == null) {
            message.visibility = GONE
            return
        }
        message.text = value
        message.setTextColor(
            Color.parseColor(
                when (state.messageTone) {
                    PracticalShoppingHomeMessageTone.NEUTRAL -> "#6B7280"
                    PracticalShoppingHomeMessageTone.ACTION_REQUIRED -> "#92400E"
                    PracticalShoppingHomeMessageTone.ERROR -> "#B42318"
                }
            )
        )
        message.visibility = VISIBLE
    }

    private fun renderPrivateMemory(
        status: PracticalShoppingHomePrivateMemoryStatus,
        summary: String?
    ) {
        if (status == PracticalShoppingHomePrivateMemoryStatus.UNAVAILABLE) {
            privateMemorySummary.text = ""
            privateMemorySummary.visibility = GONE
            privateMemoryNotice.text = context.getString(R.string.home_private_memory_unavailable)
            privateMemoryNotice.visibility = VISIBLE
        } else {
            privateMemorySummary.text = summary.orEmpty()
            privateMemorySummary.visibility = if (summary == null) GONE else VISIBLE
            privateMemoryNotice.text = ""
            privateMemoryNotice.visibility = GONE
        }
    }

    private fun renderItems(items: List<PracticalShoppingHomeItemRenderState>) {
        itemsContainer.removeAllViews()
        itemsHeading.visibility = if (items.isEmpty()) GONE else VISIBLE
        items.forEach { item ->
            itemsContainer.addView(itemRow(item))
        }
    }

    private fun itemRow(item: PracticalShoppingHomeItemRenderState): View =
        bareColumn().apply {
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 5)
            addView(
                actionRow(
                    label = "${item.name}  •  ${item.detail}",
                    onRemove = { onRemoveItem?.invoke(item.key) },
                    removeEnabled = onRemoveItem != null,
                    removeOwnerControls = itemRemovalOwnerControls,
                    removeDescription =
                        context.getString(R.string.home_remove_item_description, item.name),
                    onDetails = { onEditItemDetails?.invoke(item.key) },
                    detailsEnabled = onEditItemDetails != null,
                    detailsOwnerControls = itemDetailsOwnerControls,
                    detailsLabel = item.requestDetailsActionLabel,
                    detailsDescription =
                        context.getString(R.string.home_item_details_action_description, item.name)
                )
            )
            item.storeAssignment?.let { store ->
                addView(line("Buy at $store", 12f, "#374151", topPadding = 2))
            }
            item.priceCoverageNotice?.let { notice ->
                addView(line(notice, 12f, "#92400E", topPadding = 2))
            }
            item.personalHistoryNotice?.let { notice ->
                addView(line(notice, 12f, "#6B7280", topPadding = 2))
            }
            addView(line(item.requestDetailsSummary, 12f, "#6B7280", topPadding = 2))
            item.requestDetailsNotice?.let { notice ->
                addView(line(notice, 12f, "#92400E", topPadding = 2))
            }
        }

    private fun actionRow(
        label: String,
        onRemove: () -> Unit,
        removeEnabled: Boolean = false,
        removeOwnerControls: MutableList<View>? = null,
        lineColor: String = "#374151",
        removeDescription: String = context.getString(R.string.home_remove_item),
        onDetails: (() -> Unit)? = null,
        detailsEnabled: Boolean = false,
        detailsOwnerControls: MutableList<View>? = null,
        detailsLabel: String = context.getString(R.string.home_item_details_action),
        detailsDescription: String = detailsLabel
    ): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 5)

            addView(
                line(label, 14f, lineColor, topPadding = 7).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            onDetails?.let {
                addView(
                    detailButton(
                        it,
                        detailsLabel,
                        detailsDescription,
                        detailsEnabled,
                        detailsOwnerControls
                    )
                )
            }
            addView(
                removeButton(
                    onRemove,
                    removeDescription,
                    removeEnabled,
                    removeOwnerControls
                )
            )
        }

    private fun detailButton(
        onDetails: () -> Unit,
        label: String,
        description: String,
        enabled: Boolean,
        ownerControls: MutableList<View>? = null
    ): MaterialButton = MaterialButton(context).apply {
        ownerControls?.add(this)
        text = label
        contentDescription = description
        isAllCaps = false
        textSize = 12f
        minHeight = dp(40)
        minimumHeight = dp(40)
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
        isEnabled = enabled
        setPadding(dp(8), 0, dp(8), 0)
        layoutParams = LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            dp(40)
        ).apply { leftMargin = dp(4) }
        setOnClickListener { onDetails() }
    }

    private fun removeButton(
        onRemove: () -> Unit,
        description: String,
        enabled: Boolean,
        ownerControls: MutableList<View>? = null
    ): MaterialButton =
        MaterialButton(context).apply {
            ownerControls?.add(this)
            text = context.getString(R.string.home_remove_item)
            contentDescription = description
            isAllCaps = false
            textSize = 12f
            minHeight = dp(40)
            minimumHeight = dp(40)
            minWidth = 0
            minimumWidth = 0
            insetTop = 0
            insetBottom = 0
            isEnabled = enabled
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply { leftMargin = dp(8) }
            setOnClickListener { onRemove() }
        }

    private fun renderRefinement(state: PracticalShoppingHomeRefinementRenderState?) {
        refinementBody.removeAllViews()
        if (state == null) {
            refinementCard.visibility = GONE
            return
        }
        refinementBody.addView(line(context.getString(R.string.home_refinement_title), 11f, "#92400E", true))
        refinementBody.addView(
            line(
                context.getString(R.string.home_refinement_question, state.prompt),
                17f,
                "#111827",
                true,
                5
            )
        )
        refinementBody.addView(
            ChipGroup(context).apply {
                isSingleLine = false
                setPadding(0, dp(8), 0, 0)
                setChipSpacingHorizontal(dp(8))
                setChipSpacingVertical(dp(6))
                state.choices.forEach { option ->
                    addView(
                        Chip(context).apply {
                            chickenChoiceOwnerControls += this
                            text = option.label
                            isCheckable = false
                            isEnabled = onChickenChoice != null
                            setOnClickListener {
                                onChickenChoice?.invoke(option.choice)
                                hideKeyboard()
                            }
                        }
                    )
                }
            }
        )
        refinementCard.visibility = VISIBLE
    }

    private fun renderUnknown(items: List<String>) {
        unknownBody.removeAllViews()
        if (items.isEmpty()) {
            unknownCard.visibility = GONE
            return
        }
        unknownBody.addView(line(context.getString(R.string.home_unknown_title), 13f, "#92400E", true))
        items.forEach { token ->
            unknownBody.addView(
                actionRow(
                    label = "• $token",
                    onRemove = { onRemoveUnknownItem?.invoke(token) },
                    removeEnabled = onRemoveUnknownItem != null,
                    removeOwnerControls = unknownRemovalOwnerControls,
                    lineColor = "#92400E",
                    removeDescription =
                        context.getString(R.string.home_remove_item_description, token),
                    onDetails = { onFindOfflineCatalogMatch?.invoke(token) },
                    detailsEnabled = onFindOfflineCatalogMatch != null,
                    detailsOwnerControls = offlineCatalogOwnerControls,
                    detailsLabel = context.getString(R.string.home_unknown_find_matches),
                    detailsDescription =
                        context.getString(R.string.home_unknown_find_matches_description, token)
                )
            )
        }
        unknownCard.visibility = VISIBLE
    }

    private fun renderExtraStopSettings(
        state: PracticalShoppingHomeExtraStopSettingsRenderState
    ) {
        extraStopSettingsButton.visibility = if (state.visible) VISIBLE else GONE
        // The disclosure itself is an owner-driven control too. Keep a detached
        // Home renderer inert even when an already-projected result makes the
        // settings panel visible; the option chips below use the same gate.
        extraStopSettingsButton.isEnabled =
            state.visible && onExtraStopMinimumSavingsChoice != null
        if (!state.visible || onExtraStopMinimumSavingsChoice == null) {
            // Do not leave a stale expanded panel on screen after its owner
            // disappears; a detached/reused renderer should look inert too.
            extraStopSettingsExpanded = false
        }
        currentExtraStopSettingsNotice = state.notice
        extraStopSettingsButton.text = state.summary
        syncExtraStopSettingsAccessibility()
        extraStopSettingsBody.removeAllViews()
        extraStopSettingsBody.addView(line(state.prompt, 15f, "#111827", true))
        state.notice?.let {
            extraStopSettingsBody.addView(line(it, 13f, "#92400E", topPadding = 8))
        }
        extraStopSettingsBody.addView(
            ChipGroup(context).apply {
                isSingleLine = false
                isSingleSelection = true
                setPadding(0, dp(8), 0, 0)
                setChipSpacingHorizontal(dp(8))
                setChipSpacingVertical(dp(6))
                state.choices.forEach { option ->
                    addView(
                        Chip(context).apply {
                            extraStopChoiceOwnerControls += this
                            text = option.label
                            isCheckable = true
                            isChecked = option.selected
                            isEnabled = onExtraStopMinimumSavingsChoice != null
                            setOnClickListener {
                                onExtraStopMinimumSavingsChoice?.invoke(option.choice)
                            }
                        }
                    )
                }
            }
        )
        syncExtraStopSettingsVisibility()
    }

    private fun syncExtraStopSettingsAccessibility() {
        extraStopSettingsButton.contentDescription =
            if (extraStopSettingsButton.visibility == VISIBLE) {
                practicalShoppingExtraStopSettingsContentDescription(
                    summary = extraStopSettingsButton.text?.toString().orEmpty(),
                    expanded = extraStopSettingsExpanded,
                    notice = currentExtraStopSettingsNotice
                )
            } else {
                null
            }
    }

    private fun syncExtraStopSettingsVisibility() {
        extraStopSettingsCard.visibility =
            if (extraStopSettingsButton.visibility == VISIBLE && extraStopSettingsExpanded) {
                VISIBLE
            } else {
                GONE
            }
    }

    private fun sampleCard(): View =
        card("#F0FDF4", "#BBF7D0", 20).apply {
            addView(
                column().apply {
                    addView(line(context.getString(R.string.home_sample_badge), 11f, "#047857", true))
                    sampleNotice.setPadding(0, dp(4), 0, 0)
                    addView(sampleNotice)
                }
            )
        }

    private fun compareButton(): MaterialButton = MaterialButton(context).apply {
        text = context.getString(R.string.home_compare_secondary)
        isAllCaps = false
        textSize = 14f
        cornerRadius = dp(16)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(Color.parseColor("#D1D5DB"))
        setTextColor(Color.parseColor("#374151"))
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        layoutParams = fullWidth(dp(50), 12)
        // Keep the owner-driven comparison route inert until its callback is attached.
        isEnabled = false
        setOnClickListener { onCompare?.invoke() }
    }

    private fun goodPriceButton(): MaterialButton = MaterialButton(context).apply {
        goodPriceOwnerControls += this
        text = context.getString(R.string.home_good_price_secondary)
        isAllCaps = false
        textSize = 14f
        cornerRadius = dp(16)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(Color.parseColor("#D1D5DB"))
        setTextColor(Color.parseColor("#374151"))
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        layoutParams = fullWidth(dp(50), 12)
        // Keep the owner-driven price question inert until its callback is attached.
        isEnabled = false
        setOnClickListener { onGoodPrice?.invoke() }
    }

    private fun heading(value: String): TextView = line(value, 13f, "#374151", true, 18)

    private fun card(
        background: String,
        stroke: String,
        topMargin: Int,
        body: View? = null
    ): MaterialCardView = MaterialCardView(context).apply {
        radius = dp(18).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(Color.parseColor(background))
        strokeColor = Color.parseColor(stroke)
        strokeWidth = dp(1)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, topMargin)
        body?.let(::addView)
    }

    private fun bareColumn(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
    }

    private fun column(): LinearLayout = bareColumn().apply {
        setPadding(dp(16), dp(15), dp(16), dp(15))
    }

    private fun line(
        value: String,
        sizeSp: Float,
        color: String,
        bold: Boolean = false,
        topPadding: Int = 0
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
    }

    private fun fullWidth(height: Int, topMargin: Int): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, height).apply { this.topMargin = dp(topMargin) }

    private fun hideKeyboard() {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
