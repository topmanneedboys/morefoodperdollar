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
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin
import com.valuepilot.core.ShoppingItemKey

sealed interface PracticalShoppingBasketUiAction {
    data object OpenHome : PracticalShoppingBasketUiAction

    /** Opens the primary Search tab for an empty Basket. */
    data object OpenSearch : PracticalShoppingBasketUiAction

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
    val itemCount =
        state.items.size + state.unknownItems.size + state.privateIdentityItems.size
    val itemSummary = if (itemCount == 1) "1 product" else "$itemCount products"
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
            1 -> "Store: ${stores.single()}"
            else -> "Stores: ${stores.take(2).joinToString()}" +
                if (stores.size > 2) " + ${stores.size - 2} more" else ""
        }
    val tripSummary =
        state.extraStopRuleText?.let { "Trip: $it" }
            ?: state.extraStopRuleNotice?.let { "Trip: $it" }
    val privateChecklistSummary =
        state.privateIdentityItems.size.takeIf { it > 0 }?.let { count ->
            if (count == 1) "1 saved product" else "$count saved products"
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

    /** Runs one bounded exact-key backend plan for the saved Search identities. */
    var onPlanPrivateIdentities: (() -> Unit)? = null
        set(value) {
            field = value
            privateIdentityPlanButton.isEnabled = value != null && hasRenderedState
        }

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

    /** Opens contextual product details while preserving the Basket route. */
    var onOpenProduct: ((PracticalShoppingHomeItemRenderState) -> Unit)? = null
        set(value) {
            field = value
            productOwnerControls.forEach { control -> control.isClickable = value != null }
        }

    /** Toggles the same exact private Price Watch identity used by Home and Search. */
    var onTogglePriceWatch: ((PriceWatchUiAction.ToggleWatch) -> Unit)? = null
        set(value) {
            field = value
            priceWatchOwnerControls.forEach { control -> control.isEnabled = value != null }
        }

    private val priceWatchOwnerControls = mutableListOf<View>()
    private val productOwnerControls = mutableListOf<View>()

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
        27f,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        true,
        ValuePilotDesignTokens.Spacing.SM_DP
    ).apply {
        // The large count is the visual progress treatment. Its content
        // description carries the complete meaning for assistive technology.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private val collectionProgressBar = ProgressBar(
        context,
        null,
        android.R.attr.progressBarStyleHorizontal
    ).apply {
        max = 100
        isIndeterminate = false
        progressTintList = ColorStateList.valueOf(
            ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SUCCESS)
        )
        progressBackgroundTintList = ColorStateList.valueOf(
            ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
        )
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = fullWidth(dp(8), ValuePilotDesignTokens.Spacing.XXS_DP)
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
                val presentation =
                    PracticalShoppingBasketPresentationPolicy.from(
                        state = state,
                        collectedRegularCount = progressState.collectedItemKeys.size
                    )
                renderCollectionProgress(state, presentation)
                renderSummary(state)
                recordPurchaseButton.visibility =
                    if (presentation.showRecordPurchase) VISIBLE else GONE
                renderCollectionResetControl()
                renderItems(state)
                renderDetailsVisibility(presentation)
            }
        }
    }
    private val privateIdentityHeading =
        line(
            "SAVED PRODUCTS",
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
            "Saved products are a local checklist; checking one never records a purchase.",
            12f,
            ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
            topPadding = 5
        )
    private val privateIdentityPlanButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = "Check current prices"
        contentDescription = "Check current listed prices for saved exact products"
        isAllCaps = false
        textSize = 13f
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, ValuePilotDesignTokens.Spacing.XS_DP)
        isEnabled = false
        setOnClickListener { onPlanPrivateIdentities?.invoke() }
    }
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
        setOnClickListener {
            onAction?.invoke(
                if (lastRenderState?.status == PracticalShoppingBasketStatus.EMPTY) {
                    PracticalShoppingBasketUiAction.OpenSearch
                } else {
                    PracticalShoppingBasketUiAction.OpenHome
                }
            )
        }
    }
    private val tripPlannerButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = "Trip cost"
        contentDescription = "Review trip cost for the selected Basket store"
        layoutParams = fullWidth(dp(52), 8)
        isEnabled = false
        setOnClickListener { onOpenTripPlanner?.invoke() }
    }
    private val recordPurchaseButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = "Record purchase"
        contentDescription =
            "Show purchase confirmation actions for collected products; nothing is recorded automatically"
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, ValuePilotDesignTokens.Spacing.SM_DP)
        isEnabled = true
        setOnClickListener { revealFirstCollectedPurchaseAction() }
    }
    private val detailsToggle = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        text = "More basket details"
        contentDescription = "Show planning, price and trip details for this Basket"
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, ValuePilotDesignTokens.Spacing.MD_DP)
        setOnClickListener { setDetailsExpanded(!detailsExpanded) }
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
        addView(headline)
        addView(collectionProgress)
        addView(collectionProgressBar)
        addView(summaryStore)
        addView(guidance)
        addView(recordPurchaseButton)
        addView(actionButton)
    }
    private val basketSummaryCard = ValuePilotProductCard(context).apply {
        setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
        strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.DIVIDER)
        radius = dp(ValuePilotDesignTokens.Shape.NORMAL_COMPONENT_RADIUS_DP).toFloat()
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 0)
        addView(basketSummaryBody)
    }
    private val basketDetailsBody = column().apply {
        addView(summaryTrip)
        addView(summaryPrivate)
        addView(noCoverageSummary)
        addView(collectionNotice)
        addView(clearCollectionButton)
        addView(tripPlannerButton)
        addView(planResult)
        addView(unresolvedCard)
        addView(extraStopRule)
        addView(extraStopRuleNotice)
    }
    private val basketDetailsCard = card(
        ValuePilotDesignTokens.Colors.SURFACE_SUBTLE,
        ValuePilotDesignTokens.Colors.OUTLINE,
        0,
        basketDetailsBody
    )
    private val sampleCardView = sampleCard()
    private val argentinaPlanTitle = line(
        "CURRENT PRICE CHECK",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.PRIMARY_DARK,
        true
    )
    private val argentinaPlanDetails = line(
        "",
        ValuePilotDesignTokens.Typography.BODY_SP,
        ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        topPadding = ValuePilotDesignTokens.Spacing.XS_DP
    )
    private val argentinaPlanBody = column().apply {
        addView(argentinaPlanTitle)
        addView(argentinaPlanDetails)
    }
    private val argentinaPlanCard = card(
        ValuePilotDesignTokens.Colors.SURFACE,
        ValuePilotDesignTokens.Colors.OUTLINE,
        ValuePilotDesignTokens.Spacing.MD_DP,
        argentinaPlanBody
    )

    private var progressState = PracticalShoppingBasketProgressSession.initial()
    private var lastRenderState: PracticalShoppingBasketRenderState? = null
    private var pendingRestoredCollectedKeys: List<String>? = null
    private var pendingRestoredCollectionScopeId: String? = null
    private var pendingRestoredCollectionScopePresent = false
    private var pendingRestoredDetailsExpanded: Boolean? = null
    private var detailsExpanded = false
    private var unresolvedAvailable = false
    private val privateIdentityRowsByKey = LinkedHashMap<String, PrivateIdentityRow>()
    private val basketItemRowsByKey = LinkedHashMap<ShoppingItemKey, BasketItemRow>()

    init {
        orientation = VERTICAL
        isSaveEnabled = true
        privateIdentityBody.addView(privateIdentityHeading)
        privateIdentityBody.addView(privateIdentityProgress)
        privateIdentityBody.addView(privateIdentityNotice)
        privateIdentityBody.addView(privateIdentityPlanButton)
        privateIdentityBody.addView(privateIdentityPhotoError)
        privateIdentityBody.addView(privateIdentityHeadingSeparator())
        privateIdentityBody.addView(privateIdentityRows)
        addView(basketSummaryCard)
        addView(itemsContainer)
        addView(sampleCardView)
        addView(privateIdentityCard)
        addView(argentinaPlanCard)
        addView(detailsToggle)
        addView(basketDetailsCard)
        summaryStore.visibility = GONE
        summaryTrip.visibility = GONE
        summaryPrivate.visibility = GONE
        noCoverageSummary.visibility = GONE
        collectionProgress.visibility = GONE
        collectionProgressBar.visibility = GONE
        collectionNotice.visibility = GONE
        clearCollectionButton.visibility = GONE
        itemsHeading.visibility = GONE
        unresolvedCard.visibility = GONE
        extraStopRule.visibility = GONE
        extraStopRuleNotice.visibility = GONE
        privateIdentityCard.visibility = GONE
        argentinaPlanCard.visibility = GONE
        privateIdentityPlanButton.visibility = GONE
        recordPurchaseButton.visibility = GONE
        detailsToggle.visibility = GONE
        basketDetailsCard.visibility = GONE
    }

    fun render(state: PracticalShoppingBasketRenderState) {
        render(state, null)
    }

    internal fun render(
        state: PracticalShoppingBasketRenderState,
        argentinaPlan: ArgentinaBasketPlanProjection?
    ) {
        hasRenderedState = true
        lastRenderState = state
        pendingRestoredDetailsExpanded?.let { restored ->
            detailsExpanded = restored
            pendingRestoredDetailsExpanded = null
        }
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

        val presentation =
            PracticalShoppingBasketPresentationPolicy.from(
                state = state,
                collectedRegularCount = progressState.collectedItemKeys.size
            )
        if (presentation.isEmpty) detailsExpanded = false
        headline.text = if (presentation.isEmpty) state.headline else "Basket"
        guidance.text = state.guidance
        guidance.visibility = if (presentation.showGuidance) VISIBLE else GONE
        renderSummary(state)
        noCoverageSummary.text = state.noCoverageSummary.orEmpty()
        sampleNotice.text = state.sampleNotice
        sampleCardView.contentDescription = state.sampleNotice
        sampleCardView.visibility =
            if (!presentation.isEmpty && state.sampleNotice.isNotBlank()) VISIBLE else GONE
        actionButton.text = state.actionLabel
        actionButton.isEnabled = onAction != null && hasRenderedState
        actionButton.visibility = if (presentation.showPrimaryAction) VISIBLE else GONE
        recordPurchaseButton.visibility =
            if (presentation.showRecordPurchase) VISIBLE else GONE
        tripPlannerButton.visibility =
            if (presentation.showTripAction) VISIBLE else GONE
        tripPlannerButton.isEnabled = onOpenTripPlanner != null && hasRenderedState
        renderPrivateIdentities(state.privateIdentityItems)
        renderArgentinaPlan(argentinaPlan)
        renderCollectionProgress(state, presentation)
        collectionNotice.text = state.collectionNotice.orEmpty()
        renderCollectionResetControl()
        renderItems(state)
        renderUnknownItems(state.unknownItems)
        planResult.render(state.result, state.sampleNotice)
        extraStopRule.text = state.extraStopRuleText.orEmpty()
        extraStopRuleNotice.text = state.extraStopRuleNotice.orEmpty()
        detailsToggle.visibility = if (presentation.showDetailsToggle) VISIBLE else GONE
        renderDetailsVisibility(presentation)
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
        summaryTrip.text = summary.tripSummary.orEmpty()
        summaryPrivate.text = summary.privateChecklistSummary.orEmpty()
        summaryStore.contentDescription = summary.storeSummary
        summaryTrip.contentDescription = summary.tripSummary
        summaryPrivate.contentDescription = summary.privateChecklistSummary
    }

    private fun renderArgentinaPlan(plan: ArgentinaBasketPlanProjection?) {
        if (plan == null) {
            argentinaPlanCard.visibility = GONE
            argentinaPlanDetails.text = ""
            return
        }
        argentinaPlanCard.visibility = VISIBLE
        argentinaPlanTitle.text = when (plan.status) {
            ArgentinaBasketPlanStatus.CURRENT_COMPLETE -> "CURRENT LISTED-PRICE PLAN"
            ArgentinaBasketPlanStatus.CURRENT_PARTIAL -> "CURRENT PRICE COVERAGE"
            ArgentinaBasketPlanStatus.NO_CURRENT_PLAN -> "CURRENT PRICE CHECK"
        }
        argentinaPlanDetails.text = buildString {
            append(plan.headline)
            append("\n")
            plan.totalText?.let { append(it).append("\n") }
            append(plan.coverageText)
            plan.storeSummary?.let { append("\nStores · ").append(it) }
            plan.evaluatedPlanSummary?.let { append("\nEvaluated plans · ").append(it) }
            plan.lines.forEach { line ->
                append("\n• ").append(line.productName)
                line.brand?.let { append(" · ").append(it) }
                append(" · ").append(line.lineTotalText)
                line.unitPriceText?.let { append(" · ").append(it) }
                append(" · ").append(line.storeName)
                append(" · ").append(line.availabilityText)
            }
            append("\n\n").append(plan.notice)
        }
        argentinaPlanDetails.contentDescription = argentinaPlanDetails.text
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
        lastRenderState?.let { state ->
            val presentation =
                PracticalShoppingBasketPresentationPolicy.from(
                    state = state,
                    collectedRegularCount = progressState.collectedItemKeys.size
                )
            renderSummary(state)
            renderCollectionProgress(state, presentation)
            recordPurchaseButton.visibility =
                if (presentation.showRecordPurchase) VISIBLE else GONE
            detailsToggle.visibility = if (presentation.showDetailsToggle) VISIBLE else GONE
            renderDetailsVisibility(presentation)
        }
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
        state.putBoolean(SAVED_DETAILS_EXPANDED, detailsExpanded)
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
        pendingRestoredDetailsExpanded =
            state.getBoolean(SAVED_DETAILS_EXPANDED).takeIf {
                state.containsKey(SAVED_DETAILS_EXPANDED)
            }
        super.onRestoreInstanceState(state.getParcelable(SAVED_SUPER_STATE))

        lastRenderState?.let(::render)
    }

    private fun eligibleKeys(state: PracticalShoppingBasketRenderState) =
        state.collectibleItemKeys

    private fun renderCollectionProgress(
        state: PracticalShoppingBasketRenderState,
        presentation: PracticalShoppingBasketPresentation
    ) {
        collectionProgress.text =
            if (presentation.showProgress) {
                "${presentation.progressCollected} / ${presentation.progressTotal}"
            } else {
                ""
            }
        collectionProgress.contentDescription =
            if (presentation.showProgress) {
                "${presentation.progressCollected} of ${presentation.progressTotal} basket products collected" +
                    if (state.collectionEnabled || state.items.isEmpty()) {
                        ""
                    } else {
                        ". Some products are not ready to collect until a usable price is available"
                    }
            } else {
                ""
            }
        collectionProgressBar.progress = presentation.progressPercent
        collectionProgress.visibility = if (presentation.showProgress) VISIBLE else GONE
        collectionProgressBar.visibility = if (presentation.showProgress) VISIBLE else GONE
    }

    private fun renderCollectionResetControl() {
        clearCollectionButton.visibility = GONE
    }

    private fun renderDetailsVisibility(
        presentation: PracticalShoppingBasketPresentation
    ) {
        if (!presentation.showDetailsToggle) detailsExpanded = false
        detailsToggle.text = if (detailsExpanded) "Hide basket details" else "More basket details"
        detailsToggle.contentDescription =
            if (detailsExpanded) {
                "Hide planning, price and trip details for this Basket"
            } else {
                "Show planning, price and trip details for this Basket"
            }
        basketDetailsCard.visibility =
            if (presentation.showDetailsToggle && detailsExpanded) VISIBLE else GONE

        val state = lastRenderState
        summaryStore.visibility =
            if (presentation.showStoreContext) VISIBLE else GONE
        summaryTrip.visibility =
            if (detailsExpanded && state?.let { it.extraStopRuleText != null || it.extraStopRuleNotice != null } == true) {
                VISIBLE
            } else {
                GONE
            }
        summaryPrivate.visibility = GONE
        noCoverageSummary.visibility =
            if (detailsExpanded && state?.noCoverageSummary != null) VISIBLE else GONE
        collectionNotice.visibility =
            if (detailsExpanded && state?.collectionNotice != null) VISIBLE else GONE
        clearCollectionButton.visibility =
            if (detailsExpanded && progressState.collectedItemKeys.isNotEmpty()) VISIBLE else GONE
        unresolvedCard.visibility =
            if (detailsExpanded && unresolvedAvailable) VISIBLE else GONE
        extraStopRule.visibility =
            if (detailsExpanded && state?.extraStopRuleText != null) VISIBLE else GONE
        extraStopRuleNotice.visibility =
            if (detailsExpanded && state?.extraStopRuleNotice != null) VISIBLE else GONE
    }

    private fun setDetailsExpanded(expanded: Boolean) {
        val state = lastRenderState ?: return
        val presentation =
            PracticalShoppingBasketPresentationPolicy.from(
                state = state,
                collectedRegularCount = progressState.collectedItemKeys.size
            )
        detailsExpanded = expanded && presentation.showDetailsToggle
        renderDetailsVisibility(presentation)
        if (detailsExpanded) {
            basketDetailsCard.post { detailsToggle.requestFocus() }
        }
    }

    private fun revealFirstCollectedPurchaseAction() {
        basketItemRowsByKey.values.firstOrNull { it.isCollected() }?.let { row ->
            row.revealPurchaseAction()
            row.root.requestFocus()
            return
        }
        privateIdentityRowsByKey.values.firstOrNull { it.collected }?.let { row ->
            row.revealPurchaseAction()
            row.root.requestFocus()
        }
    }

    private fun renderPrivateIdentities(
        identities: List<PracticalShoppingBasketPrivateIdentityRenderState>
    ) {
        privateIdentityCard.visibility = if (identities.isEmpty()) GONE else VISIBLE
        privateIdentityPlanButton.visibility = if (identities.isEmpty()) GONE else VISIBLE
        privateIdentityPlanButton.isEnabled = onPlanPrivateIdentities != null && hasRenderedState
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
        val collected = rows.count { it.collected }
        privateIdentityProgress.text = "$collected / ${rows.size}"
        privateIdentityProgress.contentDescription =
            "$collected of ${rows.size} saved products collected; checking one never records a purchase"
    }

    private inner class PrivateIdentityRow(identity: PracticalShoppingBasketPrivateIdentityRenderState) {
        val root = ValuePilotProductCard(context).apply {
            setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
            strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, ValuePilotDesignTokens.Spacing.XS_DP)
        }
        private val body = column(padded = true)
        private val toggle = privateIdentityButton(identity)
        private val pricePreview = line("", 12f, ValuePilotDesignTokens.Colors.PRIMARY_DARK, true, topPadding = 3)
        private val brand = line("", 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 2)
        private val packageQuantity = line("", 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 2)
        private val source = line("", 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 2)
        private val notice = line("", 12f, ValuePilotDesignTokens.Colors.TEXT_MUTED, topPadding = 2)
        private val watchContext = line("", 12f, ValuePilotDesignTokens.Colors.PRIMARY_DARK, true, 4)
        private val moreActions = column(padded = false).apply {
            visibility = GONE
        }
        private val moreButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
            text = "More"
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
            notice.visibility = GONE
            body.addView(toggle)
            body.addView(pricePreview)
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
            pricePreview.text = identity.pricePreviewText.orEmpty()
            pricePreview.contentDescription = identity.pricePreviewText
            pricePreview.visibility = if (identity.pricePreviewText != null) VISIBLE else GONE
            brand.text = identity.brand?.let { "Brand: $it" }.orEmpty()
            brand.visibility = if (moreExpanded && identity.brand != null) VISIBLE else GONE
            packageQuantity.text = identity.packageQuantityLabel?.let { "Package: $it" }.orEmpty()
            packageQuantity.visibility =
                if (moreExpanded && identity.packageQuantityLabel != null) VISIBLE else GONE
            source.text = identity.sourceLabel?.let { "Source: $it" }.orEmpty()
            source.visibility = if (moreExpanded && identity.sourceLabel != null) VISIBLE else GONE
            notice.text = identity.notice
            notice.visibility = if (moreExpanded) VISIBLE else GONE
            watchContext.text = if (identity.watched) "Watching this exact product" else ""
            watchContext.visibility = if (moreExpanded && identity.watched) VISIBLE else GONE
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
            brand.visibility = if (expanded) VISIBLE else GONE
            packageQuantity.visibility = if (expanded) VISIBLE else GONE
            source.visibility = if (expanded) VISIBLE else GONE
            notice.visibility = if (expanded) VISIBLE else GONE
            moreButton.text = if (expanded) "Hide" else "More"
            moreButton.contentDescription =
                if (expanded) {
                    "Hide more actions for this private item"
                } else {
                    "Show more actions for this private item"
                }
            ValuePilotMotion.reveal(moreActions, animate = expanded)
        }

        fun revealPurchaseAction() {
            setMoreExpanded(true)
            purchase.requestFocus()
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
                    row.dispose()
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
            strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.DIVIDER)
            radius = dp(ValuePilotDesignTokens.Shape.NORMAL_COMPONENT_RADIUS_DP).toFloat()
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, ValuePilotDesignTokens.Spacing.XXS_DP)
        }
        private val body = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(4), dp(6))
        }
        private val identityBody = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
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
            text = "⋯"
            contentDescription = "Show more actions for this basket item"
            minWidth = dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
            minimumWidth = dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
            layoutParams = LayoutParams(
                dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP),
                dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
            )
            setOnClickListener { setMoreExpanded(!moreExpanded) }
        }
        private val collectionButton = ValuePilotProgrammaticButton.outlined(context).apply {
            textSize = ValuePilotDesignTokens.Typography.BODY_SP
            gravity = Gravity.CENTER
            minWidth = dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
            minimumWidth = dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
            setPadding(0, 0, 0, 0)
            layoutParams = LayoutParams(
                dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP),
                dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
            )
        }
        private var currentItem = initialItem
        private var currentCollected = false
        private var moreExpanded = false
        private val purchaseButton = purchaseButton { currentItem }
        private val details = column(padded = false)

        init {
            body.addView(collectionButton)
            identityBody.addView(identity)
            identityBody.addView(quantity)
            body.addView(identityBody)
            body.addView(moreButton)
            root.addView(body)
            root.addView(details)
            secondaryActions.addView(purchaseButton)
            root.addView(secondaryActions)
            productOwnerControls += identityBody
            identityBody.setOnClickListener { onOpenProduct?.invoke(currentItem) }
            collectionButton.setOnClickListener {
                progressState =
                    PracticalShoppingBasketProgressSession.toggle(
                        progressState,
                        currentItem.key
                    )
                lastRenderState?.let { state ->
                    renderSummary(state)
                    val presentation =
                        PracticalShoppingBasketPresentationPolicy.from(
                            state = state,
                            collectedRegularCount = progressState.collectedItemKeys.size
                        )
                    renderCollectionProgress(state, presentation)
                    recordPurchaseButton.visibility =
                        if (presentation.showRecordPurchase) VISIBLE else GONE
                    renderCollectionResetControl()
                    renderItems(state)
                    renderDetailsVisibility(presentation)
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
            identityBody.contentDescription =
                listOf(item.name, item.detail).joinToString(". ") + ". Open product details."
            identityBody.isClickable = onOpenProduct != null
            collectionButton.text =
                when {
                    !eligible -> "—"
                    collected -> "✓"
                    else -> "○"
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
            details.visibility = if (moreExpanded) VISIBLE else GONE
            purchaseButton.text = if (collected) "Record purchase" else "Confirm purchase"
            purchaseButton.contentDescription =
                if (collected) {
                    "Record a purchase of ${item.name}; explicit purchase facts are still required"
                } else {
                    "Confirm a purchase of ${item.name}"
                }
            purchaseButton.isEnabled = onConfirmPurchase != null
            currentCollected = collected
            if (collectedChanged) ValuePilotMotion.reveal(root)
        }

        private fun setMoreExpanded(expanded: Boolean) {
            moreExpanded = expanded
            details.visibility = if (expanded) VISIBLE else GONE
            secondaryActions.visibility = if (expanded) VISIBLE else GONE
            moreButton.text = if (expanded) "×" else "⋯"
            moreButton.contentDescription =
                if (expanded) {
                    "Hide more actions for this basket item"
                } else {
                    "Show more actions for this basket item"
                }
            ValuePilotMotion.reveal(secondaryActions, animate = expanded)
        }

        fun isCollected(): Boolean = currentCollected

        fun dispose() {
            productOwnerControls.remove(identityBody)
        }

        fun revealPurchaseAction() {
            setMoreExpanded(true)
            purchaseButton.requestFocus()
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
        unresolvedAvailable = items.isNotEmpty()
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
        unresolvedCard.visibility = GONE
    }

    private fun sampleCard(): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, ValuePilotDesignTokens.Spacing.XS_DP)
            val badge = ValuePilotCompactUi.statusChip(context, "DEMO DATA")
            badge.contentDescription = "Demo data. The sample notice is not a live retailer claim."
            addView(badge)
            // Keep the detailed notice available to accessibility without
            // forcing a repeated paragraph into the first Basket viewport.
            contentDescription = sampleNotice.text
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
        if (padded) setPadding(dp(12), dp(11), dp(12), dp(11))
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
        private const val SAVED_DETAILS_EXPANDED = "basket.details_expanded"
    }
}
