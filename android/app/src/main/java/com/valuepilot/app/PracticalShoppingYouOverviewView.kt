package com.valuepilot.app

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import android.content.res.ColorStateList

/**
 * Compact personal hub for the You tab. Counts are bounded summaries only;
 * the existing Bought, Saved and Watching routes remain the owners of their
 * product facts.
 */
internal class PracticalShoppingYouOverviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headingView = textLine("You", 24f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, true)
    private val privacyChip = ValuePilotCompactUi.statusChip(context, "Private on this device").apply {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        }
        contentDescription = "Private shopping memory stays on this device"
    }
    private val selector = LinearLayout(context).apply {
        orientation = HORIZONTAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        }
    }
    private val selectedSummary = LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        }
    }
    private val selectedTitle = textLine("", 18f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, true)
    private val selectedSupporting = textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 4)
    private val actionButtons = mutableListOf<MaterialButton>()
    private val settingsButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        text = "Settings"
        contentDescription = "Open Settings"
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        }
    }

    private var selected = Collection.BOUGHT

    var onAction: ((PracticalShoppingYouOverviewAction) -> Unit)? = null
        set(value) {
            field = value
            actionButtons.forEach { it.isEnabled = value != null }
            settingsButton.isEnabled = value != null
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
        addView(headingView)
        addView(privacyChip)
        addView(selector)
        selectedSummary.addView(selectedTitle)
        selectedSummary.addView(selectedSupporting)
        addView(selectedSummary)
        settingsButton.setOnClickListener { onAction?.invoke(PracticalShoppingYouOverviewAction.OpenSettings) }
        addView(settingsButton)
    }

    fun render(state: PracticalShoppingYouOverviewState) {
        actionButtons.clear()
        if (selected == Collection.BOUGHT && state.boughtCount == 0 && state.savedCount > 0) {
            selected = Collection.SAVED
        }
        settingsButton.isEnabled = onAction != null
        selector.removeAllViews()
        listOf(
            segment("Bought", state.boughtCount, Collection.BOUGHT, PracticalShoppingYouOverviewAction.OpenBought),
            segment("Saved", state.savedCount, Collection.SAVED, PracticalShoppingYouOverviewAction.OpenSaved),
            segment("Watching", state.watchingCount, Collection.WATCHING, PracticalShoppingYouOverviewAction.OpenWatching)
        ).forEach {
            actionButtons += it
            selector.addView(it)
        }
        val title: String
        val count: Int
        val empty: String
        val action: PracticalShoppingYouOverviewAction
        val description: String
        when (selected) {
            Collection.BOUGHT -> {
                title = "Bought"
                count = state.boughtCount
                empty = "Nothing bought yet"
                action = PracticalShoppingYouOverviewAction.OpenBought
                description = "Open your confirmed purchases"
            }
            Collection.SAVED -> {
                title = "Saved"
                count = state.savedCount
                empty = "Nothing saved yet"
                action = PracticalShoppingYouOverviewAction.OpenSaved
                description = "Review your saved products"
            }
            Collection.WATCHING -> {
                title = "Watching"
                count = state.watchingCount
                empty = "Nothing watched yet"
                action = PracticalShoppingYouOverviewAction.OpenWatching
                description = "Open your private price watches"
            }
        }
        selectedTitle.text = title
        selectedSupporting.text = if (count == 0) empty else "$count item${if (count == 1) "" else "s"}"
        val actionButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
            text = when (selected) {
                Collection.BOUGHT -> "Open Bought"
                Collection.SAVED -> "Review Saved"
                Collection.WATCHING -> "Open Watching"
            }
            contentDescription = description
            isEnabled = onAction != null
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
            setOnClickListener { onAction?.invoke(action) }
        }
        actionButtons += actionButton
        selectedSummary.removeViews(2, selectedSummary.childCount - 2)
        selectedSummary.addView(actionButton)
    }

    private fun segment(
        label: String,
        count: Int,
        collection: Collection,
        action: PracticalShoppingYouOverviewAction
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        text = "$label $count"
        contentDescription = "$label, $count item${if (count == 1) "" else "s"}"
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(4)
        }
        isEnabled = onAction != null
        setOnClickListener {
            selected = collection
            onAction?.invoke(action)
        }
        if (selected == collection) {
            backgroundTintList = ColorStateList.valueOf(
                ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE_ACCENT)
            )
            strokeColor = ColorStateList.valueOf(
                ValuePilotPalette.color(ValuePilotDesignTokens.Colors.PRIMARY)
            )
        }
    }

    private enum class Collection { BOUGHT, SAVED, WATCHING }

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
