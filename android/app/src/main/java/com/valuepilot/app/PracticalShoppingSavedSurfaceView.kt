package com.valuepilot.app

import android.content.Context
import android.graphics.Color
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
import com.google.android.material.card.MaterialCardView

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

    var onAction: ((PracticalShoppingSavedSurfaceAction) -> Unit)? = null
        set(value) {
            field = value
            // The lifecycle owner may clear the callback after a render while
            // the route is being replaced. Update existing controls too, so
            // a detached surface cannot still look actionable.
            ownerBoundButtons.forEach { button ->
                button.isEnabled = value != null
            }
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    override fun render(state: PracticalShoppingSavedSurfaceState) {
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
            addView(
                actionButton(
                    label = requireNotNull(state.clearAllActionLabel),
                    action = action,
                    destructive = true,
                    onClick = { showClearAllConfirmation(action) }
                )
            )
        }
    }

    private fun showClearAllConfirmation(
        action: PracticalShoppingSavedSurfaceAction.Preference
    ) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.saved_clear_all_confirmation_title))
            .setMessage(context.getString(R.string.saved_clear_all_confirmation_body))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.saved_clear_all_confirmation_confirm) { _, _ ->
                onAction?.invoke(action)
            }
            .show()
    }

    private fun productCard(row: PracticalShoppingSavedSurfaceProductRow): View =
        preferenceCard(
            title = row.title,
            supportingText = row.supportingText,
            action = row.action,
            actionLabel = row.actionLabel,
            actionDescription = row.actionDescription
        )

    private fun storeCard(row: PracticalShoppingSavedSurfaceStoreRow): View =
        preferenceCard(
            title = row.title,
            supportingText = row.supportingText,
            action = row.action,
            actionLabel = row.actionLabel,
            actionDescription = row.actionDescription
        )

    private fun preferenceCard(
        title: String,
        supportingText: String,
        action: PracticalShoppingSavedSurfaceAction.Preference?,
        actionLabel: String?,
        actionDescription: String?
    ): View {
        val card =
            MaterialCardView(context).apply {
                radius = dp(18).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(Color.WHITE)
                strokeColor = Color.parseColor("#E5E7EB")
                strokeWidth = dp(1)
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
                textColor = "#111827",
                bold = true
            )
        )
        body.addView(
            textLine(
                value = supportingText,
                sizeSp = 12f,
                textColor = "#6B7280",
                topPadding = 5
            )
        )
        action?.let { typedAction ->
            body.addView(
                actionButton(
                    label = requireNotNull(actionLabel),
                    action = typedAction,
                    destructive = true,
                    compact = true,
                    contentDescription = requireNotNull(actionDescription)
                )
            )
        }

        card.addView(body)
        return card
    }

    private fun heading(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(Color.parseColor("#111827"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun sectionHeading(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#374151"))
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
                    PracticalShoppingSavedSurfaceMode.ERROR -> "#B91C1C"
                    PracticalShoppingSavedSurfaceMode.DEGRADED -> "#92400E"
                    else -> "#4B5563"
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
            textColor = "#92400E",
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
            textColor = "#4B5563",
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
        Button(context).apply {
            ownerBoundButtons += this
            text = label
            this.contentDescription = contentDescription
            setAllCaps(false)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 13f else 14f)
            if (destructive) {
                setTextColor(Color.parseColor("#B91C1C"))
            }
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
            setTextColor(Color.parseColor(textColor))
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
