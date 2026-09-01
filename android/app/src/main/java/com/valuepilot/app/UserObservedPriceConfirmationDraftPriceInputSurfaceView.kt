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
import com.valuepilot.core.Money

/**
 * Replaceable physical adapter for explicit observed-price amount and currency entry.
 *
 * This view starts hidden and never decides route visibility. It owns only raw text controls,
 * delegates text interpretation to [UserObservedPriceConfirmationDraftPriceTextInputAdapter], and
 * emits an exact [Money] only after that adapter succeeds. It does not receive or inspect the draft,
 * infer currency from store/device/locale, capture proof, generate identifiers/timestamps, submit a
 * confirmation, persist anything, create evidence, or authorize current-price semantics.
 */
class UserObservedPriceConfirmationDraftPriceInputSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val amountEditor = EditText(context)
    private val currencyEditor = EditText(context)
    private val applyButton = Button(context)

    var onCommit: ((Money) -> Unit)? = null
        set(value) {
            field = value
            applyButton.isEnabled = value != null
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false

        addView(heading("Enter observed price"))
        addView(
            helperText(
                "Enter the amount you personally observed and its ISO currency code. " +
                    "ValuePilot does not infer currency from the store or your device."
            )
        )

        amountEditor.apply {
            hint = "Amount, for example 5.99"
            isSingleLine = true
            isSaveEnabled = false
            inputType =
                InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_SIGNED or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
        }
        addView(amountEditor)

        currencyEditor.apply {
            hint = "Currency code, for example CAD"
            isSingleLine = true
            isSaveEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(3))
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
        }
        addView(currencyEditor)

        addView(
            helperText(
                "Use an uppercase ISO currency code. Decimal precision follows that explicit currency."
            )
        )

        applyButton.apply {
            text = "Set observed price"
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
        amountEditor.setText("")
        currencyEditor.setText("")
        amountEditor.error = null
        currencyEditor.error = null
    }

    private fun commitInput() {
        when (
            val result =
                UserObservedPriceConfirmationDraftPriceTextInputAdapter.adapt(
                    UserObservedPriceConfirmationDraftPriceTextInput(
                        amountText = amountEditor.text.toString(),
                        currencyCodeText = currencyEditor.text.toString()
                    )
                )
        ) {
            is UserObservedPriceConfirmationDraftPriceTextInputResult.Success -> {
                amountEditor.error = null
                currencyEditor.error = null
                onCommit?.invoke(result.price)
            }

            is UserObservedPriceConfirmationDraftPriceTextInputResult.Failure -> {
                showInputError(result.reason)
            }
        }
    }

    private fun showInputError(
        reason: UserObservedPriceConfirmationDraftPriceTextInputFailure
    ) {
        amountEditor.error = null
        currencyEditor.error = null
        when (reason) {
            UserObservedPriceConfirmationDraftPriceTextInputFailure.BLANK_AMOUNT ->
                amountEditor.error = "Enter an observed amount."
            UserObservedPriceConfirmationDraftPriceTextInputFailure.BLANK_CURRENCY ->
                currencyEditor.error = "Enter a currency code."
            UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_CURRENCY_CODE ->
                currencyEditor.error = "Enter a valid uppercase ISO currency code."
            UserObservedPriceConfirmationDraftPriceTextInputFailure.UNSUPPORTED_CURRENCY_PRECISION ->
                currencyEditor.error = "That currency precision is not supported."
            UserObservedPriceConfirmationDraftPriceTextInputFailure.INVALID_AMOUNT_FORMAT ->
                amountEditor.error = "Enter the amount using digits and a decimal point only."
            UserObservedPriceConfirmationDraftPriceTextInputFailure.OUT_OF_RANGE ->
                amountEditor.error = "That amount is too large."
        }
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
