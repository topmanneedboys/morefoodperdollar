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
import com.valuepilot.core.ConfirmedPurchaseIdentityOrigin

sealed interface PracticalShoppingBasketUiAction {
    data object OpenHome : PracticalShoppingBasketUiAction

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

    private val headline = line("", 22f, "#111827", true)
    private val guidance = line("", 14f, "#4B5563", topPadding = 8)
    private val noCoverageSummary = line("", 13f, "#92400E", true, 8).apply {
        // This aggregate is already projected by Home and only clarifies the
        // existing no-coverage state. Announce it without giving the View any
        // pricing or planning authority.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
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
        strokeColor = ColorStateList.valueOf(ValuePilotPalette.color("#D1D5DB"))
        setTextColor(ValuePilotPalette.color("#374151"))
        backgroundTintList = ColorStateList.valueOf(ValuePilotPalette.color("#FFFFFF"))
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
        line("PRIVATE SEARCH CHECKLIST", 13f, "#1D4ED8", true, 16)
    private val privateIdentityProgress = line("", 13f, "#1E3A8A", true, 6).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val privateIdentityNotice =
        line(
            "Saved identities are a local checklist only; they do not confirm price, store, stock, availability or purchase.",
            12f,
            "#374151",
            topPadding = 5
        )
    private val privateIdentityPhotoError = line("", 13f, "#B42318", true, 8).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        visibility = GONE
    }
    private val privateIdentityRows = column(padded = false)
    private val privateIdentityBody = column()
    private val privateIdentityCard =
        card("#EFF6FF", "#BFDBFE", 12, privateIdentityBody)
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
        strokeColor = ColorStateList.valueOf(ValuePilotPalette.color("#D1D5DB"))
        setTextColor(ValuePilotPalette.color("#374151"))
        backgroundTintList = ColorStateList.valueOf(ValuePilotPalette.color("#FFFFFF"))
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
    private val privateIdentityRowsByKey = LinkedHashMap<String, PrivateIdentityRow>()

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
        addView(privateIdentityCard)
        addView(headline.apply { setPadding(0, dp(20), 0, 0) })
        addView(guidance)
        addView(noCoverageSummary)
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
        noCoverageSummary.visibility = GONE
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
        noCoverageSummary.text = state.noCoverageSummary.orEmpty()
        noCoverageSummary.visibility =
            if (state.noCoverageSummary == null) GONE else VISIBLE
        sampleNotice.text = state.sampleNotice
        actionButton.text = state.actionLabel
        actionButton.isEnabled = onAction != null && hasRenderedState
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

    private fun renderPrivateIdentities(
        identities: List<PracticalShoppingBasketPrivateIdentityRenderState>
    ) {
        privateIdentityCard.visibility = if (identities.isEmpty()) GONE else VISIBLE
        if (identities.isEmpty()) {
            privateIdentityPhotoError.text = ""
            privateIdentityPhotoError.visibility = GONE
            privateIdentityRows.removeAllViews()
            privateIdentityRowsByKey.clear()
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
                privateIdentityRowsByKey.remove(identityKey)?.root?.let(privateIdentityRows::removeView)
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
        val root = column(padded = false).apply {
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 0)
        }
        private val toggle = privateIdentityButton(identity)
        private val brand = line("", 12f, "#374151", topPadding = 2)
        private val packageQuantity = line("", 12f, "#374151", topPadding = 2)
        private val source = line("", 12f, "#374151", topPadding = 2)
        private val notice = line("", 12f, "#6B7280", topPadding = 2)
        private val photo = privateIdentityPhotoButton(identity)
        private val purchase = privateIdentityPurchaseButton(identity)
        private var displayName = identity.displayName
        var collected: Boolean = identity.collected
            private set

        init {
            brand.visibility = GONE
            packageQuantity.visibility = GONE
            source.visibility = GONE
            root.addView(toggle)
            root.addView(brand)
            root.addView(packageQuantity)
            root.addView(source)
            root.addView(notice)
            root.addView(photo)
            root.addView(purchase)
            update(identity)
        }

        fun update(identity: PracticalShoppingBasketPrivateIdentityRenderState) {
            displayName = identity.displayName
            toggle.text = "${if (identity.collected) "✓" else "○"} ${identity.displayName}"
            toggle.contentDescription =
                "Mark ${identity.displayName} as " +
                    (if (identity.collected) "not collected" else "collected") +
                    ". This is only a local shopping checklist mark."
            toggle.isEnabled = onPrivateIdentityToggle != null
            brand.text = identity.brand?.let { "Brand: $it" }.orEmpty()
            brand.visibility = if (identity.brand == null) GONE else VISIBLE
            packageQuantity.text = identity.packageQuantityLabel?.let { "Package: $it" }.orEmpty()
            packageQuantity.visibility =
                if (identity.packageQuantityLabel == null) GONE else VISIBLE
            source.text = identity.sourceLabel?.let { "Source: $it" }.orEmpty()
            source.visibility = if (identity.sourceLabel == null) GONE else VISIBLE
            notice.text = identity.notice
            photo.contentDescription = context.getString(
                R.string.home_private_identity_photos_description,
                identity.displayName
            )
            photo.isEnabled = onOpenPrivateIdentityPhotos != null
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
            collected = nextCollected
        }
    }

    private fun privateIdentityButton(
        identity: PracticalShoppingBasketPrivateIdentityRenderState
    ): MaterialButton = MaterialButton(context).apply {
        isAllCaps = false
        textSize = 14f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        cornerRadius = dp(14)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(ValuePilotPalette.color("#93C5FD"))
        setTextColor(ValuePilotPalette.color("#1E3A8A"))
        backgroundTintList = ColorStateList.valueOf(ValuePilotPalette.color("#FFFFFF"))
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
    ): MaterialButton = MaterialButton(context).apply {
        text = context.getString(R.string.search_identity_view_photos)
        contentDescription = context.getString(
            R.string.home_private_identity_photos_description,
            identity.displayName
        )
        isAllCaps = false
        textSize = 13f
        minHeight = dp(40)
        minimumHeight = dp(40)
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
        isEnabled = onOpenPrivateIdentityPhotos != null
        setPadding(dp(10), 0, dp(10), 0)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(ValuePilotPalette.color("#BFDBFE"))
        setTextColor(ValuePilotPalette.color("#1D4ED8"))
        backgroundTintList = ColorStateList.valueOf(ValuePilotPalette.color("#FFFFFF"))
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 5)
        setOnClickListener { onOpenPrivateIdentityPhotos?.invoke(identity.identityKey) }
    }

    private fun privateIdentityPurchaseButton(
        identity: PracticalShoppingBasketPrivateIdentityRenderState
    ): MaterialButton = MaterialButton(context).apply {
        text = "Confirm purchase"
        contentDescription = "Confirm a purchase of ${identity.displayName}"
        isAllCaps = false
        textSize = 13f
        minHeight = dp(40)
        minimumHeight = dp(40)
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
        setPadding(dp(10), 0, dp(10), 0)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(ValuePilotPalette.color("#A7F3D0"))
        setTextColor(ValuePilotPalette.color("#047857"))
        backgroundTintList = ColorStateList.valueOf(ValuePilotPalette.color("#FFFFFF"))
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
            setBackgroundColor(ValuePilotPalette.color("#BFDBFE"))
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
                        addView(purchaseButton(item))
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
                        addView(purchaseButton(item))
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
            container.addView(
                line(
                    practicalShoppingPlannedStoreLabel(store),
                    12f,
                    "#374151",
                    topPadding = 2
                )
            )
            container.addView(
                line(
                    PRACTICAL_SHOPPING_PLANNED_STORE_NOTICE,
                    12f,
                    "#6B7280",
                    topPadding = 2
                )
            )
        }
        item.plannedPriceText?.let { price ->
            container.addView(line("Included in plan: $price", 12f, "#374151", topPadding = 2))
        }
        item.plannedPriceNotice?.let { notice ->
            container.addView(line(notice, 12f, "#6B7280", topPadding = 2))
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
        strokeColor = ColorStateList.valueOf(ValuePilotPalette.color("#D1D5DB"))
        setTextColor(ValuePilotPalette.color("#374151"))
        backgroundTintList = ColorStateList.valueOf(ValuePilotPalette.color("#FFFFFF"))
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

    private fun purchaseButton(item: PracticalShoppingHomeItemRenderState): MaterialButton =
        MaterialButton(context).apply {
            text = "Confirm purchase"
            contentDescription = "Confirm a purchase of ${item.name}"
            isAllCaps = false
            textSize = 13f
            minHeight = dp(40)
            minimumHeight = dp(40)
            minWidth = 0
            minimumWidth = 0
            insetTop = 0
            insetBottom = 0
            setPadding(dp(10), 0, dp(10), 0)
            strokeWidth = dp(1)
            strokeColor = ColorStateList.valueOf(ValuePilotPalette.color("#A7F3D0"))
            setTextColor(ValuePilotPalette.color("#047857"))
            backgroundTintList = ColorStateList.valueOf(ValuePilotPalette.color("#FFFFFF"))
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 5)
            isEnabled = onConfirmPurchase != null
            setOnClickListener {
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
