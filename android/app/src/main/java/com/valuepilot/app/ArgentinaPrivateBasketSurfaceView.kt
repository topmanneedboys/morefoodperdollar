package com.valuepilot.app

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

sealed interface ArgentinaPrivateBasketUiAction {
    data object PlanBasket : ArgentinaPrivateBasketUiAction
    data object OpenSearch : ArgentinaPrivateBasketUiAction
    data class OpenStore(val store: ArgentinaPrivateBasketStoreProjection) : ArgentinaPrivateBasketUiAction
    data class Remove(val identityKey: String) : ArgentinaPrivateBasketUiAction
}

/** Renders only the immutable private-basket projection; all decisions stay outside the View. */
class ArgentinaPrivateBasketSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onAction: ((ArgentinaPrivateBasketUiAction) -> Unit)? = null

    private val body = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        addView(body)
    }

    fun render(state: ArgentinaPrivateBasketRenderState) {
        body.removeAllViews()
        body.addView(card("#FFF7ED", "#FED7AA") {
            addView(text("Private basket", 12f, "#92400E", true))
            addView(text(state.headline, 23f, "#111827", true, 8))
            addView(text(state.guidance, 14f, "#4B5563", false, 7))
            addView(text(state.evidenceNotice, 12f, "#92400E", false, 8))
            state.errorNotice?.let { addView(text(it, 13f, "#B42318", true, 8)) }
        })

        val action = MaterialButton(context).apply {
            text = state.actionLabel
            isAllCaps = false
            minHeight = dp(52)
            contentDescription = state.actionLabel
            setOnClickListener {
                onAction?.invoke(
                    if (state.canPlan) ArgentinaPrivateBasketUiAction.PlanBasket
                    else ArgentinaPrivateBasketUiAction.OpenSearch
                )
            }
        }
        action.isEnabled = onAction != null
        body.addView(action)

        if (state.lines.isNotEmpty()) {
            body.addView(text("Selected products", 13f, "#374151", true, 18))
            state.lines.forEach { line -> body.addView(lineCard(line)) }
        }
        state.plan?.let { plan ->
            body.addView(text(plan.totalText ?: plan.headline, 21f, "#111827", true, 18))
            body.addView(text(plan.coverageText, 13f, "#374151", false, 5))
            plan.stores.forEach { store ->
                val button = MaterialButton(context).apply {
                    text = "Open ${store.storeName} in Maps"
                    isAllCaps = false
                    minHeight = dp(46)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    contentDescription = "Open ${store.storeName} in Maps"
                    setOnClickListener { onAction?.invoke(ArgentinaPrivateBasketUiAction.OpenStore(store)) }
                }
                button.isEnabled = onAction != null
                body.addView(button)
            }
            if (plan.mapMarkers.isNotEmpty()) {
                body.addView(text("Store map context", 15f, "#111827", true, 16))
                body.addView(
                    text(
                        "Exact store markers from the selected plan; coordinates are source evidence, not a driving route.",
                        12f,
                        "#6B7280",
                        false,
                        4
                    )
                )
                plan.mapMarkers.forEach { marker ->
                    body.addView(
                        text(
                            "• ${marker.storeName} · ${marker.productName} · ${marker.priceText}\n" +
                                "  storeKey ${marker.storeKey} · ${marker.latitude}, ${marker.longitude}",
                            13f,
                            "#374151",
                            false,
                            7
                        )
                    )
                }
            }
            body.addView(text(plan.notice, 12f, "#6B7280", false, 8))
        }
    }

    private fun lineCard(line: ArgentinaPrivateBasketLineProjection): View =
        card(if (line.missing) "#FFF7ED" else "#F8FAFC", if (line.missing) "#FED7AA" else "#E5E7EB") {
            addView(text(line.productName, 16f, "#111827", true))
            addView(text(line.packageQuantityText, 13f, "#374151", false, 5))
            if (line.missing) {
                addView(text("${line.availabilityText}. Search again to refresh this item.", 13f, "#92400E", false, 6))
            } else {
                addView(text(line.lineTotalText ?: "Price unknown", 15f, "#111827", true, 6))
                line.storeName?.let { addView(text("Store: $it", 13f, "#374151", false, 4)) }
                addView(text("${line.availabilityText}; price evidence only", 12f, "#6B7280", false, 4))
            }
            val remove = MaterialButton(context).apply {
                text = "Remove"
                isAllCaps = false
                minHeight = dp(42)
                contentDescription = "Remove ${line.productName} from private basket"
                setOnClickListener { onAction?.invoke(ArgentinaPrivateBasketUiAction.Remove(line.identityKey)) }
            }
            remove.isEnabled = onAction != null
            addView(remove)
        }

    private fun card(background: String, stroke: String, body: LinearLayout.() -> Unit): View =
        MaterialCardView(context).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ValuePilotPalette.color(background))
            strokeColor = ValuePilotPalette.color(stroke)
            strokeWidth = dp(1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(16), dp(15), dp(16), dp(15))
                body()
            })
        }

    private fun text(value: String, size: Float, color: String, bold: Boolean = false, top: Int = 0) =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            setTextColor(ValuePilotPalette.color(color))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (top > 0) setPadding(0, dp(top), 0, 0)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

data class ArgentinaPrivateBasketRenderState(
    val headline: String,
    val guidance: String,
    val evidenceNotice: String,
    val actionLabel: String,
    val canPlan: Boolean,
    val lines: List<ArgentinaPrivateBasketLineProjection>,
    val plan: ArgentinaPrivateBasketPlanProjection?,
    val errorNotice: String? = null
)

internal object ArgentinaPrivateBasketRenderer {
    fun render(
        identities: List<PracticalShoppingPrivateIdentity>,
        offersByIdentity: Map<String, List<ArgentinaBackendContract.SearchOffer>>,
        plan: ArgentinaPrivateBasketPlanProjection?,
        errorNotice: String? = null
    ): ArgentinaPrivateBasketRenderState {
        val lines = identities.map { identity ->
            val offer = offersByIdentity[identity.identityKey].orEmpty().firstOrNull {
                it.productEvidenceKey == identity.identityKey &&
                    it.freshnessStatus == ArgentinaBackendContract.Freshness.FRESH &&
                    it.quantity != null
            }
            if (offer == null) {
                ArgentinaPrivateBasketLineProjection(
                    identityKey = identity.identityKey,
                    productName = identity.displayName,
                    packageQuantityText = identity.packageQuantityLabel ?: "package quantity unknown",
                    packageCount = null,
                    lineTotalText = null,
                    storeKey = null,
                    storeName = null,
                    availabilityText = "Price evidence unknown",
                    missing = true
                )
            } else {
                ArgentinaPrivateBasketLineProjection(
                    identityKey = identity.identityKey,
                    productName = identity.displayName,
                    packageQuantityText = identity.packageQuantityLabel
                        ?: offer.quantity?.let { "${it.amount} ${it.unit}" }
                        ?: "package quantity unknown",
                    packageCount = null,
                    lineTotalText = "${offer.listPrice.currency} ${offer.listPrice.amount}",
                    storeKey = offer.storeKey,
                    storeName = offer.storeName,
                    availabilityText = "Current qualified price preview",
                    missing = false
                )
            }
        }
        val canPlan = lines.any { !it.missing }
        return ArgentinaPrivateBasketRenderState(
            headline = plan?.headline ?: "${identities.size} selected product${if (identities.size == 1) "" else "s"}",
            guidance = if (canPlan) {
                "Plan all products in one authoritative request. Missing or stale evidence stays unknown."
            } else {
                "Search each selected product again to attach fresh qualified price evidence."
            },
            evidenceNotice = "Prices are session-only evidence from Argentina SEPA / Precios Claros. Availability is always UNKNOWN.",
            actionLabel = if (canPlan) "Plan this basket" else "Open Search",
            canPlan = canPlan,
            lines = lines,
            plan = plan,
            errorNotice = errorNotice
        )
    }
}
