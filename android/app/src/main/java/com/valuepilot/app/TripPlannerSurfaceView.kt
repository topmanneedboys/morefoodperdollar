package com.valuepilot.app

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
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

/** Minimal route surface. It renders a projection and emits only typed user actions. */
internal class TripPlannerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onAction: ((TripPlannerUiAction) -> Unit)? = null

    init {
        orientation = VERTICAL
        visibility = GONE
        isSaveEnabled = false
    }

    fun render(state: TripPlannerSurfaceState, preferred: Boolean) {
        removeAllViews()
        addView(line(state.headline, 22f, "#111827", bold = true))
        addView(line(state.guidance, 14f, "#4B5563", top = 8))
        addView(line(state.basketText, 13f, "#374151", bold = true, top = 14))
        addView(line(state.destinationText, 13f, "#374151", top = 10))
        addView(line(state.originText, 13f, "#374151", top = 5))
        addView(line(state.assumptionsText, 13f, "#374151", top = 5))
        addView(line(state.tripCostText, 14f, "#92400E", bold = true, top = 10))
        addView(line(state.savingsText, 12f, "#6B7280", top = 5))
        addView(line(state.decisionText, 12f, "#6B7280", top = 5))

        addView(actionButton("Choose destination store", "Choose the exact destination store for this private trip scenario", TripPlannerUiAction.ChooseDestinationStore))
        addView(actionButton("Set starting point", "Enter a private starting point for this trip scenario", TripPlannerUiAction.SetManualOrigin))
        addView(actionButton("Use foreground location", "Use one foreground location reading for this trip session; no background tracking", TripPlannerUiAction.UseForegroundLocation))
        addView(actionButton("Enter driving assumptions", "Enter manual distance, fuel consumption, fuel price, tolls and parking", TripPlannerUiAction.EditTripAssumptions))
        addView(actionButton("Calculate trip cost", "Calculate the exact manual trip cost", TripPlannerUiAction.Calculate).apply { isEnabled = state.canCalculate })
        addView(actionButton(if (preferred) "Remove preferred store" else "Use destination as preferred store", "${if (preferred) "Remove" else "Save"} this store as the private preferred store", TripPlannerUiAction.TogglePreferredStore))
        if (state.directionsAvailable) {
            addView(actionButton("Open external directions", "Open an external directions app for this selected store; route facts remain outside ValuePilot", TripPlannerUiAction.OpenDirections))
        }
        addView(actionButton("Clear trip scenario", "Clear the saved trip scenario without changing Basket, purchases or Price Watch", TripPlannerUiAction.Clear, destructive = true))
    }

    private fun actionButton(
        label: String,
        description: String,
        action: TripPlannerUiAction,
        destructive: Boolean = false
    ): MaterialButton = MaterialButton(context).apply {
        text = label
        contentDescription = description
        isAllCaps = false
        minHeight = dp(44)
        setTextColor(ValuePilotPalette.color(if (destructive) "#A63D32" else "#8F3620"))
        setOnClickListener { onAction?.invoke(action) }
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        isEnabled = onAction != null
    }

    private fun line(value: String, size: Float, color: String, bold: Boolean = false, top: Int = 0): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(ValuePilotPalette.color(color))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (top > 0) setPadding(0, dp(top), 0, 0)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
