package com.valuepilot.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

sealed interface PracticalShoppingBasketUiAction {
    data object OpenHome : PracticalShoppingBasketUiAction
}

/**
 * Keeps the check-off action understandable when a screen reader replaces the
 * button's visible label with its content description.
 *
 * Every value comes from the immutable Home projection. This helper adds no
 * matching, pricing, eligibility, or collection policy.
 */
internal fun practicalShoppingBasketCollectionActionDescription(
    item: PracticalShoppingHomeItemRenderState,
    collected: Boolean
): String {
    val state = if (collected) "not collected" else "collected"
    val store = item.storeAssignment?.let { "Buy at $it. " }.orEmpty()
    val notice = item.requestDetailsNotice?.let { " $it" }.orEmpty()
    return "Mark ${item.name} (${item.detail}) as $state. " +
        store +
        "${item.requestDetailsSummary}.$notice"
}

/** Renders immutable Basket state and owns only typed foreground check-off UI state. */
class PracticalShoppingBasketSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var hasRenderedState = false

    var onAction: ((PracticalShoppingBasketUiAction) -> Unit)? = null
        set(value) {
            field = value
            // The route owner may detach while this surface remains mounted.
            // Keep the already-rendered navigation control inert immediately.
            actionButton.isEnabled = value != null && hasRenderedState
        }

    private val headline = line("", 22f, "#111827", true)
    private val guidance = line("", 14f, "#4B5563", topPadding = 8)
    private val collectionProgress = line("", 13f, "#374151", true, 16).apply {
        // Foreground check-off changes after each typed local action. Announce
        // the projected progress politely without granting the View order or
        // shopping-decision authority.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val collectionNotice = line("", 12f, "#6B7280", topPadding = 4).apply {
        // This projected safety disclosure appears when foreground check-off
        // becomes available. Announce it politely without granting the View
        // order, plan, or shopping-decision authority.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val clearCollectionButton = MaterialButton(context).apply {
        text = "Clear check-off"
        contentDescription = "Clear collected item marks"
        isAllCaps = false
        textSize = 13f
        cornerRadius = dp(14)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(Color.parseColor("#D1D5DB"))
        setTextColor(Color.parseColor("#374151"))
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 6)
        setOnClickListener {
            progressState = PracticalShoppingBasketProgressSession.clearCollected(progressState)
            lastRenderState?.let { state ->
                renderCollectionProgress(state)
                renderCollectionResetControl()
                renderItems(state)
            }
        }
    }
    private val itemsHeading =
        line(context.getString(R.string.basket_items_title), 13f, "#374151", true, 18)
    private val itemsContainer = column(padded = false)
    private val unresolvedBody = column()
    private val unresolvedCard = card("#FFF7ED", "#FED7AA", 12, unresolvedBody)
    private val planResult = PracticalShoppingPlanResultSurfaceView(context)
    private val extraStopRule = line("", 13f, "#374151", topPadding = 12)
    private val extraStopRuleNotice = line("", 12f, "#92400E", topPadding = 4)
    private val sampleNotice = line("", 13f, "#374151", topPadding = 4)
    private val actionButton = MaterialButton(context).apply {
        isAllCaps = false
        textSize = 15f
        cornerRadius = dp(16)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(Color.parseColor("#D1D5DB"))
        setTextColor(Color.parseColor("#374151"))
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        layoutParams = fullWidth(dp(52), 16)
        // Keep the replaceable navigation control inert until its typed owner is attached.
        isEnabled = false
        setOnClickListener { onAction?.invoke(PracticalShoppingBasketUiAction.OpenHome) }
    }

    private var progressState = PracticalShoppingBasketProgressSession.initial()
    private var lastRenderState: PracticalShoppingBasketRenderState? = null
    private var pendingRestoredCollectedKeys: List<String>? = null
    private var pendingRestoredCollectionScopeId: String? = null
    private var pendingRestoredCollectionScopePresent = false

    init {
        orientation = VERTICAL
        isSaveEnabled = true
        addView(sampleCard())
        addView(headline.apply { setPadding(0, dp(20), 0, 0) })
        addView(guidance)
        addView(collectionProgress)
        addView(collectionNotice)
        addView(clearCollectionButton)
        addView(itemsHeading)
        addView(itemsContainer)
        addView(unresolvedCard)
        addView(planResult)
        addView(extraStopRule)
        addView(extraStopRuleNotice)
        addView(actionButton)
        collectionProgress.visibility = GONE
        collectionNotice.visibility = GONE
        clearCollectionButton.visibility = GONE
        itemsHeading.visibility = GONE
        unresolvedCard.visibility = GONE
        extraStopRule.visibility = GONE
        extraStopRuleNotice.visibility = GONE
    }

    fun render(state: PracticalShoppingBasketRenderState) {
        hasRenderedState = true
        lastRenderState = state
        progressState =
            pendingRestoredCollectedKeys?.let { restored ->
                pendingRestoredCollectedKeys = null
                val savedCollectionScopeId =
                    pendingRestoredCollectionScopeId.takeIf {
                        pendingRestoredCollectionScopePresent
                    }
                pendingRestoredCollectionScopeId = null
                pendingRestoredCollectionScopePresent = false
                PracticalShoppingBasketProgressSession.restore(
                    collectedItemKeyValues = restored,
                    eligibleItemKeys = eligibleKeys(state),
                    collectionScopeId = state.collectionScopeId,
                    savedCollectionScopeId = savedCollectionScopeId
                )
            } ?: PracticalShoppingBasketProgressSession.reconcile(
                progressState,
                eligibleKeys(state),
                collectionScopeId = state.collectionScopeId
            )

        headline.text = state.headline
        guidance.text = state.guidance
        sampleNotice.text = state.sampleNotice
        actionButton.text = state.actionLabel
        actionButton.isEnabled = onAction != null && hasRenderedState
        renderCollectionProgress(state)
        collectionNotice.text = state.collectionNotice.orEmpty()
        collectionNotice.visibility = if (state.collectionNotice == null) GONE else VISIBLE
        renderCollectionResetControl()
        renderItems(state)
        renderUnknownItems(state.unknownItems)
        planResult.render(state.result, state.sampleNotice)
        extraStopRule.text = state.extraStopRuleText.orEmpty()
        extraStopRule.visibility = if (state.extraStopRuleText == null) GONE else VISIBLE
        extraStopRuleNotice.text = state.extraStopRuleNotice.orEmpty()
        extraStopRuleNotice.visibility =
            if (state.extraStopRuleNotice == null) GONE else VISIBLE
    }

    override fun onSaveInstanceState(): Parcelable? {
        val state = Bundle()
        state.putParcelable(SAVED_SUPER_STATE, super.onSaveInstanceState())
        state.putStringArrayList(
            SAVED_COLLECTED_KEYS,
            ArrayList(PracticalShoppingBasketProgressSession.snapshot(progressState))
        )
        state.putString(SAVED_COLLECTION_SCOPE_ID, progressState.collectionScopeId)
        return state
    }

    @Suppress("DEPRECATION")
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }

        pendingRestoredCollectedKeys = state.getStringArrayList(SAVED_COLLECTED_KEYS)?.toList()
        pendingRestoredCollectionScopePresent = state.containsKey(SAVED_COLLECTION_SCOPE_ID)
        pendingRestoredCollectionScopeId = state.getString(SAVED_COLLECTION_SCOPE_ID)
        super.onRestoreInstanceState(state.getParcelable(SAVED_SUPER_STATE))

        lastRenderState?.let(::render)
    }

    private fun eligibleKeys(state: PracticalShoppingBasketRenderState) =
        state.collectibleItemKeys

    private fun renderCollectionProgress(state: PracticalShoppingBasketRenderState) {
        when {
            state.collectionEnabled -> {
                val itemLabel =
                    if (state.result?.primary?.missingItemsText == null) {
                        "planned items"
                    } else {
                        "priced items"
                    }
                collectionProgress.text =
                    "${progressState.collectedItemKeys.size} of " +
                        "${progressState.eligibleItemKeys.size} $itemLabel collected"
                collectionProgress.visibility = VISIBLE
            }

            else -> {
                collectionProgress.text = ""
                collectionProgress.visibility = GONE
            }
        }
    }

    private fun renderCollectionResetControl() {
        clearCollectionButton.visibility =
            if (progressState.collectedItemKeys.isNotEmpty()) VISIBLE else GONE
    }

    private fun renderItems(state: PracticalShoppingBasketRenderState) {
        itemsContainer.removeAllViews()
        itemsHeading.visibility = if (state.items.isEmpty()) GONE else VISIBLE
        state.items.forEach { item ->
            if (item.key in progressState.eligibleItemKeys) {
                val collected = item.key in progressState.collectedItemKeys
                itemsContainer.addView(
                    column(padded = false).apply {
                        addView(collectionButton(item, collected))
                        addItemDetails(item, this, state.collectionEnabled)
                    }
                )
            } else {
                itemsContainer.addView(
                    column(padded = false).apply {
                        addView(
                            line(
                                "• ${item.name}  •  ${item.detail}",
                                14f,
                                "#374151",
                                topPadding = 7
                            )
                        )
                        addItemDetails(item, this, state.collectionEnabled)
                    }
                )
            }
        }
    }

    private fun addItemDetails(
        item: PracticalShoppingHomeItemRenderState,
        container: LinearLayout,
        collectionEnabled: Boolean
    ) {
        item.storeAssignment?.let { store ->
            container.addView(line("Buy at $store", 12f, "#374151", topPadding = 2))
        }
        if (collectionEnabled && item.storeAssignment == null) {
            container.addView(
                line(
                    "No usable price yet — not ready to collect",
                    12f,
                    "#92400E",
                    topPadding = 2
                )
            )
        }
        if (!collectionEnabled) {
            item.priceCoverageNotice?.let { notice ->
                container.addView(line(notice, 12f, "#92400E", topPadding = 2))
            }
        }
        container.addView(line(item.requestDetailsSummary, 12f, "#6B7280", topPadding = 2))
        item.requestDetailsNotice?.let { notice ->
            container.addView(line(notice, 12f, "#92400E", topPadding = 2))
        }
    }

    private fun collectionButton(
        item: PracticalShoppingHomeItemRenderState,
        collected: Boolean
    ): MaterialButton = MaterialButton(context).apply {
        isAllCaps = false
        textSize = 14f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        cornerRadius = dp(14)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(Color.parseColor("#D1D5DB"))
        setTextColor(Color.parseColor("#374151"))
        backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 7)
        text = "${if (collected) "✓" else "○"} ${item.name}  •  ${item.detail}"
        contentDescription = practicalShoppingBasketCollectionActionDescription(item, collected)
        setOnClickListener {
            progressState = PracticalShoppingBasketProgressSession.toggle(progressState, item.key)
            lastRenderState?.let { state ->
                renderCollectionProgress(state)
                renderItems(state)
            }
        }
    }

    private fun renderUnknownItems(items: List<String>) {
        unresolvedBody.removeAllViews()
        if (items.isEmpty()) {
            unresolvedCard.visibility = GONE
            return
        }
        unresolvedBody.addView(
            line(
                context.getString(R.string.basket_unresolved_title),
                13f,
                "#92400E",
                true
            )
        )
        items.forEach { item ->
            unresolvedBody.addView(line("• $item", 14f, "#92400E", topPadding = 7))
        }
        unresolvedCard.visibility = VISIBLE
    }

    private fun sampleCard(): View =
        card("#F0FDF4", "#BBF7D0", 0).apply {
            addView(
                column().apply {
                    addView(
                        line(
                            context.getString(R.string.home_sample_badge),
                            11f,
                            "#047857",
                            true
                        )
                    )
                    addView(sampleNotice)
                }
            )
        }

    private fun card(
        background: String,
        stroke: String,
        topMargin: Int,
        body: View? = null
    ): MaterialCardView = MaterialCardView(context).apply {
        radius = dp(18).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(Color.parseColor(background))
        strokeColor = Color.parseColor(stroke)
        strokeWidth = dp(1)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, topMargin)
        body?.let(::addView)
    }

    private fun column(padded: Boolean = true): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        if (padded) setPadding(dp(16), dp(15), dp(16), dp(15))
    }

    private fun line(
        value: String,
        sizeSp: Float,
        color: String,
        bold: Boolean = false,
        topPadding: Int = 0
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(Color.parseColor(color))
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
    }

    private fun fullWidth(height: Int, topMargin: Int): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, height).apply { this.topMargin = dp(topMargin) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val SAVED_SUPER_STATE = "basket.super_state"
        private const val SAVED_COLLECTED_KEYS = "basket.collected_keys"
        private const val SAVED_COLLECTION_SCOPE_ID = "basket.collection_scope_id"
    }
}
