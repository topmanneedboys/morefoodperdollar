package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
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

/** Presentation-only Trip Planner surface; all facts and actions arrive from immutable state. */
internal class TripPlannerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val headline = textLine("", 22f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true)
    private val guidance = textLine("", 14f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 8)
    private val basketSummary = card().apply {
        addView(cardColumn().apply {
            addView(textLine("Basket coverage", 13f, ValuePilotDesignTokens.Colors.TEXT_MUTED, bold = true))
            addView(textLineView(""))
        })
    }
    private val basketSummaryText = (basketSummary.getChildAt(0) as LinearLayout).getChildAt(1) as TextView
    private val tripCard = card().apply {
        addView(cardColumn().apply {
            addView(textLine("Trip overview", 13f, ValuePilotDesignTokens.Colors.TEXT_MUTED, bold = true))
            addView(textLineView(""))
            addView(textLineView(""))
            addView(textLineView(""))
            addView(textLineView(""))
            addView(textLineView(""))
        })
    }
    private val tripCardBody = tripCard.getChildAt(0) as LinearLayout
    private val destinationText = tripCardBody.getChildAt(1) as TextView
    private val originText = tripCardBody.getChildAt(2) as TextView
    private val tripCostHeadline = tripCardBody.getChildAt(3) as TextView
    private val tripCostDetail = tripCardBody.getChildAt(4) as TextView
    private val breakEvenText = tripCardBody.getChildAt(5) as TextView
    private val evidenceCard = card().apply {
        addView(cardColumn().apply {
            addView(textLineView(""))
            addView(textLineView(""))
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                tag = "evidence-chips"
            })
        })
    }
    private val evidenceCardBody = evidenceCard.getChildAt(0) as LinearLayout
    private val decisionHeadline = evidenceCardBody.getChildAt(0) as TextView
    private val decisionDetail = evidenceCardBody.getChildAt(1) as TextView
    private val evidenceChips = evidenceCardBody.getChildAt(2) as LinearLayout
    private val detailsButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = "Why this answer"
        contentDescription = "Show the trip assumptions and shopping evidence details"
        layoutParams = fullWidth(top = ValuePilotDesignTokens.Spacing.SM_DP)
        setOnClickListener { setDetailsExpanded(!detailsExpanded) }
    }
    private val detailsContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        visibility = GONE
        layoutParams = fullWidth(top = ValuePilotDesignTokens.Spacing.XS_DP)
    }
    private val detailLines = mutableListOf<TextView>()
    private val primaryButton = ValuePilotProgrammaticButton.filled(context).apply {
        layoutParams = fullWidth(top = ValuePilotDesignTokens.Spacing.LG_DP)
    }
    private val moreActionsButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = "More trip actions"
        contentDescription = "Show store, origin, directions and trip actions"
        layoutParams = fullWidth(top = ValuePilotDesignTokens.Spacing.XS_DP)
        setOnClickListener { setActionsExpanded(!actionsExpanded) }
    }
    private val actionsContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        visibility = GONE
        layoutParams = fullWidth(top = ValuePilotDesignTokens.Spacing.XS_DP)
    }
    private val actionButtons = mutableListOf<MaterialButton>()
    private val chooseDestinationButton = outlinedAction(
        "Choose destination store",
        "Choose the exact destination store for this private trip scenario",
        TripPlannerUiAction.ChooseDestinationStore
    )
    private val setOriginButton = outlinedAction(
        "Set starting point",
        "Enter a private starting point for this trip scenario",
        TripPlannerUiAction.SetManualOrigin
    )
    private val locationButton = outlinedAction(
        "Use foreground location",
        "Use one foreground location reading for this trip session; no background tracking",
        TripPlannerUiAction.UseForegroundLocation
    )
    private val assumptionsButton = outlinedAction(
        "Enter driving assumptions",
        "Enter manual distance, fuel consumption, fuel price, tolls and parking",
        TripPlannerUiAction.EditTripAssumptions
    )
    private val preferredButton = outlinedAction(
        "",
        "Save or remove this store as the private preferred store",
        TripPlannerUiAction.TogglePreferredStore
    )
    private val directionsButton = outlinedAction(
        "Open external directions",
        "Open an external directions app for this selected store; route facts remain outside ValuePilot",
        TripPlannerUiAction.OpenDirections
    )
    private val clearButton = outlinedAction(
        "Clear trip scenario",
        "Clear the saved trip scenario without changing Basket, purchases or Price Watch",
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
        addView(tripCard)
        addView(evidenceCard)
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
        detailLines += textLineView("")
        detailLines += textLineView("")
        detailLines += textLineView("")
        detailLines += textLineView("")
        detailLines += textLineView("")
        detailLines.forEach(detailsContainer::addView)
        actionButtons += chooseDestinationButton
        actionButtons += setOriginButton
        actionButtons += locationButton
        actionButtons += assumptionsButton
        actionButtons += preferredButton
        actionButtons += directionsButton
        actionButtons += clearButton
    }

    fun render(state: TripPlannerSurfaceState, preferred: Boolean) {
        headline.text = state.headline
        guidance.text = state.guidance
        basketSummaryText.text = state.basketText
        destinationText.text = state.destinationText
        originText.text = state.originText
        tripCostHeadline.text = state.tripCostHeadline
        tripCostHeadline.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            if (state.mode == TripPlannerSurfaceMode.CALCULATED) 20f else 16f
        )
        tripCostHeadline.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        tripCostDetail.text = state.tripCostDetail
        breakEvenText.text = state.breakEvenText
        decisionHeadline.text = state.decisionHeadline
        decisionHeadline.setTextColor(
            ValuePilotPalette.color(
                if (state.mode == TripPlannerSurfaceMode.CALCULATED) {
                    ValuePilotDesignTokens.Colors.TEXT_PRIMARY
                } else {
                    ValuePilotDesignTokens.Colors.WARNING
                }
            )
        )
        decisionDetail.text = state.decisionDetail
        renderEvidenceChips(state.evidenceChips)
        detailLines[0].text = state.destinationText
        detailLines[1].text = state.originText
        detailLines[2].text = state.assumptionsText
        detailLines[3].text = state.tripCostText
        detailLines[4].text = state.savingsText

        primaryAction = primaryActionFor(state)
        primaryButton.text = primaryLabelFor(state)
        primaryButton.contentDescription = primaryDescriptionFor(state)
        primaryButton.isEnabled = onAction != null && primaryAction != null
        primaryButton.setOnClickListener { primaryAction?.let { onAction?.invoke(it) } }
        preferredButton.text = if (preferred) "Remove preferred store" else "Use destination as preferred store"
        directionsButton.visibility = if (state.directionsAvailable) VISIBLE else GONE
        moreActionsButton.visibility = if (state.mode == TripPlannerSurfaceMode.UNAVAILABLE) GONE else VISIBLE
        detailsButton.visibility = if (state.mode == TripPlannerSurfaceMode.UNAVAILABLE) GONE else VISIBLE
        moreActionsButton.isEnabled = onAction != null
        actionButtons.forEach { it.isEnabled = onAction != null }
        if (!hasRenderedState) {
            ValuePilotMotion.reveal(tripCard, animate = false)
            ValuePilotMotion.reveal(evidenceCard, animate = false)
            hasRenderedState = true
        } else {
            ValuePilotMotion.reveal(tripCard)
            ValuePilotMotion.reveal(evidenceCard)
        }
    }

    private fun renderEvidenceChips(chips: List<TripPlannerEvidenceChip>) {
        evidenceChips.removeAllViews()
        chips.forEach { chip ->
            val chipView = TextView(context).apply {
                text = "${chip.count} ${chip.label}"
                contentDescription = "${chip.count} ${chip.label}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(ValuePilotPalette.color(chipTextColor(chip.tone)))
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(ValuePilotPalette.color(chipBackgroundColor(chip.tone)))
                }
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6)
                    topMargin = dp(10)
                }
            }
            evidenceChips.addView(chipView)
        }
    }

    private fun setDetailsExpanded(expanded: Boolean) {
        detailsExpanded = expanded
        detailsContainer.visibility = if (expanded) VISIBLE else GONE
        detailsButton.text = if (expanded) "Hide answer details" else "Why this answer"
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
            state.mode == TripPlannerSurfaceMode.UNAVAILABLE || state.mode == TripPlannerSurfaceMode.EMPTY_BASKET -> null
            !state.destinationReady -> TripPlannerUiAction.ChooseDestinationStore
            !state.originReady -> TripPlannerUiAction.SetManualOrigin
            !state.assumptionsReady -> TripPlannerUiAction.EditTripAssumptions
            else -> TripPlannerUiAction.Calculate
        }

    private fun primaryLabelFor(state: TripPlannerSurfaceState): String =
        when (primaryActionFor(state)) {
            TripPlannerUiAction.ChooseDestinationStore -> "Choose destination store"
            TripPlannerUiAction.SetManualOrigin -> "Set starting point"
            TripPlannerUiAction.EditTripAssumptions -> "Enter driving assumptions"
            TripPlannerUiAction.Calculate -> if (state.mode == TripPlannerSurfaceMode.CALCULATED) "Recalculate trip cost" else "Calculate trip cost"
            null -> if (state.mode == TripPlannerSurfaceMode.EMPTY_BASKET) "Add items in Basket first" else "Trip details unavailable"
            else -> "Continue"
        }

    private fun primaryDescriptionFor(state: TripPlannerSurfaceState): String =
        when (primaryActionFor(state)) {
            TripPlannerUiAction.ChooseDestinationStore -> "Choose the exact destination store for this private trip scenario"
            TripPlannerUiAction.SetManualOrigin -> "Enter a private starting point for this trip scenario"
            TripPlannerUiAction.EditTripAssumptions -> "Enter manual distance, fuel consumption, fuel price, tolls and parking"
            TripPlannerUiAction.Calculate -> "Calculate the exact manual trip cost"
            null -> if (state.mode == TripPlannerSurfaceMode.EMPTY_BASKET) "Add items to Basket before planning a trip" else "Trip details are unavailable"
            else -> "Continue the private trip scenario"
        }

    private fun outlinedAction(
        label: String,
        description: String,
        action: TripPlannerUiAction,
        destructive: Boolean = false
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context, destructive = destructive).apply {
        text = label
        contentDescription = description
        layoutParams = fullWidth(top = ValuePilotDesignTokens.Spacing.XS_DP)
        setOnClickListener { onAction?.invoke(action) }
    }

    private fun card(): ValuePilotProductCard = ValuePilotProductCard(context).apply {
        setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
        strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
        layoutParams = fullWidth(top = ValuePilotDesignTokens.Spacing.SM_DP)
    }

    private fun cardColumn(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
    }

    private fun textLineView(value: String): TextView =
        textLine(value, 13f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, top = 6)

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

    private fun chipTextColor(tone: TripPlannerEvidenceTone): String =
        when (tone) {
            TripPlannerEvidenceTone.POSITIVE -> ValuePilotDesignTokens.Colors.SUCCESS
            TripPlannerEvidenceTone.NEUTRAL -> ValuePilotDesignTokens.Colors.PRIMARY_DARK
            TripPlannerEvidenceTone.WARNING -> ValuePilotDesignTokens.Colors.WARNING
            TripPlannerEvidenceTone.UNKNOWN -> ValuePilotDesignTokens.Colors.TEXT_SECONDARY
        }

    private fun chipBackgroundColor(tone: TripPlannerEvidenceTone): String =
        when (tone) {
            TripPlannerEvidenceTone.POSITIVE -> "#E9F3ED"
            TripPlannerEvidenceTone.NEUTRAL -> ValuePilotDesignTokens.Colors.SURFACE_ACCENT
            TripPlannerEvidenceTone.WARNING -> "#FFF2DC"
            TripPlannerEvidenceTone.UNKNOWN -> ValuePilotDesignTokens.Colors.SURFACE_SUBTLE
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
