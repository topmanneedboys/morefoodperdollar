package com.valuepilot.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Replaceable physical renderer for one explicit Staple Watch policy draft.
 *
 * The view receives only [StapleWatchPolicyDraftUiState]. Raw text is converted to the already-
 * defined typed consumer actions only through [StapleWatchPolicyDraftTextInputAdapter]. The view
 * never mutates a draft, constructs policy, resolves evidence, chooses defaults, evaluates store
 * economics, persists setup, schedules work, or authorizes notifications.
 *
 * Route visibility is external to [render]. This view starts GONE and render never makes it visible.
 * Continuation is likewise owner-controlled: the already-projected continuation action is rendered
 * only when an external owner installs [onContinueAction].
 */
class StapleWatchPolicyDraftSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), StapleWatchPolicyDraftSurfaceRenderer {

    var onAction: ((StapleWatchPolicyDraftUiAction) -> Unit)? = null
    var onContinueAction: ((StapleWatchPolicyHandoffUiAction) -> Unit)? = null

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    override fun render(state: StapleWatchPolicyDraftUiState) {
        removeAllViews()

        addView(heading(state.headline))
        addView(guidance(state.guidance))
        state.notice?.let { message -> addView(notice(message)) }

        addView(
            numericField(
                state = state,
                label = state.minimumSwitchSavingsLabel,
                unitLabel = state.minimumSwitchSavingsUnitLabel,
                value =
                    StapleWatchPolicyDraftTextValueFormatter.money(
                        minorUnits = state.minimumSwitchSavingsMinorUnits,
                        fractionDigits = state.currencyFractionDigits
                    ),
                decimal = true,
                input = StapleWatchPolicyDraftTextInput::MinimumSwitchSavings
            )
        )
        addView(
            numericField(
                state = state,
                label = state.maxAdditionalTravelLabel,
                unitLabel = state.maxAdditionalTravelUnitLabel,
                value = StapleWatchPolicyDraftTextValueFormatter.whole(state.maxAdditionalTravelSeconds),
                decimal = false,
                input = StapleWatchPolicyDraftTextInput::MaxAdditionalTravelSeconds
            )
        )
        addView(distanceField(state))
        addView(
            numericField(
                state = state,
                label = state.minimumStapleItemCountLabel,
                unitLabel = "items",
                value = StapleWatchPolicyDraftTextValueFormatter.whole(state.minimumStapleItemCount),
                decimal = false,
                input = StapleWatchPolicyDraftTextInput::MinimumStapleItemCount
            )
        )

        state.continueAction
            ?.takeIf { onContinueAction != null }
            ?.let { action ->
                addView(
                    continuationButton(
                        label = requireNotNull(state.continueActionLabel),
                        action = action
                    )
                )
            }
    }

    private fun distanceField(state: StapleWatchPolicyDraftUiState): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(18), 0, 0)

            addView(fieldLabel(state.distanceLimitLabel))
            addView(
                helperText(
                    when (state.distanceLimitMode) {
                        StapleWatchPolicyDistanceLimitUiMode.UNANSWERED ->
                            "Choose a distance limit or no distance limit."
                        StapleWatchPolicyDistanceLimitUiMode.UNLIMITED ->
                            "No distance limit selected."
                        StapleWatchPolicyDistanceLimitUiMode.AT_MOST_METRES ->
                            "Distance limit selected."
                    }
                )
            )

            val limitedValue =
                if (state.distanceLimitMode == StapleWatchPolicyDistanceLimitUiMode.AT_MOST_METRES) {
                    StapleWatchPolicyDraftTextValueFormatter.whole(
                        state.maxAdditionalDistanceMetres
                    )
                } else {
                    ""
                }
            addView(
                numericInputControl(
                    state = state,
                    unitLabel = state.maxAdditionalDistanceUnitLabel,
                    value = limitedValue,
                    decimal = false,
                    input = StapleWatchPolicyDraftTextInput::MaxAdditionalDistanceMetres
                )
            )
            addView(
                Button(context).apply {
                    text = "No distance limit"
                    setAllCaps(false)
                    isSaveEnabled = false
                    isEnabled = onAction != null
                    setOnClickListener {
                        onAction?.invoke(StapleWatchPolicyDraftUiAction.SetDistanceUnlimited)
                    }
                    layoutParams =
                        LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(8) }
                }
            )
        }

    private fun numericField(
        state: StapleWatchPolicyDraftUiState,
        label: String,
        unitLabel: String,
        value: String,
        decimal: Boolean,
        input: (String) -> StapleWatchPolicyDraftTextInput
    ): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(0, dp(18), 0, 0)
            addView(fieldLabel(label))
            addView(
                numericInputControl(
                    state = state,
                    unitLabel = unitLabel,
                    value = value,
                    decimal = decimal,
                    input = input
                )
            )
        }

    private fun numericInputControl(
        state: StapleWatchPolicyDraftUiState,
        unitLabel: String,
        value: String,
        decimal: Boolean,
        input: (String) -> StapleWatchPolicyDraftTextInput
    ): View {
        val container =
            LinearLayout(context).apply {
                orientation = VERTICAL
            }
        val editor =
            EditText(context).apply {
                setText(value)
                hint = unitLabel
                isSingleLine = true
                isSaveEnabled = false
                inputType =
                    InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_FLAG_SIGNED or
                        (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
            }
        val applyButton =
            Button(context).apply {
                text = "Apply"
                setAllCaps(false)
                isSaveEnabled = false
                isEnabled = onAction != null
                setOnClickListener {
                    when (
                        val result =
                            StapleWatchPolicyDraftTextInputAdapter.adapt(
                                state = state,
                                input = input(editor.text.toString())
                            )
                    ) {
                        is StapleWatchPolicyDraftTextInputResult.Success -> {
                            editor.error = null
                            onAction?.invoke(result.action)
                        }
                        is StapleWatchPolicyDraftTextInputResult.Failure -> {
                            editor.error = inputError(result.reason)
                        }
                    }
                }
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(6) }
            }

        container.addView(editor)
        container.addView(helperText(unitLabel))
        container.addView(applyButton)
        return container
    }

    private fun continuationButton(
        label: String,
        action: StapleWatchPolicyHandoffUiAction
    ): Button =
        Button(context).apply {
            text = label
            setAllCaps(false)
            isSaveEnabled = false
            isEnabled = onContinueAction != null
            setOnClickListener { onContinueAction?.invoke(action) }
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(18) }
        }

    private fun inputError(reason: StapleWatchPolicyDraftTextInputFailure): String =
        when (reason) {
            StapleWatchPolicyDraftTextInputFailure.BLANK -> "Enter a value."
            StapleWatchPolicyDraftTextInputFailure.INVALID_FORMAT -> "Enter a valid number."
            StapleWatchPolicyDraftTextInputFailure.OUT_OF_RANGE -> "That number is too large."
        }

    private fun heading(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun guidance(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#4B5563"))
            setPadding(0, dp(8), 0, 0)
        }

    private fun notice(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#92400E"))
            setPadding(0, dp(10), 0, 0)
        }

    private fun fieldLabel(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun helperText(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(4), 0, 0)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

/** Exact locale-neutral text formatting for already-projected policy values. */
internal object StapleWatchPolicyDraftTextValueFormatter {
    fun money(minorUnits: Long?, fractionDigits: Int): String {
        if (minorUnits == null) return ""
        require(fractionDigits in 0..6)
        if (fractionDigits == 0) return minorUnits.toString()

        val signed = minorUnits.toString()
        val negative = signed.startsWith('-')
        val digits = if (negative) signed.substring(1) else signed
        val padded = digits.padStart(fractionDigits + 1, '0')
        val split = padded.length - fractionDigits
        return buildString {
            if (negative) append('-')
            append(padded.substring(0, split))
            append('.')
            append(padded.substring(split))
        }
    }

    fun whole(value: Long?): String = value?.toString().orEmpty()

    fun whole(value: Int?): String = value?.toString().orEmpty()
}
