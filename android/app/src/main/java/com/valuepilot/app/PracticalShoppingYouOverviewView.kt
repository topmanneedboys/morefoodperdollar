package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * Calm first viewport for the You tab. This is deliberately a renderer only: it receives
 * bounded counts and emits typed route intents, while the shell and existing projectors keep
 * ownership of personal data and business rules.
 */
internal class PracticalShoppingYouOverviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headingView = textLine(
        value = "You",
        sizeSp = 22f,
        color = ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        bold = true
    )
    private val bodyView = textLine(
        value = "Your private shopping memory, kept on this device.",
        sizeSp = 13f,
        color = ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = 5
    )
    private val cards = LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
    private val actionButtons = mutableListOf<MaterialButton>()

    var onAction: ((PracticalShoppingYouOverviewAction) -> Unit)? = null
        set(value) {
            field = value
            actionButtons.forEach { it.isEnabled = value != null }
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
        addView(headingView)
        addView(bodyView)
        addView(cards)
    }

    fun render(state: PracticalShoppingYouOverviewState) {
        actionButtons.clear()
        cards.removeAllViews()
        overviewCard(
            title = "Bought",
            count = state.boughtCount,
            emptyLabel = "Nothing bought yet",
            actionLabel = "Open Bought",
            actionDescription = "Open your confirmed purchases",
            action = PracticalShoppingYouOverviewAction.OpenBought
        ).let(cards::addView)
        overviewCard(
            title = "Saved",
            count = state.savedCount,
            emptyLabel = "Nothing saved yet",
            actionLabel = "Review Saved",
            actionDescription = "Review your saved products and stores",
            action = PracticalShoppingYouOverviewAction.OpenSaved
        ).let(cards::addView)
        overviewCard(
            title = "Watching",
            count = state.watchingCount,
            emptyLabel = "Nothing watched yet",
            actionLabel = "Open Watching",
            actionDescription = "Open your private price watches",
            action = PracticalShoppingYouOverviewAction.OpenWatching
        ).let(cards::addView)
    }

    private fun overviewCard(
        title: String,
        count: Int,
        emptyLabel: String,
        actionLabel: String,
        actionDescription: String,
        action: PracticalShoppingYouOverviewAction
    ): View {
        val card = ValuePilotProductCard(context).apply {
            setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
            strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
            contentDescription = "$title: ${if (count == 0) emptyLabel else "$count item${if (count == 1) "" else "s"}"}"
        }
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(13))
        }
        body.addView(
            textLine(
                value = title,
                sizeSp = 17f,
                color = ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
                bold = true
            )
        )
        body.addView(
            textLine(
                value = if (count == 0) emptyLabel else "$count",
                sizeSp = if (count == 0) 13f else 25f,
                color = if (count == 0) {
                    ValuePilotDesignTokens.Colors.TEXT_SECONDARY
                } else {
                    ValuePilotDesignTokens.Colors.PRIMARY_DARK
                },
                bold = count > 0,
                topPadding = 5
            )
        )
        val button = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
            text = actionLabel
            contentDescription = actionDescription
            isEnabled = onAction != null
            setOnClickListener { onAction?.invoke(action) }
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
        }
        actionButtons += button
        body.addView(button)
        card.addView(body)
        return card
    }

    private fun textLine(
        value: String,
        sizeSp: Float,
        color: String,
        bold: Boolean = false,
        topPadding: Int = 0
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(ValuePilotPalette.color(color))
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
