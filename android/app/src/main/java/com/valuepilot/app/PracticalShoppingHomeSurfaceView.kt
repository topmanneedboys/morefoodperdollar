package com.valuepilot.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.doOnLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.valuepilot.core.ShoppingItemKey

/**
 * Gives assistive technology the same context as the visible extra-stop summary,
 * including whether the progressive settings panel is currently open.
 *
 * This is fixed presentation copy. It does not interpret the threshold or make
 * a second-stop decision.
 */
internal fun practicalShoppingExtraStopSettingsContentDescription(
    summary: String,
    expanded: Boolean,
    notice: String? = null
): String {
    require(summary.isNotBlank())
    require(notice == null || notice.isNotBlank())
    val action = if (expanded) "Hide" else "Show"
    return if (notice == null) {
        "$action extra-stop rule settings. $summary"
    } else {
        "$action extra-stop rule settings. $summary. $notice."
    }
}

/**
 * Replaceable Android renderer for the fictional Practical Shopping Home proof.
 * It receives immutable render state and forwards typed actions only.
 */
class PracticalShoppingHomeSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val queryOwnerControls = mutableListOf<View>()
    private val submitOwnerControls = mutableListOf<View>()
    private val quickAddOwnerControls = mutableListOf<View>()
    private val itemRemovalOwnerControls = mutableListOf<View>()
    private val unknownRemovalOwnerControls = mutableListOf<View>()
    private val privateIdentityRemovalOwnerControls = mutableListOf<View>()
    private val privateIdentityPhotoOwnerControls = mutableListOf<View>()
    private val itemPriceWatchOwnerControls = mutableListOf<View>()
    private val itemDetailsOwnerControls = mutableListOf<View>()
    private val observedPriceOwnerControls = mutableListOf<View>()
    private val chickenChoiceOwnerControls = mutableListOf<View>()
    private val extraStopOwnerControls = mutableListOf<View>()
    private val extraStopChoiceOwnerControls = mutableListOf<View>()
    private val goodPriceOwnerControls = mutableListOf<View>()
    private val privateMemoryReviewOwnerControls = mutableListOf<View>()
    private val privateMemoryExportOwnerControls = mutableListOf<View>()
    private val privateMemoryForgetOwnerControls = mutableListOf<View>()
    private val shopAgainOwnerControls = mutableListOf<View>()
    private val sharePlanOwnerControls = mutableListOf<View>()
    private val purchaseHistoryOwnerControls = mutableListOf<View>()
    private val priceWatchOwnerControls = mutableListOf<View>()
    private val homeSearchOwnerControls = mutableListOf<View>()
    private val homeBasketOwnerControls = mutableListOf<View>()

    private var hasRenderedState = false
    private var lastRenderedSubmitEnabled = false
    private var lastRenderedQuickAddVisible = false
    // Keep unchanged item rows mounted so an unrelated projected update does not
    // reset scroll position or accessibility focus in a long Home list.
    private var lastRenderedItems: List<PracticalShoppingHomeItemRenderState>? = null
    private var projectedResultVisible = false
    private var lastCommandCenterSignature: String? = null

    var onQueryChanged: ((String) -> Unit)? = null
        set(value) {
            field = value
            queryOwnerControls.forEach { control ->
                control.isEnabled = value != null && hasRenderedState
            }
        }
    var onSubmit: ((String) -> Unit)? = null
        set(value) {
            field = value
            submitOwnerControls.forEach { control ->
                control.isEnabled = value != null && lastRenderedSubmitEnabled
            }
        }
    var onQuickAdd: ((PracticalShoppingHomeQuickAdd) -> Unit)? = null
        set(value) {
            field = value
            quickAddOwnerControls.forEach { control ->
                control.isEnabled = value != null && lastRenderedQuickAddVisible
            }
        }
    var onRemoveItem: ((ShoppingItemKey) -> Unit)? = null
        set(value) {
            field = value
            itemRemovalOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onRemoveUnknownItem: ((String) -> Unit)? = null
        set(value) {
            field = value
            unknownRemovalOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onRemovePrivateIdentity: ((String) -> Unit)? = null
        set(value) {
            field = value
            privateIdentityRemovalOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onOpenPrivateIdentityPhotos: ((String) -> Unit)? = null
        set(value) {
            field = value
            privateIdentityPhotoOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onChickenChoice: ((LocalSamplePracticalShoppingDemo.ChickenChoice) -> Unit)? = null
        set(value) {
            field = value
            chickenChoiceOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onExtraStopMinimumSavingsChoice:
        ((LocalSamplePracticalShoppingDemo.ExtraStopMinimumSavingsChoice) -> Unit)? = null
        set(value) {
            field = value
            extraStopOwnerControls.forEach { control ->
                control.isEnabled =
                    value != null && hasRenderedState && extraStopSettingsButton.visibility == VISIBLE
            }
            extraStopChoiceOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
            if (value == null) {
                // A detached owner must not leave a previously expanded settings
                // panel visible with stale choices while the surface remains mounted.
                extraStopSettingsExpanded = false
                syncExtraStopSettingsVisibility()
            }
        }
    var onEditItemDetails: ((ShoppingItemKey) -> Unit)? = null
        set(value) {
            field = value
            itemDetailsOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onAddObservedPrice: ((ShoppingItemKey) -> Unit)? = null
        set(value) {
            field = value
            observedPriceOwnerControls.forEach { control ->
                control.isEnabled = value != null
            }
        }
    var onTogglePriceWatch: ((PriceWatchUiAction.ToggleWatch) -> Unit)? = null
        set(value) {
            field = value
            priceWatchOwnerControls.forEach { control -> control.isEnabled = value != null }
            itemPriceWatchOwnerControls.forEach { control -> control.isEnabled = value != null }
        }
    var onCompare: (() -> Unit)? = null
        set(value) {
            field = value
            compareActionButton.isEnabled = value != null && hasRenderedState
        }
    var onGoodPrice: (() -> Unit)? = null
        set(value) {
            field = value
            goodPriceOwnerControls.forEach { control ->
                control.isEnabled = value != null && hasRenderedState
            }
        }
    var onReviewPrivateMemory: (() -> Unit)? = null
        set(value) {
            field = value
            privateMemoryReviewOwnerControls.forEach { control ->
                control.isEnabled =
                    value != null && hasRenderedState && control.visibility == VISIBLE
            }
        }
    var onExportPrivateMemory: (() -> Unit)? = null
        set(value) {
            field = value
            privateMemoryExportOwnerControls.forEach { control ->
                control.isEnabled =
                    value != null && hasRenderedState && control.visibility == VISIBLE
            }
        }
    var onForgetPrivateMemory: (() -> Unit)? = null
        set(value) {
            field = value
            privateMemoryForgetOwnerControls.forEach { control ->
                control.isEnabled =
                    value != null && hasRenderedState && control.visibility == VISIBLE
            }
        }
    var onShopAgain: (() -> Unit)? = null
        set(value) {
            field = value
            shopAgainOwnerControls.forEach { control ->
                control.isEnabled =
                    value != null && hasRenderedState && control.visibility == VISIBLE
            }
        }
    var onSharePlan: (() -> Unit)? = null
        set(value) {
            field = value
            sharePlanOwnerControls.forEach { control ->
                control.isEnabled =
                    value != null && hasRenderedState && control.visibility == VISIBLE
            }
        }
    var onOpenConfirmedPurchaseHistory: (() -> Unit)? = null
        set(value) {
            field = value
            purchaseHistoryOwnerControls.forEach { control ->
                control.isEnabled = value != null && hasRenderedState && control.visibility == VISIBLE
            }
        }

    /** Opens the primary Search tab without changing Home/session state. */
    var onOpenSearch: (() -> Unit)? = null
        set(value) {
            field = value
            homeSearchOwnerControls.forEach { control ->
                control.isEnabled = value != null && hasRenderedState
            }
        }

    /** Opens the primary Basket tab without changing Home/session state. */
    var onOpenBasket: (() -> Unit)? = null
        set(value) {
            field = value
            homeBasketOwnerControls.forEach { control ->
                control.isEnabled = value != null && hasRenderedState
            }
        }

    /** Opens the concise demo-data explanation without adding provenance to the Home viewport. */
    var onOpenDemoInfo: (() -> Unit)? = null

    private var suppressInputCallback = false
    private var extraStopSettingsExpanded = false
    private var currentExtraStopSettingsNotice: String? = null

    private val input = TextInputEditText(context).apply {
        maxLines = 3
        imeOptions = EditorInfo.IME_ACTION_DONE
        inputType =
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        isSaveEnabled = false
        // A detached Home surface must not accept edits that have no owner to consume them.
        isEnabled = false
    }

    private val inputLayout = TextInputLayout(context).apply {
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        hint = context.getString(R.string.home_list_hint)
        setBoxBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
        setBoxCornerRadii(
            dp(18).toFloat(),
            dp(18).toFloat(),
            dp(18).toFloat(),
            dp(18).toFloat()
        )
        endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        addView(input)
    }

    private val homePurpose = line(
        context.getString(R.string.home_purpose),
        17f,
        ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        true
    )
    private val homeFlow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 14)
    }
    private val homeSearchButton = ValuePilotProgrammaticButton.filled(context, compact = true).apply {
        homeSearchOwnerControls += this
        text = context.getString(R.string.home_search_products)
        contentDescription = context.getString(R.string.home_search_products_description)
        textSize = 15f
        layoutParams = fullWidth(dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP), 8)
        isEnabled = false
        setOnClickListener { onOpenSearch?.invoke() }
    }
    private val homeHeroCard = ValuePilotProductCard(context).apply {
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 0)
        radius = 0f
        strokeWidth = 0
        setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.BACKGROUND))
        addView(
            column().apply {
                setPadding(0, dp(4), 0, 0)
                addView(homePurpose)
                addView(homeFlow)
                addView(homeSearchButton)
            }
        )
    }

    private val homeDemoBadge = ValuePilotCompactUi.statusChip(context, "DEMO DATA").apply {
        layoutParams = LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        visibility = GONE
        isFocusable = true
        setOnClickListener { onOpenDemoInfo?.invoke() }
    }

    private val activeBasketCount = line(
        "",
        22f,
        ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        true
    ).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val activeBasketSummary = line(
        "",
        13f,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = 4
    )
    private val activeBasketProgress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        isIndeterminate = false
        layoutParams = fullWidth(dp(8), 12)
        visibility = GONE
    }
    private val activeBasketOpenButton = ValuePilotProgrammaticButton.filled(context).apply {
        homeBasketOwnerControls += this
        text = context.getString(R.string.home_open_basket)
        contentDescription = context.getString(R.string.home_open_basket_description)
        textSize = 15f
        layoutParams = fullWidth(dp(ValuePilotDesignTokens.TouchTarget.COMPACT_MINIMUM_DP), 8)
        isEnabled = false
        setOnClickListener { onOpenBasket?.invoke() }
    }
    private val activeBasketCard = ValuePilotProductCard(context).apply {
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 8)
        radius = dp(ValuePilotDesignTokens.Shape.NORMAL_COMPONENT_RADIUS_DP).toFloat()
        setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
        strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.DIVIDER)
        addView(
            column().apply {
                setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(line(context.getString(R.string.home_basket_label), 11f, ValuePilotDesignTokens.Colors.PRIMARY_DARK, true))
                addView(activeBasketCount.apply { setPadding(0, dp(3), 0, 0) })
                addView(activeBasketSummary)
                addView(activeBasketProgress)
                addView(activeBasketOpenButton)
            }
        )
        visibility = GONE
    }

    private val buyAgainCard = ValuePilotProductCard(context).apply {
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 8)
        radius = dp(ValuePilotDesignTokens.Shape.NORMAL_COMPONENT_RADIUS_DP).toFloat()
        setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
        strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.DIVIDER)
        addView(
            column().apply {
                addView(line(context.getString(R.string.home_buy_again_label), 11f, ValuePilotDesignTokens.Colors.PRIMARY_DARK, true))
                addView(line(context.getString(R.string.home_buy_again_body), 15f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, true, 6))
                addView(
                    ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
                        purchaseHistoryOwnerControls += this
                        text = context.getString(R.string.home_buy_again)
                        contentDescription = context.getString(R.string.home_buy_again_description)
                        textSize = 14f
                        layoutParams = fullWidth(dp(48), 12)
                        isEnabled = false
                        setOnClickListener { onOpenConfirmedPurchaseHistory?.invoke() }
                    }
                )
            }
        )
        visibility = GONE
    }

    private val commandCenterListPill = commandCenterPill()
    private val commandCenterStatusPill = commandCenterPill()
    private val commandCenterEyebrow = line(
        "YOUR SHOPPING SPACE",
        11f,
        ValuePilotDesignTokens.Colors.PRIMARY_DARK,
        true
    )
    private val commandCenterTitle = line(
        "Start with your list",
        21f,
        ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        true,
        topPadding = 6
    )
    private val commandCenterSummary = line(
        "Keep the next useful action in view.",
        13f,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = 5
    )
    private val commandCenterPills = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 14)
        addView(commandCenterListPill)
        addView(commandCenterStatusPill)
    }
    private val commandCenterHistoryButton = ValuePilotProgrammaticButton.outlined(
        context,
        compact = true
    ).apply {
        purchaseHistoryOwnerControls += this
        text = context.getString(R.string.home_repeat_purchase)
        contentDescription = context.getString(R.string.home_repeat_purchase_description)
        textSize = 13f
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
        setPadding(dp(10), 0, dp(10), 0)
        layoutParams = fullWidth(dp(ValuePilotDesignTokens.TouchTarget.COMPACT_MINIMUM_DP), 10)
        visibility = GONE
        isEnabled = false
        setOnClickListener { onOpenConfirmedPurchaseHistory?.invoke() }
    }
    private val commandCenterBody = column().apply {
        addView(commandCenterEyebrow)
        addView(commandCenterTitle)
        addView(commandCenterSummary)
        addView(commandCenterPills)
        addView(commandCenterHistoryButton)
    }
    private val commandCenterCard = ValuePilotProductCard(context).apply {
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 0)
        addView(commandCenterBody)
    }

    private var appliedQueryCharacterLimit: Int? = null

    private val submitButton = ValuePilotProgrammaticButton.filled(context).apply {
        text = context.getString(R.string.home_plan_action)
        // Fail closed until the first immutable render state supplies readiness.
        isEnabled = false
        textSize = 16f
        layoutParams = fullWidth(dp(54), 12)
    }

    // Keep the two high-frequency, first-session questions beside the primary
    // Home action. They are still owner-driven navigation controls; this
    // heading only makes their purpose discoverable before a long result is
    // rendered below the list.
    private val fastActionsHeading = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        true
    ).apply {
        setPadding(dp(2), dp(14), dp(2), 0)
    }

    private val quickAddHeading = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        true
    ).apply {
        setPadding(dp(2), dp(12), dp(2), 0)
        visibility = GONE
    }

    private val quickAddGroup = ChipGroup(context).apply {
        isSingleLine = false
        setChipSpacingHorizontal(dp(8))
        setChipSpacingVertical(dp(6))
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 4)
        visibility = GONE
    }

    private val message = line(
        "",
        ValuePilotDesignTokens.Typography.BODY_SP,
        ValuePilotDesignTokens.Colors.TEXT_MUTED
    ).apply {
        setPadding(dp(2), dp(14), dp(2), 0)
        // Refinement and validation feedback changes after user actions. Let
        // assistive technology hear the new immutable message without making
        // the View infer any shopping state.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private val itemsHeading = heading(context.getString(R.string.home_items_title))
    private val itemsContainer = bareColumn()
    private val privateIdentityHeading = heading(context.getString(R.string.home_private_identity_title))
    private val privateIdentityBody = column()
    private val privateIdentityCard =
        card(
            ValuePilotDesignTokens.Colors.SURFACE_ACCENT,
            ValuePilotDesignTokens.Colors.OUTLINE,
            12,
            privateIdentityBody
        )

    private val refinementBody = column()
    private val refinementCard =
        card(
            ValuePilotDesignTokens.Colors.WARNING_SURFACE,
            ValuePilotDesignTokens.Colors.WARNING_OUTLINE,
            16,
            refinementBody
        )

    private val unknownBody = column()
    private val unknownCard =
        card(
            ValuePilotDesignTokens.Colors.WARNING_SURFACE,
            ValuePilotDesignTokens.Colors.WARNING_OUTLINE,
            12,
            unknownBody
        )

    private val resultContainer = PracticalShoppingPlanResultSurfaceView(context)
    private val extraStopSettingsButton = ValuePilotProgrammaticButton.outlined(context).apply {
        textSize = 14f
        layoutParams = fullWidth(dp(50), 12)
        setOnClickListener {
            extraStopSettingsExpanded = !extraStopSettingsExpanded
            syncExtraStopSettingsAccessibility()
            syncExtraStopSettingsVisibility()
        }
    }
    private val extraStopSettingsBody = column()
    private val extraStopSettingsCard =
        card(
            ValuePilotDesignTokens.Colors.SURFACE_SUBTLE,
            ValuePilotDesignTokens.Colors.OUTLINE,
            8,
            extraStopSettingsBody
        )
    private val shopAgainActionButton = shopAgainButton()
    private val sharePlanActionButton = sharePlanButton()
    private val privateMemorySummary = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_MUTED
    ).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = GONE
    }
    private val privateMemoryHighlightsBody = column()
    private val privateMemoryHighlightsCard =
        card(
            ValuePilotDesignTokens.Colors.SURFACE_ACCENT,
            ValuePilotDesignTokens.Colors.OUTLINE,
            8,
            privateMemoryHighlightsBody
        ).apply {
            visibility = GONE
        }
    private val privateMemoryNotice = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.WARNING
    ).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = GONE
    }
    private val sampleNotice = line(
        "",
        ValuePilotDesignTokens.Typography.LABEL_SP,
        ValuePilotDesignTokens.Colors.TEXT_SECONDARY
    )
    private val sampleCardView = sampleCard()
    private val goodPriceActionButton = goodPriceButton()
    private val privateMemoryReviewActionButton = privateMemoryReviewButton()
    private val privateMemoryExportActionButton = privateMemoryExportButton()
    private val privateMemoryForgetActionButton = privateMemoryForgetButton()
    private val compareActionButton = compareButton()
    private val noCoverageSummary = line("", 13f, "#92400E", true).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        visibility = GONE
    }

    init {
        orientation = VERTICAL
        isSaveEnabled = false

        queryOwnerControls += inputLayout
        queryOwnerControls += input
        submitOwnerControls += submitButton
        extraStopOwnerControls += extraStopSettingsButton

        addView(homeHeroCard)
        addView(homeDemoBadge)
        addView(activeBasketCard)
        addView(buyAgainCard)
        addView(commandCenterCard)
        addView(inputLayout)
        addView(submitButton)
        addView(fastActionsHeading)
        addView(compareActionButton)
        addView(goodPriceActionButton)
        // Keep fictional demo shortcuts below the real first-value actions. A blank Home should
        // lead with Plan my shop and the two owner-driven questions; the shortcuts remain a
        // bounded, clearly disclosed way to explore the offline sample without competing with
        // the product's primary actions.
        addView(quickAddHeading)
        addView(quickAddGroup)
        addView(message)
        addView(privateIdentityHeading)
        addView(privateIdentityCard)
        addView(sampleCardView)
        addView(privateMemorySummary)
        addView(privateMemoryHighlightsCard)
        addView(privateMemoryReviewActionButton)
        addView(privateMemoryExportActionButton)
        addView(privateMemoryForgetActionButton)
        addView(privateMemoryNotice)
        addView(itemsHeading)
        addView(itemsContainer)
        addView(refinementCard)
        addView(unknownCard)
        addView(noCoverageSummary)
        addView(resultContainer)
        addView(shopAgainActionButton)
        addView(sharePlanActionButton)
        addView(extraStopSettingsButton)
        addView(extraStopSettingsCard)

        itemsHeading.visibility = GONE
        privateIdentityHeading.visibility = GONE
        privateIdentityCard.visibility = GONE
        sampleCardView.visibility = GONE
        commandCenterCard.visibility = GONE
        refinementCard.visibility = GONE
        unknownCard.visibility = GONE
        extraStopSettingsCard.visibility = GONE
        privateMemoryReviewActionButton.visibility = GONE
        privateMemoryExportActionButton.visibility = GONE
        privateMemoryForgetActionButton.visibility = GONE
        shopAgainActionButton.visibility = GONE
        sharePlanActionButton.visibility = GONE

        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (!suppressInputCallback) onQueryChanged?.invoke(s?.toString().orEmpty())
                }
            }
        )
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        submitButton.setOnClickListener { submit() }
    }

    fun render(state: PracticalShoppingHomeRenderState) {
        val itemProjectionChanged = lastRenderedItems != state.items
        unknownRemovalOwnerControls.clear()
        privateIdentityRemovalOwnerControls.clear()
        privateIdentityPhotoOwnerControls.clear()
        priceWatchOwnerControls.clear()
        if (itemProjectionChanged) {
            itemRemovalOwnerControls.clear()
            itemPriceWatchOwnerControls.clear()
            itemDetailsOwnerControls.clear()
            observedPriceOwnerControls.clear()
        }
        chickenChoiceOwnerControls.clear()
        quickAddOwnerControls.clear()
        extraStopChoiceOwnerControls.clear()
        hasRenderedState = true

        syncQueryCharacterLimit(state.queryCharacterLimit)
        syncQuery(state.query)
        // The clear-text end icon belongs to the TextInputLayout wrapper, so
        // gate the wrapper as well as the editor when no owner can consume
        // query changes.
        inputLayout.isEnabled = onQueryChanged != null
        input.isEnabled = onQueryChanged != null
        lastRenderedSubmitEnabled = state.submitEnabled
        submitButton.isEnabled = state.submitEnabled && onSubmit != null
        renderCommandCenter(state)
        compareActionButton.isEnabled = onCompare != null
        renderMessage(state)
        renderPrivateIdentities(state.privateIdentityItems)
        renderItems(state.items)
        renderRefinement(state.refinement)
        renderQuickAdd(state.quickAddVisible, state.quickAddChoices)
        fastActionsHeading.text = context.getString(R.string.home_fast_actions_title)
        renderUnknown(state.unknownItems)
        renderNoCoverageSummary(state.noCoverageSummary)
        resultContainer.render(state.result, state.sampleNotice)
        projectedResultVisible = state.result != null
        renderShopAgain(state.shopAgainVisible)
        renderSharePlan(state.result?.primary != null)
        renderExtraStopSettings(state.extraStopSettings)
        renderPrivateMemory(
            status = state.privateMemoryStatus,
            summary = state.privateMemorySummary,
            highlights = state.privateMemoryHighlights,
            reviewActionVisible = state.privateMemoryReviewActionVisible,
            exportActionVisible = state.privateMemoryExportActionVisible,
            forgetActionVisible = state.privateMemoryForgetActionVisible
        )
        sampleNotice.text = state.sampleNotice
        goodPriceOwnerControls.forEach { control ->
            control.isEnabled = onGoodPrice != null && hasRenderedState
        }
        renderModernHome(state)
    }

    /** Shows a user-triggered external-photo failure on the visible Home surface. */
    fun showPhotoError(error: String) {
        require(error.isNotBlank())
        message.text = error
        message.setTextColor(ValuePilotPalette.color("#B42318"))
        message.visibility = VISIBLE
    }

    /**
     * Asks the Home scroll container to reveal the latest projected answer after an explicit
     * shopper action. The bounded rectangle keeps a long result from forcing the whole card into
     * a tiny viewport, while all shopping authority remains in the immutable render state.
     */
    fun revealProjectedResult() {
        if (!projectedResultVisible) return

        // Rendering replaces result children and may request a new layout. Waiting for that
        // layout avoids scrolling to an old/zero-height result when the answer first appears.
        resultContainer.doOnLayout {
            if (!projectedResultVisible) return@doOnLayout
            val revealHeight = minOf(resultContainer.height, dp(320))
            if (resultContainer.width <= 0 || revealHeight <= 0) return@doOnLayout
            resultContainer.requestRectangleOnScreen(
                Rect(0, 0, resultContainer.width, revealHeight),
                true
            )
        }
    }

    private fun submit() {
        // The IME action can arrive independently of the visible button click. Keep
        // both entry points on the same immutable readiness boundary.
        if (!submitButton.isEnabled || onSubmit == null) {
            hideKeyboard()
            return
        }
        onSubmit?.invoke(input.text?.toString().orEmpty())
        hideKeyboard()
    }

    private fun syncQuery(query: String) {
        if (input.text?.toString() == query) return
        suppressInputCallback = true
        input.setText(query)
        input.setSelection(query.length)
        suppressInputCallback = false
    }

    private fun syncQueryCharacterLimit(limit: Int) {
        if (appliedQueryCharacterLimit == limit) return

        input.filters = arrayOf(InputFilter.LengthFilter(limit + 1))
        inputLayout.counterMaxLength = limit
        inputLayout.isCounterEnabled = true
        appliedQueryCharacterLimit = limit
    }

    private fun renderMessage(state: PracticalShoppingHomeRenderState) {
        val value = state.message
        if (value == null) {
            message.visibility = GONE
            return
        }
        message.text = value
        message.setTextColor(
            ValuePilotPalette.color(
                when (state.messageTone) {
                    PracticalShoppingHomeMessageTone.NEUTRAL -> "#6B7280"
                    PracticalShoppingHomeMessageTone.ACTION_REQUIRED -> "#92400E"
                    PracticalShoppingHomeMessageTone.ERROR -> "#B42318"
                }
            )
        )
        message.visibility = VISIBLE
    }

    private fun renderPrivateMemory(
        status: PracticalShoppingHomePrivateMemoryStatus,
        summary: String?,
        highlights: List<PracticalShoppingHomePrivateMemoryHighlight>,
        reviewActionVisible: Boolean,
        exportActionVisible: Boolean,
        forgetActionVisible: Boolean
    ) {
        if (status == PracticalShoppingHomePrivateMemoryStatus.UNAVAILABLE) {
            privateMemorySummary.text = ""
            privateMemorySummary.visibility = GONE
            privateMemoryHighlightsBody.removeAllViews()
            privateMemoryHighlightsCard.visibility = GONE
            privateMemoryReviewActionButton.visibility =
                if (reviewActionVisible) VISIBLE else GONE
            privateMemoryReviewActionButton.isEnabled =
                reviewActionVisible && onReviewPrivateMemory != null && hasRenderedState
            privateMemoryExportActionButton.visibility = GONE
            privateMemoryExportActionButton.isEnabled = false
            privateMemoryForgetActionButton.visibility = GONE
            privateMemoryForgetActionButton.isEnabled = false
            privateMemoryNotice.text = context.getString(R.string.home_private_memory_unavailable)
            privateMemoryNotice.visibility = VISIBLE
        } else {
            privateMemorySummary.text = summary.orEmpty()
            privateMemorySummary.visibility = if (summary == null) GONE else VISIBLE
            renderPrivateMemoryHighlights(highlights)
            privateMemoryReviewActionButton.visibility = if (reviewActionVisible) VISIBLE else GONE
            privateMemoryReviewActionButton.isEnabled =
                reviewActionVisible && onReviewPrivateMemory != null && hasRenderedState
            privateMemoryExportActionButton.visibility = if (exportActionVisible) VISIBLE else GONE
            privateMemoryExportActionButton.isEnabled =
                exportActionVisible && onExportPrivateMemory != null && hasRenderedState
            privateMemoryForgetActionButton.visibility = if (forgetActionVisible) VISIBLE else GONE
            privateMemoryForgetActionButton.isEnabled =
                forgetActionVisible && onForgetPrivateMemory != null && hasRenderedState
            privateMemoryNotice.text = ""
            privateMemoryNotice.visibility = GONE
        }
    }

    private fun renderPrivateMemoryHighlights(
        highlights: List<PracticalShoppingHomePrivateMemoryHighlight>
    ) {
        privateMemoryHighlightsBody.removeAllViews()
        if (highlights.isEmpty()) {
            privateMemoryHighlightsCard.visibility = GONE
            return
        }

        privateMemoryHighlightsBody.addView(
            line(context.getString(R.string.home_private_memory_highlights_title), 14f, "#1D4ED8", true)
        )
        privateMemoryHighlightsBody.addView(
            line(context.getString(R.string.home_private_memory_highlights_body), 12f, "#374151", topPadding = 4)
        )
        highlights.forEachIndexed { index, highlight ->
            privateMemoryHighlightsBody.addView(
                line(
                    highlight.displayName,
                    15f,
                    "#111827",
                    true,
                    topPadding = if (index == 0) 10 else 12
                )
            )
            privateMemoryHighlightsBody.addView(
                line(
                    context.getString(
                        R.string.home_private_memory_highlight_prices,
                        highlight.latestPriceText,
                        highlight.latestUnitRateText,
                        highlight.lowestPriceText
                    ),
                    13f,
                    "#374151",
                    topPadding = 3
                )
            )
            privateMemoryHighlightsBody.addView(
                line(
                    context.getString(
                        R.string.home_private_memory_highlight_detail,
                        if (highlight.observationCount == 1) {
                            "1 observation"
                        } else {
                            "${highlight.observationCount} observations"
                        },
                        highlight.rangeText,
                        highlight.latestObservedText
                    ),
                    12f,
                    "#6B7280",
                    topPadding = 2
                )
            )
            highlight.recentMovementText?.let { movement ->
                privateMemoryHighlightsBody.addView(
                    line(
                        context.getString(
                            R.string.home_private_memory_highlight_movement,
                            movement
                        ),
                        12f,
                        "#1D4ED8",
                        topPadding = 2
                    )
                )
            }
        }
        privateMemoryHighlightsCard.visibility = VISIBLE
    }

    /**
     * Applies the new consumer Home hierarchy to the existing immutable projection.
     * The older planner controls remain mounted for compatibility and are deliberately
     * hidden until an active basket needs the existing editor/refinement workflow.
     */
    private fun renderModernHome(state: PracticalShoppingHomeRenderState) {
        val presentation = PracticalShoppingHomePresentationPolicy.from(state)
        if (homeFlow.childCount == 0) {
            homeFlow.addView(line(context.getString(R.string.home_flow_search), 12f, ValuePilotDesignTokens.Colors.PRIMARY_DARK, true))
            homeFlow.addView(line("→", 16f, ValuePilotDesignTokens.Colors.TEXT_MUTED, true, 0).apply { setPadding(dp(8), 0, dp(8), 0) })
            homeFlow.addView(line(context.getString(R.string.home_flow_basket), 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, true))
            homeFlow.addView(line("→", 16f, ValuePilotDesignTokens.Colors.TEXT_MUTED, true, 0).apply { setPadding(dp(8), 0, dp(8), 0) })
            homeFlow.addView(line(context.getString(R.string.home_flow_shop), 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, true))
        }
        homeHeroCard.visibility = VISIBLE
        homePurpose.visibility = if (presentation.showWelcomePurpose) VISIBLE else GONE
        // The Search → Basket → Shop relationship is now implicit in the
        // compact controls; avoid spending the first viewport on a tutorial row.
        homeFlow.visibility = GONE
        homeSearchButton.isEnabled = onOpenSearch != null && hasRenderedState
        homeDemoBadge.visibility = if (state.sampleNotice.isNotBlank()) VISIBLE else GONE
        homeDemoBadge.contentDescription =
            state.sampleNotice.takeIf { it.isNotBlank() }
                ?.let { "Demo data. $it" }

        activeBasketCard.visibility = if (presentation.showActiveBasket) VISIBLE else GONE
        activeBasketCount.text =
            if (presentation.itemCount == 1) {
                context.getString(R.string.home_basket_one_item)
            } else {
                context.getString(R.string.home_basket_item_count, presentation.itemCount)
            }
        activeBasketSummary.text =
            if (presentation.collectibleIdentityCount > 0) {
                context.getString(
                    R.string.home_basket_collected,
                    presentation.collectedIdentityCount,
                    presentation.collectibleIdentityCount
                )
            } else {
                context.getString(R.string.home_basket_open_to_collect)
            }
        presentation.collectionProgress?.let { progress ->
            activeBasketProgress.progress = (progress * 100f).toInt().coerceIn(0, 100)
            activeBasketProgress.visibility = VISIBLE
        } ?: run {
            activeBasketProgress.progress = 0
            activeBasketProgress.visibility = GONE
        }
        activeBasketOpenButton.isEnabled = onOpenBasket != null && hasRenderedState

        // A returning user gets one contextual insight at a time. An active
        // basket takes precedence over the repeat-purchase prompt.
        buyAgainCard.visibility =
            if (presentation.showBuyAgain && !presentation.showActiveBasket) VISIBLE else GONE
        purchaseHistoryOwnerControls.forEach { control ->
            control.isEnabled =
                presentation.showBuyAgain && onOpenConfirmedPurchaseHistory != null && hasRenderedState
        }

        // First-time and returning Home never show the old planner dashboard or any
        // empty advanced module. Active-basket users retain the editor and clarification
        // rows, while results/price tools stay in Basket or product context.
        commandCenterCard.visibility = GONE
        fastActionsHeading.visibility = GONE
        compareActionButton.visibility = GONE
        goodPriceActionButton.visibility = GONE
        quickAddHeading.visibility = GONE
        quickAddGroup.visibility = GONE
        privateIdentityHeading.visibility = GONE
        privateIdentityCard.visibility = GONE
        sampleCardView.visibility = GONE
        privateMemorySummary.visibility = GONE
        privateMemoryHighlightsCard.visibility = GONE
        privateMemoryReviewActionButton.visibility = GONE
        privateMemoryExportActionButton.visibility = GONE
        privateMemoryForgetActionButton.visibility = GONE
        privateMemoryNotice.visibility = GONE
        shopAgainActionButton.visibility = GONE
        sharePlanActionButton.visibility = GONE
        extraStopSettingsButton.visibility = GONE
        extraStopSettingsCard.visibility = GONE
        noCoverageSummary.visibility = GONE
        resultContainer.visibility =
            if (presentation.showBasketEditor && state.result != null) VISIBLE else GONE

        inputLayout.visibility = if (presentation.showBasketEditor) VISIBLE else GONE
        submitButton.visibility = if (presentation.showBasketEditor) VISIBLE else GONE
        message.visibility = if (presentation.showBasketEditor) message.visibility else GONE
        itemsHeading.visibility = if (presentation.showBasketEditor) itemsHeading.visibility else GONE
        itemsContainer.visibility = if (presentation.showBasketEditor) VISIBLE else GONE
        refinementCard.visibility =
            if (presentation.showBasketEditor && state.refinement != null) VISIBLE else GONE
        unknownCard.visibility =
            if (presentation.showBasketEditor && state.unknownItems.isNotEmpty()) VISIBLE else GONE
    }

    private fun renderItems(items: List<PracticalShoppingHomeItemRenderState>) {
        itemsHeading.visibility = if (items.isEmpty()) GONE else VISIBLE
        if (lastRenderedItems == items) return
        itemsContainer.removeAllViews()
        items.forEach { item ->
            itemsContainer.addView(itemRow(item))
        }
        lastRenderedItems = items
    }

    private fun renderCommandCenter(state: PracticalShoppingHomeRenderState) {
        val itemCount = state.items.size
        val privateCount = state.privateIdentityItems.size
        val primary = state.result?.primary
        commandCenterTitle.text =
            when {
                primary != null -> "Your shop plan is ready"
                itemCount > 0 -> "Your list is ready"
                privateCount > 0 -> "Your saved list is ready"
                else -> "Start with your list"
            }
        commandCenterSummary.text =
            when {
                primary != null ->
                    "${primary.coverageText} · Check the details below before you shop."
                itemCount > 0 ->
                    "Check the list, then choose Plan my shop for the next step."
                privateCount > 0 ->
                    "Saved identities stay on this device and never imply price or availability."
                else ->
                    "Type a list in plain language. ValuePilot will keep uncertainty visible."
            }
        commandCenterListPill.text =
            if (itemCount == 0) {
                "No items yet"
            } else if (itemCount == 1) {
                "1 item"
            } else {
                "$itemCount items"
            }
        commandCenterStatusPill.text =
            when {
                primary != null -> "Ready to shop"
                state.result != null -> "Coverage needs review"
                privateCount > 0 -> "$privateCount private saved"
                else -> "No plan yet"
            }
        val historyVisible = state.confirmedPurchaseCount > 0
        commandCenterHistoryButton.visibility = if (historyVisible) VISIBLE else GONE
        commandCenterHistoryButton.text =
            context.getString(
                R.string.home_repeat_purchase_count,
                state.confirmedPurchaseCount
            )
        commandCenterHistoryButton.isEnabled =
            historyVisible && onOpenConfirmedPurchaseHistory != null && hasRenderedState
        val signature =
            listOf(
                itemCount,
                privateCount,
                state.confirmedPurchaseCount,
                state.result?.headline,
                primary?.coverageText
            ).joinToString("|")
        if (signature != lastCommandCenterSignature) {
            lastCommandCenterSignature = signature
            ValuePilotMotion.reveal(commandCenterCard)
        }
    }

    private fun renderPrivateIdentities(
        identities: List<PracticalShoppingHomePrivateIdentityRenderState>
    ) {
        privateIdentityBody.removeAllViews()
        privateIdentityHeading.visibility = if (identities.isEmpty()) GONE else VISIBLE
        privateIdentityCard.visibility = if (identities.isEmpty()) GONE else VISIBLE
        if (identities.isEmpty()) return

        privateIdentityBody.addView(
            line(
                context.getString(R.string.home_private_identity_body),
                13f,
                "#374151"
            )
        )
        identities.forEach { identity ->
            val label = buildString {
                append(identity.displayName)
                identity.brand?.let { append(" · ").append(it) }
                identity.packageQuantityLabel?.let { append(" · ").append(it) }
            }
            privateIdentityBody.addView(
                line(label, 15f, "#111827", true, topPadding = 12)
            )
            identity.sourceLabel?.let { source ->
                privateIdentityBody.addView(
                    line(
                        context.getString(R.string.home_private_identity_source, source),
                        12f,
                        "#6B7280",
                        topPadding = 3
                    )
                )
            }
            privateIdentityBody.addView(
                line(identity.notice, 12f, "#92400E", topPadding = 3)
            )
            privateIdentityBody.addView(
                priceWatchButton(
                    productIdentityKey = identity.identityKey,
                    displayName = identity.displayName,
                    brand = identity.brand,
                    packageQuantityLabel = identity.packageQuantityLabel,
                    watched = identity.watched
                )
            )
            privateIdentityBody.addView(
                privateIdentityPhotoButton(identity)
            )
            privateIdentityBody.addView(
                removeButton(
                    onRemove = { onRemovePrivateIdentity?.invoke(identity.identityKey) },
                    description = context.getString(
                        R.string.home_private_identity_remove_description,
                        identity.displayName
                    ),
                    enabled = onRemovePrivateIdentity != null,
                    ownerControls = privateIdentityRemovalOwnerControls
                ).apply {
                    layoutParams = fullWidth(
                        dp(ValuePilotDesignTokens.TouchTarget.COMPACT_MINIMUM_DP),
                        5
                    )
                }
            )
        }
    }

    private fun privateIdentityPhotoButton(
        identity: PracticalShoppingHomePrivateIdentityRenderState
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        privateIdentityPhotoOwnerControls += this
        text = context.getString(R.string.home_private_identity_photos)
        contentDescription = context.getString(
            R.string.home_private_identity_photos_description,
            identity.displayName
        )
        textSize = 13f
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
        isEnabled = onOpenPrivateIdentityPhotos != null
        setPadding(dp(10), 0, dp(10), 0)
        layoutParams = LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(8) }
        setOnClickListener { onOpenPrivateIdentityPhotos?.invoke(identity.identityKey) }
    }

    private fun renderQuickAdd(
        visible: Boolean,
        choices: List<PracticalShoppingHomeQuickAddRenderState>
    ) {
        lastRenderedQuickAddVisible = visible
        quickAddHeading.text = context.getString(R.string.home_quick_add_title)
        quickAddHeading.visibility = if (visible) VISIBLE else GONE
        quickAddGroup.removeAllViews()
        quickAddGroup.visibility = if (visible) VISIBLE else GONE
        if (!visible) return

        choices.forEach { option ->
            quickAddGroup.addView(
                Chip(context).apply {
                    quickAddOwnerControls += this
                    text = option.label
                    contentDescription =
                        context.getString(
                            R.string.home_quick_add_description,
                            option.label
                        )
                    isCheckable = false
                    isEnabled = onQuickAdd != null
                    setOnClickListener {
                        onQuickAdd?.invoke(option.choice)
                        hideKeyboard()
                    }
                }
            )
        }
    }

    private fun renderNoCoverageSummary(summary: String?) {
        noCoverageSummary.text = summary.orEmpty()
        noCoverageSummary.visibility = if (summary == null) GONE else VISIBLE
    }

    private fun renderShopAgain(visible: Boolean) {
        shopAgainActionButton.visibility = if (visible) VISIBLE else GONE
        shopAgainActionButton.isEnabled =
            visible && onShopAgain != null && hasRenderedState
    }

    private fun renderSharePlan(visible: Boolean) {
        sharePlanActionButton.visibility = if (visible) VISIBLE else GONE
        sharePlanActionButton.isEnabled =
            visible && onSharePlan != null && hasRenderedState
    }

    private fun itemRow(item: PracticalShoppingHomeItemRenderState): View =
        ValuePilotProductCard(context).apply {
            setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
            strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE)
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 8)
            addView(
                column().apply {
                    addView(
                        actionRow(
                            label = "${item.name}  •  ${item.detail}",
                            onRemove = { onRemoveItem?.invoke(item.key) },
                            removeEnabled = onRemoveItem != null,
                            removeOwnerControls = itemRemovalOwnerControls,
                            removeDescription =
                                context.getString(R.string.home_remove_item_description, item.name),
                            onDetails = { onEditItemDetails?.invoke(item.key) },
                            detailsEnabled = onEditItemDetails != null,
                            detailsOwnerControls = itemDetailsOwnerControls,
                            detailsLabel = item.requestDetailsActionLabel,
                            detailsDescription =
                                context.getString(
                                    R.string.home_item_details_action_description,
                                    item.name
                                )
                        )
                    )
                    addView(
                        priceWatchButton(
                            productIdentityKey = item.key.value,
                            displayName = item.name,
                            brand = null,
                            packageQuantityLabel = item.detail,
                            watched = item.watched,
                            ownerControls = itemPriceWatchOwnerControls
                        )
                    )
                    item.savedExactProductNotice?.let { notice ->
                        addView(line(notice, 12f, "#374151", topPadding = 2))
                    }
                    item.storeAssignment?.let { store ->
                        addView(
                            line(
                                practicalShoppingPlannedStoreLabel(store),
                                12f,
                                "#374151",
                                topPadding = 2
                            )
                        )
                        addView(
                            line(
                                PRACTICAL_SHOPPING_PLANNED_STORE_NOTICE,
                                12f,
                                "#6B7280",
                                topPadding = 2
                            )
                        )
                    }
                    item.plannedPriceText?.let { price ->
                        addView(line("Included in plan: $price", 12f, "#374151", topPadding = 2))
                    }
                    item.plannedPriceNotice?.let { notice ->
                        addView(line(notice, 12f, "#6B7280", topPadding = 2))
                    }
                    item.priceCoverageNotice?.let { notice ->
                        addView(line(notice, 12f, "#92400E", topPadding = 2))
                    }
                    item.personalHistoryNotice?.let { notice ->
                        addView(line(notice, 12f, "#6B7280", topPadding = 2))
                    }
                    if (item.observedPriceActionVisible) {
                        addView(
                            detailButton(
                                onDetails = { onAddObservedPrice?.invoke(item.key) },
                                label = context.getString(R.string.home_item_observed_price_action),
                                description =
                                    context.getString(
                                        R.string.home_item_observed_price_action_description,
                                        item.name
                                    ),
                                enabled = onAddObservedPrice != null,
                                ownerControls = observedPriceOwnerControls
                            )
                        )
                    }
                    addView(line(item.requestDetailsSummary, 12f, "#6B7280", topPadding = 2))
                    item.requestDetailsNotice?.let { notice ->
                        addView(line(notice, 12f, "#92400E", topPadding = 2))
                    }
                }
            )
        }

    private fun actionRow(
        label: String,
        onRemove: () -> Unit,
        removeEnabled: Boolean = false,
        removeOwnerControls: MutableList<View>? = null,
        lineColor: String = "#374151",
        removeDescription: String = context.getString(R.string.home_remove_item),
        onDetails: (() -> Unit)? = null,
        detailsEnabled: Boolean = false,
        detailsOwnerControls: MutableList<View>? = null,
        detailsLabel: String = context.getString(R.string.home_item_details_action),
        detailsDescription: String = detailsLabel
    ): View =
        LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 5)

            addView(line(label, 15f, lineColor, topPadding = 7))
            onDetails?.let {
                addView(
                    detailButton(
                        it,
                        detailsLabel,
                        detailsDescription,
                        detailsEnabled,
                        detailsOwnerControls
                    ).apply {
                        layoutParams = fullWidth(dp(48), 6)
                    }
                )
            }
            addView(
                removeButton(
                    onRemove,
                    removeDescription,
                    removeEnabled,
                    removeOwnerControls
                ).apply {
                    layoutParams = fullWidth(dp(48), 6)
                }
            )
        }

    private fun priceWatchButton(
        productIdentityKey: String,
        displayName: String,
        brand: String?,
        packageQuantityLabel: String?,
        watched: Boolean,
        ownerControls: MutableList<View> = priceWatchOwnerControls
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        ownerControls += this
        text = if (watched) "Unwatch" else "Watch price"
        contentDescription =
            if (watched) {
                "Stop watching $displayName; private history is retained"
            } else {
                "Watch exact product $displayName privately"
            }
        textSize = 13f
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
        isEnabled = onTogglePriceWatch != null
        setOnClickListener {
            onTogglePriceWatch?.invoke(
                PriceWatchUiAction.ToggleWatch(
                    productIdentityKey = productIdentityKey,
                    displayName = displayName,
                    brand = brand,
                    packageQuantityLabel = packageQuantityLabel,
                    watched = watched
                )
            )
        }
        layoutParams = fullWidth(LayoutParams.WRAP_CONTENT, 5)
    }

    private fun detailButton(
        onDetails: () -> Unit,
        label: String,
        description: String,
        enabled: Boolean,
        ownerControls: MutableList<View>? = null
    ): MaterialButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        ownerControls?.add(this)
        text = label
        contentDescription = description
        textSize = 14f
        minWidth = 0
        minimumWidth = 0
        insetTop = 0
        insetBottom = 0
        isEnabled = enabled
        setPadding(dp(8), 0, dp(8), 0)
        layoutParams = LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(4) }
        setOnClickListener { onDetails() }
    }

    private fun removeButton(
        onRemove: () -> Unit,
        description: String,
        enabled: Boolean,
        ownerControls: MutableList<View>? = null
    ): MaterialButton =
        ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
            ownerControls?.add(this)
            text = context.getString(R.string.home_remove_item)
            contentDescription = description
            textSize = 14f
            minWidth = 0
            minimumWidth = 0
            insetTop = 0
            insetBottom = 0
            isEnabled = enabled
            setPadding(dp(10), 0, dp(10), 0)
            setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_SECONDARY))
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8) }
            setOnClickListener { onRemove() }
        }

    private fun renderRefinement(state: PracticalShoppingHomeRefinementRenderState?) {
        refinementBody.removeAllViews()
        if (state == null) {
            refinementCard.visibility = GONE
            return
        }
        refinementBody.addView(line(context.getString(R.string.home_refinement_title), 11f, "#92400E", true))
        refinementBody.addView(
            line(
                context.getString(R.string.home_refinement_question, state.prompt),
                17f,
                "#111827",
                true,
                5
            )
        )
        refinementBody.addView(
            ChipGroup(context).apply {
                isSingleLine = false
                setPadding(0, dp(8), 0, 0)
                setChipSpacingHorizontal(dp(8))
                setChipSpacingVertical(dp(6))
                state.choices.forEach { option ->
                    addView(
                        Chip(context).apply {
                            chickenChoiceOwnerControls += this
                            text = option.label
                            contentDescription =
                                context.getString(
                                    R.string.home_chicken_choice_description,
                                    option.label,
                                    state.prompt
                                )
                            isCheckable = false
                            isEnabled = onChickenChoice != null
                            setOnClickListener {
                                onChickenChoice?.invoke(option.choice)
                                hideKeyboard()
                            }
                        }
                    )
                }
            }
        )
        refinementCard.visibility = VISIBLE
    }

    private fun renderUnknown(items: List<String>) {
        unknownBody.removeAllViews()
        if (items.isEmpty()) {
            unknownCard.visibility = GONE
            return
        }
        unknownBody.addView(line(context.getString(R.string.home_unknown_title), 13f, "#92400E", true))
        items.forEach { token ->
            unknownBody.addView(
                actionRow(
                    label = "• $token",
                    onRemove = { onRemoveUnknownItem?.invoke(token) },
                    removeEnabled = onRemoveUnknownItem != null,
                    removeOwnerControls = unknownRemovalOwnerControls,
                    lineColor = "#92400E",
                    removeDescription =
                        context.getString(R.string.home_remove_item_description, token)
                )
            )
        }
        unknownCard.visibility = VISIBLE
    }

    private fun renderExtraStopSettings(
        state: PracticalShoppingHomeExtraStopSettingsRenderState
    ) {
        extraStopSettingsButton.visibility = if (state.visible) VISIBLE else GONE
        // The disclosure itself is an owner-driven control too. Keep a detached
        // Home renderer inert even when an already-projected result makes the
        // settings panel visible; the option chips below use the same gate.
        extraStopSettingsButton.isEnabled =
            state.visible && onExtraStopMinimumSavingsChoice != null
        if (!state.visible || onExtraStopMinimumSavingsChoice == null) {
            // Do not leave a stale expanded panel on screen after its owner
            // disappears; a detached/reused renderer should look inert too.
            extraStopSettingsExpanded = false
        }
        currentExtraStopSettingsNotice = state.notice
        extraStopSettingsButton.text = state.summary
        syncExtraStopSettingsAccessibility()
        extraStopSettingsBody.removeAllViews()
        extraStopSettingsBody.addView(line(state.prompt, 15f, "#111827", true))
        state.notice?.let {
            extraStopSettingsBody.addView(line(it, 13f, "#92400E", topPadding = 8))
        }
        extraStopSettingsBody.addView(
            ChipGroup(context).apply {
                isSingleLine = false
                isSingleSelection = true
                setPadding(0, dp(8), 0, 0)
                setChipSpacingHorizontal(dp(8))
                setChipSpacingVertical(dp(6))
                state.choices.forEach { option ->
                    addView(
                        Chip(context).apply {
                            extraStopChoiceOwnerControls += this
                            text = option.label
                            contentDescription =
                                context.getString(
                                    R.string.home_extra_stop_choice_description,
                                    option.label
                                )
                            isCheckable = true
                            isChecked = option.selected
                            isEnabled = onExtraStopMinimumSavingsChoice != null
                            setOnClickListener {
                                onExtraStopMinimumSavingsChoice?.invoke(option.choice)
                            }
                        }
                    )
                }
            }
        )
        syncExtraStopSettingsVisibility()
    }

    private fun syncExtraStopSettingsAccessibility() {
        extraStopSettingsButton.contentDescription =
            if (extraStopSettingsButton.visibility == VISIBLE) {
                practicalShoppingExtraStopSettingsContentDescription(
                    summary = extraStopSettingsButton.text?.toString().orEmpty(),
                    expanded = extraStopSettingsExpanded,
                    notice = currentExtraStopSettingsNotice
                )
            } else {
                null
            }
    }

    private fun syncExtraStopSettingsVisibility() {
        extraStopSettingsCard.visibility =
            if (extraStopSettingsButton.visibility == VISIBLE && extraStopSettingsExpanded) {
                VISIBLE
            } else {
                GONE
            }
    }

    private fun sampleCard(): View =
        card(
            ValuePilotDesignTokens.Colors.SUCCESS_SURFACE,
            ValuePilotDesignTokens.Colors.SUCCESS_OUTLINE,
            20
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
                    sampleNotice.setPadding(0, dp(4), 0, 0)
                    addView(sampleNotice)
                }
            )
        }

    private fun compareButton(): MaterialButton = ValuePilotProgrammaticButton.outlined(context).apply {
        text = context.getString(R.string.home_compare_secondary)
        textSize = 14f
        layoutParams = fullWidth(dp(50), 12)
        // Keep the owner-driven comparison route inert until its callback is attached.
        isEnabled = false
        setOnClickListener { onCompare?.invoke() }
    }

    private fun shopAgainButton(): MaterialButton = ValuePilotProgrammaticButton.outlined(context).apply {
        shopAgainOwnerControls += this
        text = context.getString(R.string.home_shop_again)
        contentDescription = context.getString(R.string.home_shop_again_description)
        textSize = 14f
        layoutParams = fullWidth(dp(50), 12)
        // Keep this repeat-use action inert until the owner attaches and a
        // completed immutable result is rendered.
        isEnabled = false
        setOnClickListener { onShopAgain?.invoke() }
    }

    private fun sharePlanButton(): MaterialButton = ValuePilotProgrammaticButton.outlined(context).apply {
        sharePlanOwnerControls += this
        text = context.getString(R.string.home_share_plan)
        contentDescription = context.getString(R.string.home_share_plan_description)
        textSize = 14f
        layoutParams = fullWidth(dp(50), 8)
        // Sharing is an explicit owner-driven action over an already-projected result.
        isEnabled = false
        setOnClickListener { onSharePlan?.invoke() }
    }

    private fun goodPriceButton(): MaterialButton = ValuePilotProgrammaticButton.outlined(context).apply {
        goodPriceOwnerControls += this
        text = context.getString(R.string.home_good_price_secondary)
        textSize = 14f
        layoutParams = fullWidth(dp(50), 12)
        // Keep the owner-driven price question inert until its callback is attached.
        isEnabled = false
        setOnClickListener { onGoodPrice?.invoke() }
    }

    private fun privateMemoryReviewButton(): MaterialButton = ValuePilotProgrammaticButton.outlined(context).apply {
        privateMemoryReviewOwnerControls += this
        text = context.getString(R.string.home_private_memory_review)
        contentDescription = context.getString(R.string.home_private_memory_review_description)
        textSize = 14f
        layoutParams = fullWidth(dp(50), 8)
        // Keep the owner-driven history route inert until its callback is attached.
        isEnabled = false
        setOnClickListener { onReviewPrivateMemory?.invoke() }
    }

    private fun privateMemoryExportButton(): MaterialButton = ValuePilotProgrammaticButton.outlined(context).apply {
        privateMemoryExportOwnerControls += this
        text = context.getString(R.string.home_private_memory_export)
        contentDescription =
            context.getString(R.string.home_private_memory_export_description)
        textSize = 14f
        layoutParams = fullWidth(dp(50), 4)
        // Keep the owner-driven export route inert until its callback is attached.
        isEnabled = false
        setOnClickListener { onExportPrivateMemory?.invoke() }
    }

    private fun privateMemoryForgetButton(): MaterialButton = ValuePilotProgrammaticButton.outlined(context).apply {
        privateMemoryForgetOwnerControls += this
        text = context.getString(R.string.home_private_memory_forget)
        contentDescription =
            context.getString(R.string.home_private_memory_forget_description)
        textSize = 14f
        layoutParams = fullWidth(dp(50), 4)
        // Keep the owner-driven removal route inert until its callback is attached.
        isEnabled = false
        setOnClickListener { onForgetPrivateMemory?.invoke() }
    }

    private fun commandCenterPill(): TextView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.PRIMARY_DARK))
        setPadding(dp(10), dp(8), dp(10), dp(8))
        minHeight = dp(ValuePilotDesignTokens.TouchTarget.COMPACT_MINIMUM_DP)
        minimumHeight = dp(ValuePilotDesignTokens.TouchTarget.COMPACT_MINIMUM_DP)
        background = GradientDrawable().apply {
            setColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
            cornerRadius = dp(16).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(ValuePilotDesignTokens.Spacing.XS_DP)
        }
    }

    private fun heading(value: String): TextView = line(value, 13f, "#374151", true, 18)

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

    private fun bareColumn(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
    }

    private fun column(): LinearLayout = bareColumn().apply {
                setPadding(dp(12), dp(10), dp(12), dp(10))
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

    private fun hideKeyboard() {
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
