package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

internal sealed interface TripPlannerUiAction {
    data object ChooseDestinationStore : TripPlannerUiAction
    data object SetManualOrigin : TripPlannerUiAction
    data object UseForegroundLocation : TripPlannerUiAction
    data object EditTripAssumptions : TripPlannerUiAction
    data object Calculate : TripPlannerUiAction
    data object OpenDirections : TripPlannerUiAction
    data object TogglePreferredStore : TripPlannerUiAction
    data object Clear : TripPlannerUiAction
}

/** Presentation-only Trip surface. It owns layout and taps, never trip or decision authority. */
internal class TripPlannerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headline = textLine("", 24f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true)
    private val guidance = textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 8)
    private val basketSummary = textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 14)
    private val storeSummary = textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 4)

    private val tripDetailsCard = card().apply {
        addView(cardColumn().apply {
            addView(label("Trip details"))
            addView(textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 5))
            addView(ValuePilotProgrammaticButton.outlined(context).apply {
                text = "Edit"
                contentDescription = "Edit distance, fuel use, fuel price, tolls and parking"
                layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.XS_DP)
                setOnClickListener { onAction?.invoke(TripPlannerUiAction.EditTripAssumptions) }
                tag = "edit-trip-details"
            })
        })
    }
    private val tripDetailsBody = tripDetailsCard.getChildAt(0) as LinearLayout
    private val tripDetailsSummary = tripDetailsBody.getChildAt(1) as TextView
    private val editTripDetailsButton = tripDetailsBody.getChildAt(2) as MaterialButton

    private val tripCostCard = card().apply {
        addView(cardColumn().apply {
            addView(label("Trip cost"))
            addView(textLine("", 30f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true, top = 5))
            addView(textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 4))
        })
    }
    private val tripCostBody = tripCostCard.getChildAt(0) as LinearLayout
    private val tripCostAmount = tripCostBody.getChildAt(1) as TextView
    private val tripCostSupport = tripCostBody.getChildAt(2) as TextView

    private val breakEvenCard = card().apply {
        addView(cardColumn().apply {
            addView(label("Save at least"))
            addView(textLine("", 24f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true, top = 5))
            addView(textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 4))
        })
    }
    private val breakEvenBody = breakEvenCard.getChildAt(0) as LinearLayout
    private val saveAtLeastAmount = breakEvenBody.getChildAt(1) as TextView
    private val saveAtLeastSupport = breakEvenBody.getChildAt(2) as TextView

    private val savingsCard = card().apply {
        addView(cardColumn().apply {
            addView(label("Current savings"))
            addView(textLine("", 22f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true, top = 5))
            addView(textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 4))
        })
    }
    private val savingsBody = savingsCard.getChildAt(0) as LinearLayout
    private val currentSavingsAmount = savingsBody.getChildAt(1) as TextView
    private val currentSavingsSupport = savingsBody.getChildAt(2) as TextView

    private val decisionCard = card().apply {
        addView(cardColumn().apply {
            addView(label("For this decision"))
            addView(textLine("", 19f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true, top = 5))
            addView(textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 4))
        })
    }
    private val decisionBody = decisionCard.getChildAt(0) as LinearLayout
    private val decisionHeadline = decisionBody.getChildAt(1) as TextView
    private val decisionDetail = decisionBody.getChildAt(2) as TextView

    private val detailsButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = "Why?"
        contentDescription = "Show the trip details and price context"
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.SM_DP)
        setOnClickListener { setDetailsExpanded(!detailsExpanded) }
    }
    private val detailsContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        visibility = GONE
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.XS_DP)
    }
    private val primaryButton = ValuePilotProgrammaticButton.filled(context).apply {
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.LG_DP)
    }
    private val moreActionsButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = "More trip actions"
        contentDescription = "Show store, starting point, directions and trip actions"
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.XS_DP)
        setOnClickListener { setActionsExpanded(!actionsExpanded) }
    }
    private val actionsContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        visibility = GONE
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.XS_DP)
    }
    private val actionButtons = mutableListOf<MaterialButton>()
    private val chooseDestinationButton = outlinedAction(
        "Choose store",
        "Choose the store for this trip",
        TripPlannerUiAction.ChooseDestinationStore
    )
    private val setOriginButton = outlinedAction(
        "Add starting point",
        "Enter a private starting point for this trip",
        TripPlannerUiAction.SetManualOrigin
    )
    private val locationButton = outlinedAction(
        "Use my location once",
        "Use one foreground location reading for this trip; no background tracking",
        TripPlannerUiAction.UseForegroundLocation
    )
    private val assumptionsButton = outlinedAction(
        "Edit trip details",
        "Edit distance, fuel use, fuel price, tolls and parking",
        TripPlannerUiAction.EditTripAssumptions
    )
    private val preferredButton = outlinedAction(
        "",
        "Save or remove this store as a preferred store",
        TripPlannerUiAction.TogglePreferredStore
    )
    private val directionsButton = outlinedAction(
        "Directions",
        "Open an external directions app for this store",
        TripPlannerUiAction.OpenDirections
    )
    private val clearButton = outlinedAction(
        "Clear trip",
        "Clear the saved trip details without changing Basket or purchase history",
        TripPlannerUiAction.Clear,
        destructive = true
    )
    private var detailsExpanded = false
    private var actionsExpanded = false
    private var hasRenderedState = false
    private var primaryAction: TripPlannerUiAction? = null

    var onAction: ((TripPlannerUiAction) -> Unit)? = null
        set(value) {
            field = value
            primaryButton.isEnabled = value != null && primaryAction != null
            editTripDetailsButton.isEnabled = value != null
            moreActionsButton.isEnabled = value != null
            actionButtons.forEach { it.isEnabled = value != null }
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
        addView(headline)
        addView(guidance)
        addView(basketSummary)
        addView(storeSummary)
        addView(tripDetailsCard)
        addView(tripCostCard)
        addView(breakEvenCard)
        addView(savingsCard)
        addView(decisionCard)
        addView(detailsButton)
        addView(detailsContainer)
        addView(primaryButton)
        addView(moreActionsButton)
        addView(actionsContainer)
        actionsContainer.addView(chooseDestinationButton)
        actionsContainer.addView(setOriginButton)
        actionsContainer.addView(locationButton)
        actionsContainer.addView(assumptionsButton)
        actionsContainer.addView(preferredButton)
        actionsContainer.addView(directionsButton)
        actionsContainer.addView(clearButton)
        actionButtons += chooseDestinationButton
        actionButtons += setOriginButton
        actionButtons += locationButton
        actionButtons += editTripDetailsButton
        actionButtons += assumptionsButton
        actionButtons += preferredButton
        actionButtons += directionsButton
        actionButtons += clearButton
    }

    fun render(state: TripPlannerSurfaceState, preferred: Boolean) {
        headline.text = state.headline
        headline.contentDescription = state.headline
        guidance.text = state.guidance
        basketSummary.text = state.basketSummary
        basketSummary.contentDescription = state.basketSummary
        storeSummary.text = state.storeSummary
        storeSummary.contentDescription = state.storeSummary
        tripDetailsSummary.text = state.assumptionsSummary
        tripDetailsSummary.contentDescription = state.assumptionsSummary
        editTripDetailsButton.isEnabled = onAction != null && state.mode != TripPlannerSurfaceMode.UNAVAILABLE
        tripCostAmount.text = state.tripCostAmount
        tripCostAmount.contentDescription = "Trip cost: ${state.tripCostAmount}"
        tripCostSupport.text = state.tripCostSupport
        tripCostSupport.contentDescription = state.tripCostSupport
        saveAtLeastAmount.text = state.saveAtLeastAmount
        saveAtLeastAmount.contentDescription = "Save at least: ${state.saveAtLeastAmount}"
        saveAtLeastSupport.text = state.saveAtLeastSupport
        currentSavingsAmount.text = state.currentSavingsAmount
        currentSavingsAmount.contentDescription = "Current savings: ${state.currentSavingsAmount}"
        currentSavingsSupport.text = state.currentSavingsSupport
        decisionHeadline.text = state.decisionHeadline
        decisionHeadline.setTextColor(ValuePilotPalette.color(decisionColor(state.decisionTone)))
        decisionHeadline.contentDescription = state.decisionHeadline
        decisionDetail.text = state.decisionDetail
        renderDetails(state.detailLines)

        primaryAction = primaryActionFor(state)
        primaryButton.visibility = if (primaryAction == null) GONE else VISIBLE
        primaryButton.text = primaryLabelFor(state)
        primaryButton.contentDescription = primaryDescriptionFor(state)
        primaryButton.isEnabled = onAction != null && primaryAction != null
        primaryButton.setOnClickListener { primaryAction?.let { onAction?.invoke(it) } }
        preferredButton.text = if (preferred) "Remove preferred store" else "Save this store"
        directionsButton.visibility = if (state.directionsAvailable) VISIBLE else GONE
        moreActionsButton.visibility = if (state.mode == TripPlannerSurfaceMode.UNAVAILABLE) GONE else VISIBLE
        detailsButton.visibility = if (state.mode == TripPlannerSurfaceMode.UNAVAILABLE) GONE else VISIBLE
        moreActionsButton.isEnabled = onAction != null
        actionButtons.forEach { it.isEnabled = onAction != null }

        if (!hasRenderedState) {
            ValuePilotMotion.reveal(tripCostCard, animate = false)
            ValuePilotMotion.reveal(decisionCard, animate = false)
            hasRenderedState = true
        } else {
            ValuePilotMotion.reveal(tripCostCard)
            ValuePilotMotion.reveal(decisionCard)
        }
    }

    private fun renderDetails(lines: List<String>) {
        detailsContainer.removeAllViews()
        lines.forEach { detailsContainer.addView(textLine(it, 13f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 6)) }
    }

    private fun setDetailsExpanded(expanded: Boolean) {
        detailsExpanded = expanded
        detailsContainer.visibility = if (expanded) VISIBLE else GONE
        detailsButton.text = if (expanded) "Hide details" else "Why?"
        ValuePilotMotion.reveal(detailsContainer, animate = expanded)
    }

    private fun setActionsExpanded(expanded: Boolean) {
        actionsExpanded = expanded
        actionsContainer.visibility = if (expanded) VISIBLE else GONE
        moreActionsButton.text = if (expanded) "Hide trip actions" else "More trip actions"
        ValuePilotMotion.reveal(actionsContainer, animate = expanded)
    }

    private fun primaryActionFor(state: TripPlannerSurfaceState): TripPlannerUiAction? =
        when {
            state.mode == TripPlannerSurfaceMode.UNAVAILABLE ||
                state.mode == TripPlannerSurfaceMode.EMPTY_BASKET ||
                state.mode == TripPlannerSurfaceMode.CALCULATED -> null
            !state.destinationReady -> TripPlannerUiAction.ChooseDestinationStore
            !state.originReady -> TripPlannerUiAction.SetManualOrigin
            !state.assumptionsReady -> TripPlannerUiAction.EditTripAssumptions
            else -> TripPlannerUiAction.Calculate
        }

    private fun primaryLabelFor(state: TripPlannerSurfaceState): String =
        when (primaryActionFor(state)) {
            TripPlannerUiAction.ChooseDestinationStore -> "Choose store"
            TripPlannerUiAction.SetManualOrigin -> "Add starting point"
            TripPlannerUiAction.EditTripAssumptions -> "Add trip details"
            TripPlannerUiAction.Calculate -> "Calculate trip cost"
            null -> if (state.mode == TripPlannerSurfaceMode.EMPTY_BASKET) "Add items in Basket first" else "Trip unavailable"
            else -> "Continue"
        }

    private fun primaryDescriptionFor(state: TripPlannerSurfaceState): String =
        when (primaryActionFor(state)) {
            TripPlannerUiAction.ChooseDestinationStore -> "Choose the store for this trip"
            TripPlannerUiAction.SetManualOrigin -> "Add a private starting point for this trip"
            TripPlannerUiAction.EditTripAssumptions -> "Add distance, fuel use, fuel price and optional costs"
            TripPlannerUiAction.Calculate -> "Calculate the exact trip cost from your saved details"
            null -> if (state.mode == TripPlannerSurfaceMode.EMPTY_BASKET) "Add items to Basket before planning a trip" else "Trip details are unavailable"
            else -> "Continue this trip"
        }

    private fun outlinedAction(
        label: String,
        description: String,
        action: TripPlannerUiAction,
        destructive: Boolean = false
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context, destructive = destructive).apply {
        text = label
        contentDescription = description
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.XS_DP)
        setOnClickListener { onAction?.invoke(action) }
    }

    private fun card(): ValuePilotProductCard = ValuePilotProductCard(context).apply {
        setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
        strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
        layoutParams = fullWidth(ValuePilotDesignTokens.Spacing.SM_DP)
    }

    private fun cardColumn(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
    }

    private fun label(value: String): TextView =
        textLine(value, 13f, ValuePilotDesignTokens.Colors.TEXT_MUTED, bold = true)

    private fun textLine(
        value: String,
        size: Float,
        color: String,
        bold: Boolean = false,
        top: Int = 0
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(ValuePilotPalette.color(color))
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        if (top > 0) setPadding(0, dp(top), 0, 0)
    }

    private fun fullWidth(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
        }

    private fun decisionColor(tone: TripPlannerDecisionTone): String =
        when (tone) {
            TripPlannerDecisionTone.POSITIVE -> ValuePilotDesignTokens.Colors.SUCCESS
            TripPlannerDecisionTone.WARNING -> ValuePilotDesignTokens.Colors.WARNING
            TripPlannerDecisionTone.NEUTRAL,
            TripPlannerDecisionTone.UNKNOWN -> ValuePilotDesignTokens.Colors.TEXT_PRIMARY
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
