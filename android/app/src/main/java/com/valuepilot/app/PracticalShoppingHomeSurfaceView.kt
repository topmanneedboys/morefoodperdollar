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
 * Replaceable Android renderer for the fictional Practical Shopping Home proof.
 * It receives immutable render state and forwards typed actions only.
 */
class PracticalShoppingHomeSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onQueryChanged: ((String) -> Unit)? = null
    var onSubmit: ((String) -> Unit)? = null
    var onRemoveItem: ((ShoppingItemKey) -> Unit)? = null
    var onRemoveUnknownItem: ((String) -> Unit)? = null
    var onChickenChoice: ((LocalSamplePracticalShoppingDemo.ChickenChoice) -> Unit)? = null
    var onExtraStopMinimumSavingsChoice:
        ((LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice) -> Unit)? = null
    var onEditItemDetails: ((ShoppingItemKey) -> Unit)? = null
    var onCompare: (() -> Unit)? = null

    private var suppressInputCallback = false
    private var extraStopSettingsExpanded = false

    private val input = TextInputEditText(context).apply {
        maxLines = 3
        imeOptions = EditorInfo.IME_ACTION_DONE
        inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isSaveEnabled = false
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
        isAllCaps = false
        textSize = 16f
        cornerRadius = dp(16)
        layoutParams = fullWidth(dp(54), 12)
    }

    private val message = line("", 14f, "#6B7280").apply {
        setPadding(dp(2), dp(14), dp(2), 0)
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
            syncExtraStopSettingsVisibility()
        }
    }
    private val extraStopSettingsBody = column()
    private val extraStopSettingsCard =
        card("#F9FAFB", "#E5E7EB", 8, extraStopSettingsBody)
    private val sampleNotice = line("", 13f, "#374151")

    init {
        orientation = VERTICAL
        isSaveEnabled = false

        addView(inputLayout)
        addView(submitButton)
        addView(message)
        addView(sampleCard())
        addView(itemsHeading)
        addView(itemsContainer)
        addView(refinementCard)
        addView(unknownCard)
        addView(resultContainer)
        addView(extraStopSettingsButton)
        addView(extraStopSettingsCard)
        addView(compareButton())

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
        syncQueryCharacterLimit(state.queryCharacterLimit)
        syncQuery(state.query)
        submitButton.isEnabled = state.submitEnabled
        renderMessage(state)
        renderItems(state.items)
        renderRefinement(state.refinement)
        renderUnknown(state.unknownItems)
        resultContainer.render(state.result)
        renderExtraStopSettings(state.extraStopSettings)
        sampleNotice.text = state.sampleNotice
    }

    private fun submit() {
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
                    removeDescription =
                        context.getString(R.string.home_remove_item_description, item.name),
                    onDetails = { onEditItemDetails?.invoke(item.key) },
                    detailsLabel = item.requestDetailsActionLabel,
                    detailsDescription =
                        context.getString(R.string.home_item_details_action_description, item.name)
                )
            )
            addView(line(item.requestDetailsSummary, 12f, "#6B7280", topPadding = 2))
            item.requestDetailsNotice?.let { notice ->
                addView(line(notice, 12f, "#92400E", topPadding = 2))
            }
        }

    private fun actionRow(
        label: String,
        onRemove: () -> Unit,
        lineColor: String = "#374151",
        removeDescription: String = context.getString(R.string.home_remove_item),
        onDetails: (() -> Unit)? = null,
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
                addView(detailButton(it, detailsLabel, detailsDescription))
            }
            addView(removeButton(onRemove, removeDescription))
        }

    private fun detailButton(
        onDetails: () -> Unit,
        label: String,
        description: String
    ): MaterialButton = MaterialButton(context).apply {
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
        setPadding(dp(8), 0, dp(8), 0)
        layoutParams = LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            dp(40)
        ).apply { leftMargin = dp(4) }
        setOnClickListener { onDetails() }
    }

    private fun removeButton(onRemove: () -> Unit, description: String): MaterialButton =
        MaterialButton(context).apply {
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
                            text = option.label
                            isCheckable = false
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
                    lineColor = "#92400E",
                    removeDescription =
                        context.getString(R.string.home_remove_item_description, token)
                )
            )
        }
        unknownCard.visibility = VISIBLE
    }

    private fun renderExtraStopSettings(
        state: PracticalShoppingHomeExtraStopSettingsRenderState
    ) {
        extraStopSettingsButton.visibility = if (state.visible) VISIBLE else GONE
        if (!state.visible) extraStopSettingsExpanded = false
        extraStopSettingsButton.text = state.summary
        extraStopSettingsBody.removeAllViews()
        extraStopSettingsBody.addView(line(state.prompt, 15f, "#111827", true))
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
                            text = option.label
                            isCheckable = true
                            isChecked = option.selected
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
        setOnClickListener { onCompare?.invoke() }
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
