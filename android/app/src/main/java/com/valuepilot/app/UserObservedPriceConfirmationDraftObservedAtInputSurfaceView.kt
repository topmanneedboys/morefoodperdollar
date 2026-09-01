package com.valuepilot.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Replaceable physical adapter for explicit observed-at date, time, and UTC-offset entry.
 *
 * This view starts hidden and never decides route visibility. It owns only raw text controls,
 * delegates interpretation to [UserObservedPriceConfirmationDraftObservedAtTextInputAdapter], and
 * emits exact epoch milliseconds only after that adapter succeeds. It does not read the device
 * clock or timezone, infer an offset from store/location/locale, generate identifiers, mutate a
 * draft/session, capture or persist proof, submit a confirmation, create evidence, or authorize
 * current-price semantics.
 */
class UserObservedPriceConfirmationDraftObservedAtInputSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dateEditor = EditText(context)
    private val timeEditor = EditText(context)
    private val utcOffsetEditor = EditText(context)
    private val applyButton = Button(context)

    var onCommit: ((Long) -> Unit)? = null
        set(value) {
            field = value
            applyButton.isEnabled = value != null
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false

        addView(heading("When did you observe this price?"))
        addView(
            helperText(
                "Enter the factual local date and time of the observation plus its UTC offset. " +
                    "ValuePilot does not use your device clock or timezone to fill this in."
            )
        )

        dateEditor.apply {
            hint = "Date: YYYY-MM-DD"
            isSingleLine = true
            isSaveEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(10))
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
        }
        addView(dateEditor)

        timeEditor.apply {
            hint = "Time: HH:MM or HH:MM:SS"
            isSingleLine = true
            isSaveEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(8))
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
        }
        addView(timeEditor)

        utcOffsetEditor.apply {
            hint = "UTC offset: for example -04:00 or Z"
            isSingleLine = true
            isSaveEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters = arrayOf<InputFilter>(InputFilter.LengthFilter(6))
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
        }
        addView(utcOffsetEditor)

        addView(
            helperText(
                "The offset must be explicit so the same observation means the same instant on every device."
            )
        )

        applyButton.apply {
            text = "Set observed time"
            setAllCaps(false)
            isSaveEnabled = false
            isEnabled = false
            setOnClickListener { commitInput() }
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
        }
        addView(applyButton)
    }

    fun clearInput() {
        dateEditor.setText("")
        timeEditor.setText("")
        utcOffsetEditor.setText("")
        dateEditor.error = null
        timeEditor.error = null
        utcOffsetEditor.error = null
    }

    private fun commitInput() {
        when (
            val result =
                UserObservedPriceConfirmationDraftObservedAtTextInputAdapter.adapt(
                    UserObservedPriceConfirmationDraftObservedAtTextInput(
                        dateText = dateEditor.text.toString(),
                        timeText = timeEditor.text.toString(),
                        utcOffsetText = utcOffsetEditor.text.toString()
                    )
                )
        ) {
            is UserObservedPriceConfirmationDraftObservedAtTextInputResult.Success -> {
                clearErrors()
                onCommit?.invoke(result.observedAtEpochMillis)
            }

            is UserObservedPriceConfirmationDraftObservedAtTextInputResult.Failure -> {
                showInputError(result.reason)
            }
        }
    }

    private fun showInputError(
        reason: UserObservedPriceConfirmationDraftObservedAtTextInputFailure
    ) {
        clearErrors()
        when (reason) {
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_DATE ->
                dateEditor.error = "Enter the observation date."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_TIME ->
                timeEditor.error = "Enter the observation time."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.BLANK_UTC_OFFSET ->
                utcOffsetEditor.error = "Enter the UTC offset."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_DATE_FORMAT ->
                dateEditor.error = "Use YYYY-MM-DD."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_TIME_FORMAT ->
                timeEditor.error = "Use HH:MM or HH:MM:SS in 24-hour time."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_UTC_OFFSET ->
                utcOffsetEditor.error = "Use Z or an explicit offset such as -04:00."
            UserObservedPriceConfirmationDraftObservedAtTextInputFailure.INVALID_DATE_TIME ->
                dateEditor.error = "Enter a real calendar date and time."
        }
    }

    private fun clearErrors() {
        dateEditor.error = null
        timeEditor.error = null
        utcOffsetEditor.error = null
    }

    private fun heading(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun helperText(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(0, dp(5), 0, 0)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
