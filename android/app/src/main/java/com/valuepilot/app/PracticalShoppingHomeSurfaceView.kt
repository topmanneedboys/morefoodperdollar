package com.valuepilot.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
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
    var onChickenChoice: ((LocalSamplePracticalShoppingDemo.ChickenChoice) -> Unit)? = null
    var onExtraStopMinimumSavingsChoice:
        ((LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice) -> Unit)? = null
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

    private val resultContainer = bareColumn()
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

        addView(
            TextInputLayout(context).apply {
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
        )
        addView(submitButton)
        addView(message)
        addView(itemsHeading)
        addView(itemsContainer)
        addView(refinementCard)
        addView(unknownCard)
        addView(resultContainer)
        addView(extraStopSettingsButton)
        addView(extraStopSettingsCard)
        addView(sampleCard())
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
        syncQuery(state.query)
        submitButton.isEnabled = state.submitEnabled
        renderMessage(state)
        renderItems(state.items)
        renderRefinement(state.refinement)
        renderUnknown(state.unknownItems)
        renderResult(state.result)
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
            itemsContainer.addView(
                line("${item.name}  •  ${item.detail}", 14f, "#374151", topPadding = 7)
            )
        }
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
        items.forEach { unknownBody.addView(line("• $it", 14f, "#92400E", topPadding = 5)) }
        unknownCard.visibility = VISIBLE
    }

    private fun renderResult(result: PracticalShoppingUiState?) {
        resultContainer.removeAllViews()
        if (result == null) return

        resultContainer.addView(line(result.headline, 22f, "#111827", true, 24))
        result.primary?.let { resultContainer.addView(primaryCard(it)) }
        result.secondStop?.let { resultContainer.addView(secondStopCard(it)) }
        result.secondaryMessage?.let {
            resultContainer.addView(line(it, 13f, "#6B7280", topPadding = 12))
        }
    }

    private fun renderExtraStopSettings(
        state: PracticalShoppingHomeExtraStopSettingsRenderState
    ) {
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
        extraStopSettingsCard.visibility = if (extraStopSettingsExpanded) VISIBLE else GONE
    }

    private fun primaryCard(state: PracticalShoppingPrimaryUiState): View =
        card("#ECFDF5", "#A7F3D0", 12).apply {
            addView(
                column().apply {
                    addView(line(state.badge, 11f, "#047857", true))
                    addView(line(state.storeName, 22f, "#111827", true, 6))
                    addView(line(state.basketCostText, 18f, "#111827", true, 8))
                    addView(line("${state.coverageText}  •  ${state.travelText}", 14f, "#374151", topPadding = 6))
                    state.missingItemsText?.let {
                        addView(line(it, 13f, "#92400E", true, 8))
                    }
                    addView(line(state.evidenceText, 12f, "#6B7280", topPadding = 5))
                    addView(line(state.whyText, 13f, "#374151", topPadding = 9))
                    state.notice?.let { addView(line(it, 12f, "#92400E", topPadding = 7)) }
                }
            )
        }

    private fun secondStopCard(state: PracticalShoppingSecondStopUiState): View =
        card("#FFFFFF", "#D1FAE5", 12).apply {
            addView(
                column().apply {
                    addView(line(state.badge, 11f, "#047857", true))
                    addView(line(state.storeName, 18f, "#111827", true, 6))
                    addView(line(state.savingsText, 17f, "#047857", true, 7))
                    addView(line(state.combinedBasketCostText, 14f, "#374151", topPadding = 5))
                    addView(line(state.additionalTravelText, 13f, "#374151", topPadding = 4))
                    addView(line(state.evidenceText, 12f, "#6B7280", topPadding = 5))
                }
            )
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
