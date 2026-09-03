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
 * Replaceable physical renderer for explicit Saved product/store selection before observed-price
 * prefill.
 *
 * The view receives only [UserObservedPriceSavedSelectionUiState] and emits only typed actions that
 * are already present in that immutable consumer state. It never reconstructs product/store identity
 * from labels and has no authority to read Saved storage, request prefill, mutate a confirmation
 * draft, capture proof/price/time, persist evidence, rank offers, navigate, or authorize current
 * price semantics.
 *
 * Prefill checking remains owner-controlled: the already-projected check marker is rendered only
 * when an external owner has installed [onCheckPrefillAction]. The view does not invoke a prefill
 * gate or derive a prefill request itself.
 *
 * Route visibility is deliberately external to [render]. This view starts GONE and render never
 * makes it visible; a route owner decides when the surface is on screen.
 */
internal class UserObservedPriceSavedSelectionSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), UserObservedPriceSavedSelectionSurfaceRenderer {

    private val ownerBoundSelectionButtons = mutableListOf<Button>()
    private val ownerBoundPrefillButtons = mutableListOf<Button>()

    var onSelectionAction: ((UserObservedPriceSavedSelectionAction) -> Unit)? = null
        set(value) {
            field = value
            ownerBoundSelectionButtons.forEach { button ->
                button.isEnabled = value != null
            }
        }
    var onCheckPrefillAction: ((UserObservedPriceSavedPrefillCheckUiAction) -> Unit)? = null
        set(value) {
            field = value
            ownerBoundPrefillButtons.forEach { button ->
                button.isEnabled = value != null
            }
        }

    init {
        orientation = VERTICAL
        visibility = View.GONE
        isSaveEnabled = false
    }

    override fun render(state: UserObservedPriceSavedSelectionUiState) {
        ownerBoundSelectionButtons.clear()
        ownerBoundPrefillButtons.clear()
        removeAllViews()

        addView(heading(state.headline))
        addView(guidance(state.guidance))
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
                selectionActionButton(
                    label = requireNotNull(state.clearSelectionActionLabel),
                    action = action,
                    destructive = true,
                    compact = false
                )
            )
        }

        state.checkPrefillAction
            ?.takeIf { onCheckPrefillAction != null }
            ?.let { action ->
                addView(
                    prefillCheckButton(
                        label = requireNotNull(state.checkPrefillActionLabel),
                        action = action
                    )
                )
            }
    }

    private fun productCard(row: UserObservedPriceSavedProductSelectionUiRow): View =
        selectionCard(
            title = row.title,
            selected = row.selected,
            action = row.action,
            actionLabel = row.actionLabel
        )

    private fun storeCard(row: UserObservedPriceSavedStoreSelectionUiRow): View =
        selectionCard(
            title = row.title,
            selected = row.selected,
            action = row.action,
            actionLabel = row.actionLabel
        )

    private fun selectionCard(
        title: String,
        selected: Boolean,
        action: UserObservedPriceSavedSelectionAction,
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
            selectionActionButton(
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

    private fun selectionActionButton(
        label: String,
        action: UserObservedPriceSavedSelectionAction,
        destructive: Boolean,
        compact: Boolean
    ): Button =
        Button(context).apply {
            ownerBoundSelectionButtons += this
            text = label
            setAllCaps(false)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 13f else 14f)
            if (destructive) {
                setTextColor(Color.parseColor("#B91C1C"))
            }
            isEnabled = onSelectionAction != null
            setOnClickListener { onSelectionAction?.invoke(action) }
            layoutParams =
                LayoutParams(
                    if (compact) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(if (compact) 8 else 14)
                    if (compact) gravity = Gravity.END
                }
        }

    private fun prefillCheckButton(
        label: String,
        action: UserObservedPriceSavedPrefillCheckUiAction
    ): Button =
        Button(context).apply {
            ownerBoundPrefillButtons += this
            text = label
            setAllCaps(false)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            isEnabled = onCheckPrefillAction != null
            setOnClickListener { onCheckPrefillAction?.invoke(action) }
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
