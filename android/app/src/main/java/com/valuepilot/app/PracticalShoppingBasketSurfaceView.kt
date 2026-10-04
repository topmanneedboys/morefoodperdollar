package com.valuepilot.app

import android.content.Context
import android.content.res.ColorStateList
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
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.ShoppingItemKey

sealed interface PracticalShoppingBasketUiAction {
    data object OpenHome : PracticalShoppingBasketUiAction

    /** Opens the Basket-owned manual trip planner without copying Basket state. */
    data object OpenTripPlanner : PracticalShoppingBasketUiAction

    /** Opens the explicit confirmation form; it does not mint a purchase itself. */
    data class ConfirmPurchase(
        val productIdentityKey: String,
        val displayName: String,
        val brand: String?,
        val packageQuantityLabel: String?,
        val plannedPriceText: String?,
        val identityOrigin: ConfirmedPurchaseIdentityOrigin
    ) : PracticalShoppingBasketUiAction
}

/** Presentation-only summary assembled from the already-projected Basket state. */
internal data class PracticalShoppingBasketSummaryProjection(
    val itemCount: Int,
    val itemSummary: String,
    val collectedSummary: String,
    val storeSummary: String?,
    val tripSummary: String?,
    val privateChecklistSummary: String?
)

internal fun practicalShoppingBasketSummaryProjection(
    state: PracticalShoppingBasketRenderState,
    collectedCount: Int,
    eligibleCount: Int
): PracticalShoppingBasketSummaryProjection {
    val itemCount = state.items.size + state.unknownItems.size
    val itemSummary =
        if (itemCount == 1) "1 item in this basket" else "$itemCount items in this basket"
    val collectedSummary =
        if (eligibleCount > 0) {
            "$collectedCount of $eligibleCount collected"
        } else {
            "Collection starts when a usable plan is available"
        }
    val stores = state.items.mapNotNull { it.storeAssignment }.distinct()
    val storeSummary =
        when (stores.size) {
            0 -> null
            1 -> "Planned stop: ${stores.single()}"
            else -> "Planned stops: ${stores.take(2).joinToString()}" +
                if (stores.size > 2) " + ${stores.size - 2} more" else ""
        }
    val tripSummary =
        state.extraStopRuleText?.let { "Trip context: $it" }
            ?: state.extraStopRuleNotice?.let { "Trip context: $it" }
    val privateChecklistSummary =
        state.privateIdentityItems.size.takeIf { it > 0 }?.let { count ->
            if (count == 1) "1 saved private item" else "$count saved private items"
        }
    return PracticalShoppingBasketSummaryProjection(
        itemCount = itemCount,
        itemSummary = itemSummary,
        collectedSummary = collectedSummary,
        storeSummary = storeSummary,
        tripSummary = tripSummary,
        privateChecklistSummary = privateChecklistSummary
    )
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
    val store = item.storeAssignment?.let {
        practicalShoppingPlannedStoreAccessibility(it) + " "
    }.orEmpty()
    val price =
        (
            item.plannedPriceText?.let { "Included in plan: $it. " }
                ?: item.plannedPriceNotice?.let { "$it " }
        ).orEmpty()
    val notice = item.requestDetailsNotice?.let { " $it" }.orEmpty()
    return "Mark ${item.name} (${item.detail}) as $state. " +
        store +
        price +
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

    /** Typed owner action for the separate private Search-identity checklist. */
    var onPrivateIdentityToggle: ((String) -> Unit)? = null

    /** Opens only the user-requested, unverified external photo search. */
    var onOpenPrivateIdentityPhotos: ((String) -> Unit)? = null

    /** Opens an explicit confirmation form for a private purchase. */
    var onConfirmPurchase: ((PracticalShoppingBasketUiAction.ConfirmPurchase) -> Unit)? = null

    /** Opens the Basket-owned trip planner. */
    var onOpenTripPlanner: (() -> Unit)? = null
        set(value) {
            field = value
            tripPlannerButton.isEnabled = value != null && hasRenderedState
        }

    /** Toggles the same exact private Price Watch identity used by Home and Search. */
    var onTogglePriceWatch: ((PriceWatchUiAction.ToggleWatch) -> Unit)? = null
        set(value) {
            field = value
            priceWatchOwnerControls.forEach { control -> control.isEnabled = value != null }
        }

    private val priceWatchOwnerControls = mutableListOf<View>()

    private val headline = line(
        "",
        ValuePilotDesignTokens.Typography.TITLE_SP,
        ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        true
    )
    private val guidance = line(
        "",
        ValuePilotDesignTokens.Typography.BODY_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = ValuePilotDesignTokens.Spacing.XS_DP
    )
    private val noCoverageSummary = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.WARNING,
        true,
        ValuePilotDesignTokens.Spacing.XS_DP
    ).apply {
        // This aggregate is already projected by Home and only clarifies the
        // existing no-coverage state. Announce it without giving the View any
        // pricing or planning authority.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val collectionProgress = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        true,
        ValuePilotDesignTokens.Spacing.MD_DP
    ).apply {
        // Foreground check-off changes after each typed local action. Announce
        // the projected progress politely without granting the View order or
        // shopping-decision authority.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val collectionNotice = line(
        "",
        12f,
        ValuePilotDesignTokens.Colors.TEXT_MUTED,
        topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
    ).apply {
        // This projected safety disclosure appears when foreground check-off
        // becomes available. Announce it politely without granting the View
        // order, plan, or shopping-decision authority.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val clearCollectionButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        text = "Clear check-off"
        contentDescription = "Clear collected item marks"
        isAllCaps = false
        textSize = 13f
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
    private val privateIdentityHeading =
        line(
            "PRIVATE SEARCH CHECKLIST",
            ValuePilotDesignTokens.Typography.LABEL_SP,
            ValuePilotDesignTokens.Colors.PRIMARY_DARK,
            true,
            ValuePilotDesignTokens.Spacing.MD_DP
        )
    private val privateIdentityProgress = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.PRIMARY_DARK,
        true,
        ValuePilotDesignTokens.Spacing.XS_DP
    ).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val privateIdentityNotice =
        line(
            "Saved identities are a local checklist only; they do not confirm price, store, stock, availability or purchase.",
            12f,
            ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
            topPadding = 5
        )
    private val privateIdentityPhotoError = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.ERROR,
        true,
        ValuePilotDesignTokens.Spacing.XS_DP
    ).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        visibility = GONE
    }
    private val privateIdentityRows = column(padded = false)
    private val privateIdentityBody = column()
    private val privateIdentityCard =
        card(
            ValuePilotDesignTokens.Colors.SURFACE,
            ValuePilotDesignTokens.Colors.OUTLINE,
            ValuePilotDesignTokens.Spacing.MD_DP,
            privateIdentityBody
        )
    private val itemsHeading =
        line(
            context.getString(R.string.basket_items_title),
            ValuePilotDesignTokens.Typography.LABEL_SP,
            ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
            true,
            ValuePilotDesignTokens.Spacing.LG_DP
        )
    private val itemsContainer = column(padded = false)
    private val unresolvedBody = column()
    private val unresolvedCard = card(
        ValuePilotDesignTokens.Colors.SURFACE_SUBTLE,
        ValuePilotDesignTokens.Colors.OUTLINE_DESTRUCTIVE,
        ValuePilotDesignTokens.Spacing.MD_DP,
        unresolvedBody
    )
    private val planResult = PracticalShoppingPlanResultSurfaceView(context)
    private val extraStopRule = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = ValuePilotDesignTokens.Spacing.SM_DP
    )
    private val extraStopRuleNotice = line(
        "",
        12f,
        ValuePilotDesignTokens.Colors.WARNING,
        topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
    )
    private val sampleNotice = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
    )
    private val actionButton = ValuePilotProgrammaticButton.filled(context).apply {
        textSize = ValuePilotDesignTokens.Typography.BODY_SP
        layoutParams = fullWidth(dp(56), ValuePilotDesignTokens.Spacing.MD_DP)
        // Keep the replaceable navigation control inert until its typed owner is attached.
        isEnabled = false
        setOnClickListener { onAction?.invoke(PracticalShoppingBasketUiAction.OpenHome) }
    }
    private val tripPlannerButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = "Plan this trip"
        contentDescription = "Plan a manual trip for the current Basket"
        layoutParams = fullWidth(dp(52), 8)
        isEnabled = false
        setOnClickListener { onOpenTripPlanner?.invoke() }
    }

    private val summaryItemCount = line(
        "",
        18f,
        ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        true,
        ValuePilotDesignTokens.Spacing.SM_DP
    )
    private val summaryStore = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
    )
    private val summaryTrip = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
    )
    private val summaryPrivate = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.PRIMARY_DARK,
        topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
    )
    private val basketSummaryBody = column().apply {
        addView(
            line(
                "ACTIVE SHOPPING MISSION",
                11f,
                ValuePilotDesignTokens.Colors.PRIMARY_DARK,
                true
            )
        )
        addView(headline)
        addView(summaryItemCount)
        addView(guidance)
        addView(summaryStore)
        addView(summaryTrip)
        addView(summaryPrivate)
        addView(noCoverageSummary)
        addView(collectionProgress)
        addView(collectionNotice)
        addView(clearCollectionButton)
        addView(actionButton)
        addView(tripPlannerButton)
    }
    private val basketSummaryCard = ValuePilotProductCard(context).apply {
        setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
        strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 0)
        addView(basketSummaryBody)
    }

    private var progressState = PracticalShoppingBasketProgressSession.initial()
    private var lastRenderState: PracticalShoppingBasketRenderState? = null
    private var pendingRestoredCollectedKeys: List<String>? = null
    private var pendingRestoredCollectionScopeId: String? = null
    private var pendingRestoredCollectionScopePresent = false
    private val privateIdentityRowsByKey = LinkedHashMap<String, PrivateIdentityRow>()
    private val basketItemRowsByKey = LinkedHashMap<ShoppingItemKey, BasketItemRow>()

    init {
        orientation = VERTICAL
        isSaveEnabled = true
        privateIdentityBody.addView(privateIdentityHeading)
        privateIdentityBody.addView(privateIdentityProgress)
        privateIdentityBody.addView(privateIdentityNotice)
        privateIdentityBody.addView(privateIdentityPhotoError)
        privateIdentityBody.addView(privateIdentityHeadingSeparator())
        privateIdentityBody.addView(privateIdentityRows)
        addView(sampleCard())
        addView(basketSummaryCard)
        addView(planResult)
        addView(privateIdentityCard)
        addView(itemsHeading)
        addView(itemsContainer)
        addView(unresolvedCard)
        addView(extraStopRule)
        addView(extraStopRuleNotice)
        summaryStore.visibility = GONE
        summaryTrip.visibility = GONE
        summaryPrivate.visibility = GONE
        noCoverageSummary.visibility = GONE
        collectionProgress.visibility = GONE
        collectionNotice.visibility = GONE
        clearCollectionButton.visibility = GONE
        itemsHeading.visibility = GONE
        unresolvedCard.visibility = GONE
        extraStopRule.visibility = GONE
        extraStopRuleNotice.visibility = GONE
        privateIdentityCard.visibility = GONE
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
        renderSummary(state)
        noCoverageSummary.text = state.noCoverageSummary.orEmpty()
        noCoverageSummary.visibility =
            if (state.noCoverageSummary == null) GONE else VISIBLE
        sampleNotice.text = state.sampleNotice
        actionButton.text = state.actionLabel
        actionButton.isEnabled = onAction != null && hasRenderedState
        tripPlannerButton.visibility =
            if (state.status == PracticalShoppingBasketStatus.EMPTY) GONE else VISIBLE
        tripPlannerButton.isEnabled = onOpenTripPlanner != null && hasRenderedState
        renderPrivateIdentities(state.privateIdentityItems)
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

    private fun renderSummary(state: PracticalShoppingBasketRenderState) {
        val summary =
            practicalShoppingBasketSummaryProjection(
                state = state,
                collectedCount = progressState.collectedItemKeys.size,
                eligibleCount = progressState.eligibleItemKeys.size
            )
        summaryItemCount.text = summary.itemSummary
        summaryStore.text = summary.storeSummary.orEmpty()
        summaryStore.visibility = if (summary.storeSummary == null) GONE else VISIBLE
        summaryTrip.text = summary.tripSummary.orEmpty()
        summaryTrip.visibility = if (summary.tripSummary == null) GONE else VISIBLE
        summaryPrivate.text = summary.privateChecklistSummary.orEmpty()
        summaryPrivate.visibility =
            if (summary.privateChecklistSummary == null) GONE else VISIBLE
    }

    /**
     * Updates one already-rendered private identity row without rebuilding the
     * checklist. The owner calls this for a foreground check-off tap so the
     * row, scroll position and accessibility focus remain stable.
     */
    fun updatePrivateIdentityCollected(identityKey: String, collected: Boolean): Boolean {
        val row = privateIdentityRowsByKey[identityKey] ?: return false
        row.updateCollected(collected)
        lastRenderState?.let { state ->
            lastRenderState =
                state.copy(
                    privateIdentityItems =
                        state.privateIdentityItems.map { identity ->
                            if (identity.identityKey == identityKey) {
                                identity.copy(collected = collected)
                            } else {
                                identity
                            }
                        }
                )
        }
        renderPrivateIdentityProgress()
        return true
    }

    /** Shows a user-triggered photo handoff failure on the visible Basket surface. */
    fun showPhotoError(message: String) {
        require(message.isNotBlank())
        privateIdentityPhotoError.text = message
        privateIdentityPhotoError.visibility = VISIBLE
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
        val summary =
            practicalShoppingBasketSummaryProjection(
                state = state,
                collectedCount = progressState.collectedItemKeys.size,
                eligibleCount = progressState.eligibleItemKeys.size
            )
        collectionProgress.text = summary.collectedSummary
        collectionProgress.visibility =
            if (state.status == PracticalShoppingBasketStatus.PLANNED) VISIBLE else GONE
    }

    private fun renderCollectionResetControl() {
        clearCollectionButton.visibility =
            if (progressState.collectedItemKeys.isNotEmpty()) VISIBLE else GONE
    }

    private fun renderPrivateIdentities(
        identities: List<PracticalShoppingBasketPrivateIdentityRenderState>
    ) {
        privateIdentityCard.visibility = if (identities.isEmpty()) GONE else VISIBLE
        if (identities.isEmpty()) {
            privateIdentityPhotoError.text = ""
            privateIdentityPhotoError.visibility = GONE
            privateIdentityRows.removeAllViews()
            privateIdentityRowsByKey.values.forEach(PrivateIdentityRow::dispose)
            privateIdentityRowsByKey.clear()
            priceWatchOwnerControls.clear()
            renderPrivateIdentityProgress()
            return
        }

        privateIdentityPhotoError.visibility =
            if (privateIdentityPhotoError.text.isNullOrBlank()) GONE else VISIBLE
        reconcilePrivateIdentityRows(identities)
        renderPrivateIdentityProgress()
    }

    private fun reconcilePrivateIdentityRows(
        identities: List<PracticalShoppingBasketPrivateIdentityRenderState>
    ) {
        val desiredKeys = identities.map { it.identityKey }.toSet()
        privateIdentityRowsByKey.keys
            .filterNot(desiredKeys::contains)
            .toList()
            .forEach { identityKey ->
                privateIdentityRowsByKey.remove(identityKey)?.let { row ->
                    row.dispose()
                    privateIdentityRows.removeView(row.root)
                }
            }

        identities.forEachIndexed { index, identity ->
            val row =
                privateIdentityRowsByKey[identity.identityKey]
                    ?: PrivateIdentityRow(identity).also {
                        privateIdentityRowsByKey[identity.identityKey] = it
                    }
            row.update(identity)
            val currentIndex = privateIdentityRows.indexOfChild(row.root)
            if (currentIndex != index) {
                if (currentIndex >= 0) privateIdentityRows.removeViewAt(currentIndex)
                privateIdentityRows.addView(
                    row.root,
                    index.coerceAtMost(privateIdentityRows.childCount)
                )
            }
        }
    }

    private fun renderPrivateIdentityProgress() {
        val rows = privateIdentityRowsByKey.values
        privateIdentityProgress.text =
            "${rows.count { it.collected }} of ${rows.size} private items collected"
    }

    private inner class PrivateIdentityRow(identity: PracticalShoppingBasketPrivateIdentityRenderState) {
        val root = ValuePilotProductCard(context).apply {
            setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
            strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, ValuePilotDesignTokens.Spacing.XS_DP)
        }
        private val body = column(padded = true)
        private val toggle = privateIdentityButton(identity)
        private val brand = line("", 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 2)
        private val packageQuantity = line("", 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 2)
        private val source = line("", 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 2)
        private val notice = line("", 12f, ValuePilotDesignTokens.Colors.TEXT_MUTED, topPadding = 2)
        private val watchContext = line("", 12f, ValuePilotDesignTokens.Colors.PRIMARY_DARK, true, 4)
        private val moreActions = column(padded = false).apply {
            visibility = GONE
        }
        private val moreButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
            text = "More for this item"
            contentDescription = "Show more actions for this private item"
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, ValuePilotDesignTokens.Spacing.XS_DP)
            setOnClickListener { setMoreExpanded(!moreExpanded) }
        }
        private val photo = privateIdentityPhotoButton(identity)
        private val priceWatch = privateIdentityPriceWatchButton(identity)
        private val purchase = privateIdentityPurchaseButton(identity)
        private var moreExpanded = false
        private var displayName = identity.displayName
        var collected: Boolean = identity.collected
            private set

        init {
            brand.visibility = GONE
            packageQuantity.visibility = GONE
            source.visibility = GONE
            body.addView(toggle)
            body.addView(brand)
            body.addView(packageQuantity)
            body.addView(source)
            body.addView(notice)
            body.addView(watchContext)
            body.addView(moreButton)
            moreActions.addView(priceWatch)
            moreActions.addView(photo)
            moreActions.addView(purchase)
            body.addView(moreActions)
            root.addView(body)
            update(identity)
        }

        fun update(identity: PracticalShoppingBasketPrivateIdentityRenderState) {
            displayName = identity.displayName
            toggle.text = "${if (identity.collected) "✓" else "○"} ${identity.displayName}"
            toggle.contentDescription =
                "Mark ${identity.displayName} as " +
                    (if (identity.collected) "not collected" else "collected") +
                    ". This is only a local shopping checklist mark."
            toggle.setTextColor(
                ValuePilotPalette.color(
                    if (identity.collected) {
                        ValuePilotDesignTokens.Colors.SUCCESS
                    } else {
                        ValuePilotDesignTokens.Colors.PRIMARY_DARK
                    }
                )
            )
            toggle.strokeColor =
                ColorStateList.valueOf(
                    ValuePilotPalette.color(
                        if (identity.collected) {
                            ValuePilotDesignTokens.Colors.SUCCESS
                        } else {
                            ValuePilotDesignTokens.Colors.OUTLINE
                        }
                    )
                )
            toggle.backgroundTintList =
                ColorStateList.valueOf(
                    ValuePilotPalette.color(
                        if (identity.collected) {
                            ValuePilotDesignTokens.Colors.SURFACE_SUBTLE
                        } else {
                            ValuePilotDesignTokens.Colors.SURFACE
                        }
                    )
                )
            toggle.isEnabled = onPrivateIdentityToggle != null
            brand.text = identity.brand?.let { "Brand: $it" }.orEmpty()
            brand.visibility = if (identity.brand == null) GONE else VISIBLE
            packageQuantity.text = identity.packageQuantityLabel?.let { "Package: $it" }.orEmpty()
            packageQuantity.visibility =
                if (identity.packageQuantityLabel == null) GONE else VISIBLE
            source.text = identity.sourceLabel?.let { "Source: $it" }.orEmpty()
            source.visibility = if (identity.sourceLabel == null) GONE else VISIBLE
            notice.text = identity.notice
            watchContext.text = if (identity.watched) "Price Watch is active for this exact identity" else ""
            watchContext.visibility = if (identity.watched) VISIBLE else GONE
            photo.contentDescription = context.getString(
                R.string.home_private_identity_photos_description,
                identity.displayName
            )
            photo.isEnabled = onOpenPrivateIdentityPhotos != null
            priceWatch.updatePriceWatch(identity)
            purchase.isEnabled = onConfirmPurchase != null
            collected = identity.collected
        }

        fun updateCollected(nextCollected: Boolean) {
            if (collected == nextCollected) return
            toggle.text = "${if (nextCollected) "✓" else "○"} $displayName"
            toggle.contentDescription =
                "Mark $displayName as " +
                    (if (nextCollected) "not collected" else "collected") +
                    ". This is only a local shopping checklist mark."
            toggle.setTextColor(
                ValuePilotPalette.color(
                    if (nextCollected) {
                        ValuePilotDesignTokens.Colors.SUCCESS
                    } else {
                        ValuePilotDesignTokens.Colors.PRIMARY_DARK
                    }
                )
            )
            toggle.strokeColor =
                ColorStateList.valueOf(
                    ValuePilotPalette.color(
                        if (nextCollected) {
                            ValuePilotDesignTokens.Colors.SUCCESS
                        } else {
                            ValuePilotDesignTokens.Colors.OUTLINE
                        }
                    )
                )
            toggle.backgroundTintList =
                ColorStateList.valueOf(
                    ValuePilotPalette.color(
                        if (nextCollected) {
                            ValuePilotDesignTokens.Colors.SURFACE_SUBTLE
                        } else {
                            ValuePilotDesignTokens.Colors.SURFACE
                        }
                    )
                )
            ValuePilotMotion.reveal(toggle)
            collected = nextCollected
        }

        private fun setMoreExpanded(expanded: Boolean) {
            moreExpanded = expanded
            moreActions.visibility = if (expanded) VISIBLE else GONE
            moreButton.text = if (expanded) "Hide item actions" else "More for this item"
            moreButton.contentDescription =
                if (expanded) {
                    "Hide more actions for this private item"
                } else {
                    "Show more actions for this private item"
                }
            ValuePilotMotion.reveal(moreActions, animate = expanded)
        }

        fun dispose() {
            priceWatchOwnerControls.remove(priceWatch)
        }
    }

    private fun privateIdentityButton(
        identity: PracticalShoppingBasketPrivateIdentityRenderState
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context).apply {
        textSize = 14f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 7)
        minHeight = dp(52)
        text = "${if (identity.collected) "✓" else "○"} ${identity.displayName}"
        contentDescription =
            "Mark ${identity.displayName} as " +
                (if (identity.collected) "not collected" else "collected") +
                ". This is only a local shopping checklist mark."
        isEnabled = onPrivateIdentityToggle != null
        setOnClickListener { onPrivateIdentityToggle?.invoke(identity.identityKey) }
    }

    private fun privateIdentityPhotoButton(
        identity: PracticalShoppingBasketPrivateIdentityRenderState
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        text = context.getString(R.string.search_identity_view_photos)
        contentDescription = context.getString(
            R.string.home_private_identity_photos_description,
            identity.displayName
        )
        textSize = 13f
        minWidth = 0
        minimumWidth = 0
        isEnabled = onOpenPrivateIdentityPhotos != null
        setPadding(dp(10), 0, dp(10), 0)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 5)
        setOnClickListener { onOpenPrivateIdentityPhotos?.invoke(identity.identityKey) }
    }

    private fun privateIdentityPriceWatchButton(
        identity: PracticalShoppingBasketPrivateIdentityRenderState
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        priceWatchOwnerControls += this
        textSize = 13f
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 5)
        updatePriceWatch(identity)
        setOnClickListener {
            onTogglePriceWatch?.invoke(
                PriceWatchUiAction.ToggleWatch(
                    productIdentityKey = identity.identityKey,
                    displayName = identity.displayName,
                    brand = identity.brand,
                    packageQuantityLabel = identity.packageQuantityLabel,
                    watched = identity.watched
                )
            )
        }
    }

    private fun MaterialButton.updatePriceWatch(
        identity: PracticalShoppingBasketPrivateIdentityRenderState
    ) {
        text = if (identity.watched) "Unwatch" else "Watch price"
        contentDescription =
            if (identity.watched) {
                "Stop watching ${identity.displayName}; private history is retained"
            } else {
                "Watch exact product ${identity.displayName} privately"
            }
        isEnabled = onTogglePriceWatch != null
    }

    private fun privateIdentityPurchaseButton(
        identity: PracticalShoppingBasketPrivateIdentityRenderState
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        text = "Confirm purchase"
        contentDescription = "Confirm a purchase of ${identity.displayName}"
        textSize = 13f
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 5)
        isEnabled = onConfirmPurchase != null
        setOnClickListener {
            onConfirmPurchase?.invoke(
                PracticalShoppingBasketUiAction.ConfirmPurchase(
                    productIdentityKey = identity.identityKey,
                    displayName = identity.displayName,
                    brand = identity.brand,
                    packageQuantityLabel = identity.packageQuantityLabel,
                    plannedPriceText = null,
                    identityOrigin = ConfirmedPurchaseIdentityOrigin.SEARCH_IDENTITY
                )
            )
        }
    }

    private fun privateIdentityHeadingSeparator(): View =
        View(context).apply {
            layoutParams = fullWidth(dp(1), 5)
            setBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE))
        }

    private fun renderItems(state: PracticalShoppingBasketRenderState) {
        itemsHeading.visibility = if (state.items.isEmpty()) GONE else VISIBLE
        val desiredKeys = state.items.map { it.key }.toSet()
        basketItemRowsByKey.keys
            .filterNot(desiredKeys::contains)
            .toList()
            .forEach { itemKey ->
                basketItemRowsByKey.remove(itemKey)?.let { row ->
                    itemsContainer.removeView(row.root)
                }
            }

        state.items.forEachIndexed { index, item ->
            val row =
                basketItemRowsByKey[item.key]
                    ?: BasketItemRow(item).also { basketItemRowsByKey[item.key] = it }
            row.update(
                item = item,
                eligible = item.key in progressState.eligibleItemKeys,
                collected = item.key in progressState.collectedItemKeys,
                collectionEnabled = state.collectionEnabled
            )
            val currentIndex = itemsContainer.indexOfChild(row.root)
            if (currentIndex != index) {
                if (currentIndex >= 0) itemsContainer.removeViewAt(currentIndex)
                itemsContainer.addView(row.root, index.coerceAtMost(itemsContainer.childCount))
            }
        }
    }

    /** Keyed product row that updates in place so a check-off never rebuilds the list. */
    private inner class BasketItemRow(initialItem: PracticalShoppingHomeItemRenderState) {
        val root = ValuePilotProductCard(context).apply {
            setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
            strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, ValuePilotDesignTokens.Spacing.XS_DP)
        }
        private val body = column()
        private val identity = line(
            "",
            16f,
            ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
            true
        )
        private val quantity = line(
            "",
            ValuePilotDesignTokens.Typography.LABEL_SP,
            ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
            topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
        )
        private val secondaryActions = column(padded = false).apply {
            visibility = GONE
        }
        private val moreButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
            text = "More for this item"
            contentDescription = "Show more actions for this basket item"
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, ValuePilotDesignTokens.Spacing.XS_DP)
            setOnClickListener { setMoreExpanded(!moreExpanded) }
        }
        private val collectionButton = ValuePilotProgrammaticButton.outlined(context).apply {
            textSize = ValuePilotDesignTokens.Typography.BODY_SP
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = fullWidth(dp(52), ValuePilotDesignTokens.Spacing.SM_DP)
        }
        private var currentItem = initialItem
        private var currentCollected = false
        private var moreExpanded = false
        private val purchaseButton = purchaseButton { currentItem }
        private val details = column(padded = false)

        init {
            body.addView(identity)
            body.addView(quantity)
            body.addView(collectionButton)
            body.addView(details)
            body.addView(moreButton)
            secondaryActions.addView(purchaseButton)
            body.addView(secondaryActions)
            root.addView(body)
            collectionButton.setOnClickListener {
                progressState =
                    PracticalShoppingBasketProgressSession.toggle(
                        progressState,
                        currentItem.key
                    )
                lastRenderState?.let { state ->
                    renderCollectionProgress(state)
                    renderSummary(state)
                    renderCollectionResetControl()
                    renderItems(state)
                }
            }
        }

        fun update(
            item: PracticalShoppingHomeItemRenderState,
            eligible: Boolean,
            collected: Boolean,
            collectionEnabled: Boolean
        ) {
            val collectedChanged = currentCollected != collected
            currentItem = item
            identity.text = item.name
            quantity.text = item.detail
            collectionButton.text =
                when {
                    !eligible -> "Not ready to collect"
                    collected -> "✓  Collected"
                    else -> "○  Collect this item"
                }
            collectionButton.contentDescription =
                if (eligible) {
                    practicalShoppingBasketCollectionActionDescription(item, collected)
                } else {
                    "${item.name} is not ready to collect because a usable price is not available."
                }
            collectionButton.isEnabled = eligible && collectionEnabled
            collectionButton.setTextColor(
                ValuePilotPalette.color(
                    if (collected) {
                        ValuePilotDesignTokens.Colors.SUCCESS
                    } else {
                        ValuePilotDesignTokens.Colors.PRIMARY_DARK
                    }
                )
            )
            collectionButton.strokeColor =
                ColorStateList.valueOf(
                    ValuePilotPalette.color(
                        if (collected) {
                            ValuePilotDesignTokens.Colors.SUCCESS
                        } else {
                            ValuePilotDesignTokens.Colors.OUTLINE
                        }
                    )
                )
            collectionButton.backgroundTintList =
                ColorStateList.valueOf(
                    ValuePilotPalette.color(
                        if (collected) {
                            ValuePilotDesignTokens.Colors.SURFACE_SUBTLE
                        } else {
                            ValuePilotDesignTokens.Colors.SURFACE
                        }
                    )
                )
            root.strokeColor = ValuePilotPalette.color(
                if (collected) {
                    ValuePilotDesignTokens.Colors.SUCCESS
                } else {
                    ValuePilotDesignTokens.Colors.OUTLINE
                }
            )
            root.cardElevation = if (collected) dp(1).toFloat() else 0f
            details.removeAllViews()
            addItemDetails(item, details, collectionEnabled)
            purchaseButton.isEnabled = onConfirmPurchase != null
            currentCollected = collected
            if (collectedChanged) ValuePilotMotion.reveal(root)
        }

        private fun setMoreExpanded(expanded: Boolean) {
            moreExpanded = expanded
            secondaryActions.visibility = if (expanded) VISIBLE else GONE
            moreButton.text = if (expanded) "Hide item actions" else "More for this item"
            moreButton.contentDescription =
                if (expanded) {
                    "Hide more actions for this basket item"
                } else {
                    "Show more actions for this basket item"
                }
            ValuePilotMotion.reveal(secondaryActions, animate = expanded)
        }

    }

    private fun addItemDetails(
        item: PracticalShoppingHomeItemRenderState,
        container: LinearLayout,
        collectionEnabled: Boolean
    ) {
        item.savedExactProductNotice?.let { notice ->
            container.addView(
                line(
                    notice,
                    12f,
                    ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
                    topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
                )
            )
        }
        item.storeAssignment?.let { store ->
            container.addView(
                line(
                    practicalShoppingPlannedStoreLabel(store),
                    12f,
                    ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
                    topPadding = 2
                )
            )
            container.addView(
                line(
                    PRACTICAL_SHOPPING_PLANNED_STORE_NOTICE,
                    12f,
                    ValuePilotDesignTokens.Colors.TEXT_MUTED,
                    topPadding = 2
                )
            )
        }
        item.plannedPriceText?.let { price ->
            container.addView(
                line(
                    "Included in plan: $price",
                    12f,
                    ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
                    topPadding = 2
                )
            )
        }
        item.plannedPriceNotice?.let { notice ->
            container.addView(
                line(notice, 12f, ValuePilotDesignTokens.Colors.TEXT_MUTED, topPadding = 2)
            )
        }
        item.personalHistoryNotice?.let { notice ->
            container.addView(
                line(
                    notice,
                    12f,
                    ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
                    topPadding = ValuePilotDesignTokens.Spacing.XXS_DP
                )
            )
        }
        if (collectionEnabled && item.storeAssignment == null) {
            container.addView(
                line(
                    "No usable price yet — not ready to collect",
                    12f,
                    ValuePilotDesignTokens.Colors.WARNING,
                    topPadding = 2
                )
            )
        }
        if (!collectionEnabled) {
            item.priceCoverageNotice?.let { notice ->
                container.addView(
                    line(notice, 12f, ValuePilotDesignTokens.Colors.WARNING, topPadding = 2)
                )
            }
        }
        container.addView(
            line(
                item.requestDetailsSummary,
                12f,
                ValuePilotDesignTokens.Colors.TEXT_MUTED,
                topPadding = 2
            )
        )
        item.requestDetailsNotice?.let { notice ->
            container.addView(
                line(notice, 12f, ValuePilotDesignTokens.Colors.WARNING, topPadding = 2)
            )
        }
    }

    private fun purchaseButton(
        itemProvider: () -> PracticalShoppingHomeItemRenderState
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        text = "Confirm purchase"
        contentDescription = "Confirm the purchase of this basket item"
        textSize = 13f
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 5)
        isEnabled = onConfirmPurchase != null
        setOnClickListener {
            val item = itemProvider()
            onConfirmPurchase?.invoke(
                PracticalShoppingBasketUiAction.ConfirmPurchase(
                    productIdentityKey = item.key.value,
                    displayName = item.name,
                    brand = null,
                    packageQuantityLabel = item.detail,
                    plannedPriceText = item.plannedPriceText,
                    identityOrigin = ConfirmedPurchaseIdentityOrigin.HOME_BASKET
                )
            )
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
                ValuePilotDesignTokens.Colors.WARNING,
                true
            )
        )
        items.forEach { item ->
            unresolvedBody.addView(
                line("• $item", 14f, ValuePilotDesignTokens.Colors.WARNING, topPadding = 7)
            )
        }
        unresolvedCard.visibility = VISIBLE
    }

    private fun sampleCard(): View =
        card(
            ValuePilotDesignTokens.Colors.SURFACE_SUBTLE,
            ValuePilotDesignTokens.Colors.OUTLINE,
            0
        ).apply {
            addView(
                column().apply {
                    addView(
                        line(
                            context.getString(R.string.home_sample_badge),
                            11f,
                            ValuePilotDesignTokens.Colors.SUCCESS,
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
    ): ValuePilotProductCard = ValuePilotProductCard(context).apply {
        radius = dp(18).toFloat()
        cardElevation = 0f
        setCardBackgroundColor(ValuePilotPalette.color(background))
        strokeColor = ValuePilotPalette.color(stroke)
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
        setTextColor(ValuePilotPalette.color(color))
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
