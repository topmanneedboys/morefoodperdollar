package com.valuepilot.app

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * Secondary product actions revealed on demand.
 *
 * The component owns only presentation mechanics: disclosure, touch-target
 * sizing and typed action delivery. The caller supplies labels and owns all
 * product/evidence/navigation decisions.
 */
internal enum class ValuePilotProductContextActionKind {
    PHOTO,
    WATCH,
    MAP
}

internal data class ValuePilotProductContextAction(
    val kind: ValuePilotProductContextActionKind,
    val label: String,
    val contentDescription: String
) {
    init {
        require(label.isNotBlank())
        require(contentDescription.isNotBlank())
    }
}

internal class ValuePilotProductContextMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val disclosureButton =
        ValuePilotProgrammaticButton.outlined(context).apply {
            isAllCaps = false
            setOnClickListener { setExpanded(!expanded) }
        }

    private val actionsContainer = LinearLayout(context).apply {
        orientation = VERTICAL
        visibility = GONE
    }

    private var expanded = false
    private var actions: List<ValuePilotProductContextAction> = emptyList()

    var onAction: ((ValuePilotProductContextAction) -> Unit)? = null

    init {
        orientation = VERTICAL
        addView(disclosureButton)
        addView(actionsContainer)
        updateDisclosurePresentation()
    }

    fun setActions(value: List<ValuePilotProductContextAction>) {
        require(value.map { it.kind }.distinct().size == value.size) {
            "A product context action may appear only once"
        }
        require(value.size <= MAX_ACTIONS) {
            "Product context actions must remain bounded"
        }
        actions = value
        actionsContainer.removeAllViews()
        value.forEachIndexed { index, action ->
            val button = ValuePilotProgrammaticButton.outlined(context).apply {
                text = action.label
                contentDescription = action.contentDescription
                minimumHeight = dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(
                        if (index == 0) ValuePilotDesignTokens.Spacing.XS_DP
                        else ValuePilotDesignTokens.Spacing.XXS_DP
                    )
                }
                setOnClickListener { onAction?.invoke(action) }
            }
            actionsContainer.addView(button)
        }
        setExpanded(false)
        visibility = if (value.isEmpty()) GONE else VISIBLE
    }

    fun setExpanded(value: Boolean) {
        expanded = value && actions.isNotEmpty()
        actionsContainer.visibility = if (expanded) VISIBLE else GONE
        updateDisclosurePresentation()
    }

    fun isExpanded(): Boolean = expanded

    private fun updateDisclosurePresentation() {
        disclosureButton.text =
            context.getString(
                if (expanded) R.string.product_context_hide_actions
                else R.string.product_context_more_actions
            )
        disclosureButton.contentDescription =
            context.getString(
                if (expanded) R.string.product_context_hide_actions_description
                else R.string.product_context_more_actions_description
            )
        disclosureButton.visibility = if (actions.isEmpty()) GONE else VISIBLE
        disclosureButton.minimumHeight = dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
        contentDescription = disclosureButton.contentDescription
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MAX_ACTIONS = 3
    }
}
