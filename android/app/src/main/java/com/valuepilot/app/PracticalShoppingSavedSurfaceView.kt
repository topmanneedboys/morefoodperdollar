package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Replaceable physical renderer for the Saved surface.
 *
 * This view receives only [PracticalShoppingSavedSurfaceState] and emits only typed
 * [PracticalShoppingSavedSurfaceAction] values. It has no access to persistence documents,
 * exact-preference stores, unresolved technical keys, provider adapters, price/travel facts,
 * clocks, network, or ranking logic.
 *
 * Route visibility is deliberately not controlled by [render]. A lifecycle completion may
 * arrive after the user navigates away; only the app shell owner may decide whether this view
 * is VISIBLE/GONE. The view starts GONE until that future owner activates the Saved route.
 */
class PracticalShoppingSavedSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), PracticalShoppingSavedSurfaceRenderer {

    private val ownerBoundButtons = mutableListOf<Button>()
    private var clearAllConfirmationDialog: AlertDialog? = null
    private var lastRenderedState: PracticalShoppingSavedSurfaceState? = null

    var onAction: ((PracticalShoppingSavedSurfaceAction) -> Unit)? = null
        set(value) {
            field = value
            // The lifecycle owner may clear the callback after a render while
            // the route is being replaced. Update existing controls too, so
            // a detached surface cannot still look actionable.
            ownerBoundButtons.forEach { button ->
                button.isEnabled = value != null
            }
            if (value == null) {
                clearAllConfirmationDialog?.dismiss()
                clearAllConfirmationDialog = null
            }
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    /**
     * Closes window-level confirmation UI when the shell leaves Saved.
     *
     * Route ownership remains with MainActivity; this callback only prevents a
     * confirmation opened on Saved from floating above another destination.
     */
    fun onRouteVisibilityChanged(visible: Boolean) {
        if (visible) return

        clearAllConfirmationDialog?.dismiss()
        clearAllConfirmationDialog = null
    }

    override fun render(state: PracticalShoppingSavedSurfaceState) {
        // Shell renders can repeat while the user scrolls or returns from a contextual route.
        // Avoid rebuilding up to the bounded Saved rows when the immutable projection did not
        // change; the other personal surfaces use the same keyed/stable-render principle.
        if (lastRenderedState == state) return
        lastRenderedState = state
        // A fresh projection supersedes any confirmation tied to the previous
        // projection. Do not leave a detached or stale dialog actionable.
        clearAllConfirmationDialog?.dismiss()
        clearAllConfirmationDialog = null
        ownerBoundButtons.clear()
        removeAllViews()

        addView(heading(state.headline))

        if (state.progressVisible) {
            addView(progress())
        }

        state.statusMessage?.let { message ->
            addView(status(message, state.mode))
        }
        state.notice?.let { message -> addView(notice(message)) }

        state.productSectionTitle?.let { sectionTitle ->
            addView(sectionHeading(sectionTitle))
            state.productRows.forEach { row -> addView(productCard(row)) }
        }

        state.storeSectionTitle?.let { sectionTitle ->
            addView(sectionHeading(sectionTitle))
            state.storeRows.forEach { row -> addView(storeCard(row)) }
        }

        state.emptyMessage?.let { message -> addView(emptyMessage(message)) }

        state.emptyAction?.let { action ->
            addView(
                actionButton(
                    label = requireNotNull(state.emptyActionLabel),
                    action = action,
                    destructive = false,
                    contentDescription = "Search products to save"
                )
            )
        }

        state.refreshAction?.let { action ->
            addView(
                actionButton(
                    label = requireNotNull(state.refreshActionLabel),
                    action = action,
                    destructive = false
                )
            )
        }

        state.clearAllAction?.let { action ->
            val moreActions = LinearLayout(context).apply {
                orientation = VERTICAL
                visibility = View.GONE
            }
            val moreButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
                ownerBoundButtons += this
                text = "More options"
                contentDescription = "Show additional saved-list options"
                isEnabled = onAction != null
                setOnClickListener {
                    val expanded = moreActions.visibility != View.VISIBLE
                    moreActions.visibility = if (expanded) View.VISIBLE else View.GONE
                    text = if (expanded) "Hide options" else "More options"
                    ValuePilotMotion.reveal(moreActions, animate = expanded)
                }
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(12)
                }
            }
            moreActions.addView(
                actionButton(
                    label = requireNotNull(state.clearAllActionLabel),
                    action = action,
                    destructive = true,
                    onClick = { showClearAllConfirmation(action) }
                )
            )
            addView(moreButton)
            addView(moreActions)
        }
    }

    private fun showClearAllConfirmation(
        action: PracticalShoppingSavedSurfaceAction.Preference
    ) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.saved_clear_all_confirmation_title))
            .setMessage(context.getString(R.string.saved_clear_all_confirmation_body))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.saved_clear_all_confirmation_confirm) { _, _ ->
                onAction?.invoke(action)
            }
            .create()
        clearAllConfirmationDialog = dialog
        dialog.setOnDismissListener {
            if (clearAllConfirmationDialog === dialog) {
                clearAllConfirmationDialog = null
            }
        }
        dialog.show()
    }

    private fun productCard(row: PracticalShoppingSavedSurfaceProductRow): View =
        preferenceCard(
            title = row.title,
            supportingText = row.supportingText,
            action = row.action,
            actionLabel = row.actionLabel,
            actionDescription = row.actionDescription,
            secondaryAction = row.secondaryAction,
            secondaryActionLabel = row.secondaryActionLabel,
            secondaryActionDescription = row.secondaryActionDescription,
            watchAction = row.watchAction,
            watchActionLabel = row.watchActionLabel,
            watchActionDescription = row.watchActionDescription
        )

    private fun storeCard(row: PracticalShoppingSavedSurfaceStoreRow): View =
        preferenceCard(
            title = row.title,
            supportingText = row.supportingText,
            action = row.action,
            actionLabel = row.actionLabel,
            actionDescription = row.actionDescription,
            secondaryAction = null,
            secondaryActionLabel = null,
            secondaryActionDescription = null
        )

    private fun preferenceCard(
        title: String,
        supportingText: String,
        action: PracticalShoppingSavedSurfaceAction.Preference?,
        actionLabel: String?,
        actionDescription: String?,
        secondaryAction: PracticalShoppingSavedSurfaceAction?,
        secondaryActionLabel: String?,
        secondaryActionDescription: String?,
        watchAction: PracticalShoppingSavedSurfaceAction.TogglePriceWatch? = null,
        watchActionLabel: String? = null,
        watchActionDescription: String? = null
    ): View {
        val card =
            ValuePilotProductCard(context).apply {
                setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
                strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(10) }
            }

        val body =
            LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(13))
            }
        body.addView(
            textLine(
                value = title,
                sizeSp = 17f,
                textColor = ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
                bold = true
            )
        )
        body.addView(
            textLine(
                value = supportingText,
                sizeSp = 12f,
                textColor = ValuePilotDesignTokens.Colors.TEXT_MUTED,
                topPadding = 5
            )
        )
        // The useful next step remains visible. Destructive and less-frequent
        // actions stay behind an explicit disclosure so a saved product reads
        // as a calm personal list rather than a toolbar of equal-weight verbs.
        secondaryAction?.let { typedAction ->
            body.addView(
                actionButton(
                    label = requireNotNull(secondaryActionLabel),
                    action = typedAction,
                    destructive = false,
                    compact = true,
                    contentDescription = requireNotNull(secondaryActionDescription)
                )
            )
        }
        if (watchAction != null || action != null) {
            val moreActions = LinearLayout(context).apply {
                orientation = VERTICAL
                visibility = View.GONE
            }
            val moreButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
                ownerBoundButtons += this
                text = "More"
                contentDescription = "Show additional actions for $title"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.PRIMARY_DARK))
                isEnabled = onAction != null
                setOnClickListener {
                    val expanded = moreActions.visibility != View.VISIBLE
                    moreActions.visibility = if (expanded) View.VISIBLE else View.GONE
                    text = if (expanded) "Hide" else "More"
                    ValuePilotMotion.reveal(moreActions, animate = expanded)
                }
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                }
            }
            watchAction?.let { typedAction ->
                moreActions.addView(
                    actionButton(
                        label = requireNotNull(watchActionLabel),
                        action = typedAction,
                        destructive = false,
                        compact = true,
                        contentDescription = requireNotNull(watchActionDescription)
                    )
                )
            }
            action?.let { typedAction ->
                moreActions.addView(
                    actionButton(
                        label = requireNotNull(actionLabel),
                        action = typedAction,
                        destructive = true,
                        compact = true,
                        contentDescription = requireNotNull(actionDescription)
                    )
                )
            }
            body.addView(moreButton)
            body.addView(moreActions)
        }

        card.addView(body)
        return card
    }

    private fun heading(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_PRIMARY))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun sectionHeading(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_SECONDARY))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(20), 0, 0)
        }

    private fun progress(): ProgressBar =
        ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams =
                LayoutParams(dp(28), dp(28)).apply {
                    topMargin = dp(14)
                    gravity = Gravity.START
                }
        }

    private fun status(
        value: String,
        mode: PracticalShoppingSavedSurfaceMode
    ): TextView =
        textLine(
            value = value,
            sizeSp = 13f,
            textColor =
                when (mode) {
                    PracticalShoppingSavedSurfaceMode.ERROR -> ValuePilotDesignTokens.Colors.ERROR
                    PracticalShoppingSavedSurfaceMode.DEGRADED -> ValuePilotDesignTokens.Colors.WARNING
                    else -> ValuePilotDesignTokens.Colors.TEXT_SECONDARY
                },
            topPadding = 12
        ).apply {
            // Refresh, mutation, degradation and error messages are projected
            // state changes. Announce them politely without making the View
            // interpret Saved lifecycle or persistence state.
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }

    private fun notice(value: String): TextView =
        textLine(
            value = value,
            sizeSp = 13f,
            textColor = ValuePilotDesignTokens.Colors.WARNING,
            topPadding = 10
        ).apply {
            // An unresolved display-metadata warning is projected state, not a
            // decision. Announce it politely when Saved content changes while
            // keeping interpretation and exact-key handling in the projector.
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }

    private fun emptyMessage(value: String): TextView =
        textLine(
            value = value,
            sizeSp = 15f,
            textColor = ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
            topPadding = 18
        )

    private fun actionButton(
        label: String,
        action: PracticalShoppingSavedSurfaceAction,
        destructive: Boolean,
        compact: Boolean = false,
        contentDescription: String? = null,
        onClick: (() -> Unit)? = null
    ): Button =
        ValuePilotProgrammaticButton.outlined(
            context,
            compact = compact,
            destructive = destructive
        ).apply {
            ownerBoundButtons += this
            text = label
            this.contentDescription = contentDescription
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 13f else 14f)
            isEnabled = onAction != null
            setOnClickListener { onClick?.invoke() ?: onAction?.invoke(action) }
            layoutParams =
                LayoutParams(
                    if (compact) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(if (compact) 8 else 14)
                    if (compact) gravity = Gravity.END
                }
        }

    private fun textLine(
        value: String,
        sizeSp: Float,
        textColor: String,
        bold: Boolean = false,
        topPadding: Int = 0
    ): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(ValuePilotPalette.color(textColor))
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
