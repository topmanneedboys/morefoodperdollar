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
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

/**
 * Replaceable physical renderer for explicit Saved-backed staple setup.
 *
 * The view receives only [StapleWatchSavedSelectionUiState] and emits only the typed actions
 * already embedded in that consumer state. It never reconstructs identity from labels and has no
 * authority to load Saved data, begin fact checks, evaluate store economics, persist setup, or
 * schedule delivery.
 *
 * Continuation stays owner-controlled: the already-projected continuation marker is rendered only
 * when an external owner has installed [onContinueAction]. The view never inspects setup status or
 * creates a continuation request by itself.
 *
 * Route visibility is deliberately external to [render]. This view starts GONE and render never
 * makes it visible; a route owner must decide when this setup surface is on screen.
 */
class StapleWatchSavedSelectionSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), StapleWatchSavedSelectionSurfaceRenderer {

    var onAction: ((StapleWatchSavedIdentitySelectionAction) -> Unit)? = null
    var onContinueAction: ((StapleWatchSavedIdentityHandoffUiAction) -> Unit)? = null

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    override fun render(state: StapleWatchSavedSelectionUiState) {
        removeAllViews()

        addView(heading(state.headline))
        addView(guidance(state.guidance))
        addView(selectionSummary(state.selectionSummary))
        state.notice?.let { message -> addView(notice(message)) }

        state.productSectionTitle?.let { sectionTitle ->
            addView(sectionHeading(sectionTitle))
            state.productRows.forEach { row -> addView(productCard(row)) }
        }

        state.storeSectionTitle?.let { sectionTitle ->
            addView(sectionHeading(sectionTitle))
            state.storeRows.forEach { row -> addView(storeCard(row)) }
        }

        state.clearSelectionAction?.let { action ->
            addView(
                actionButton(
                    label = requireNotNull(state.clearSelectionActionLabel),
                    action = action,
                    destructive = true,
                    compact = false
                )
            )
        }

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

    private fun productCard(row: StapleWatchSavedProductSelectionUiRow): View =
        selectionCard(
            title = row.title,
            selected = row.watched,
            action = row.action,
            actionLabel = row.actionLabel
        )

    private fun storeCard(row: StapleWatchSavedStoreSelectionUiRow): View =
        selectionCard(
            title = row.title,
            selected = row.usualStore,
            action = row.action,
            actionLabel = row.actionLabel
        )

    private fun selectionCard(
        title: String,
        selected: Boolean,
        action: StapleWatchSavedIdentitySelectionAction,
        actionLabel: String
    ): View {
        val card =
            MaterialCardView(context).apply {
                radius = dp(18).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(
                    Color.parseColor(if (selected) "#F0FDF4" else "#FFFFFF")
                )
                strokeColor = Color.parseColor(if (selected) "#86EFAC" else "#E5E7EB")
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
            actionButton(
                label = actionLabel,
                action = action,
                destructive = selected,
                compact = true
            )
        )

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

    private fun guidance(value: String): TextView =
        textLine(
            value = value,
            sizeSp = 14f,
            textColor = "#4B5563",
            topPadding = 8
        )

    private fun selectionSummary(value: String): TextView =
        textLine(
            value = value,
            sizeSp = 13f,
            textColor = "#374151",
            topPadding = 10
        )

    private fun notice(value: String): TextView =
        textLine(
            value = value,
            sizeSp = 13f,
            textColor = "#92400E",
            topPadding = 10
        )

    private fun sectionHeading(value: String): TextView =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#374151"))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(20), 0, 0)
        }

    private fun actionButton(
        label: String,
        action: StapleWatchSavedIdentitySelectionAction,
        destructive: Boolean,
        compact: Boolean
    ): Button =
        Button(context).apply {
            text = label
            setAllCaps(false)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 13f else 14f)
            if (destructive) {
                setTextColor(Color.parseColor("#B91C1C"))
            }
            isEnabled = onAction != null
            setOnClickListener { onAction?.invoke(action) }
            layoutParams =
                LayoutParams(
                    if (compact) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(if (compact) 8 else 14)
                    if (compact) gravity = Gravity.END
                }
        }

    private fun continuationButton(
        label: String,
        action: StapleWatchSavedIdentityHandoffUiAction
    ): Button =
        Button(context).apply {
            text = label
            setAllCaps(false)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isEnabled = onContinueAction != null
            setOnClickListener { onContinueAction?.invoke(action) }
            layoutParams =
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(14) }
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
