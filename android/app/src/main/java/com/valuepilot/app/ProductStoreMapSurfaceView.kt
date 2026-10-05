package com.valuepilot.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.valuepilot.core.ProductStoreMapPin

/**
 * Calm, tile-free renderer for one exact product's nearby stores.
 *
 * This view owns no matching, geography, price, freshness, or Trip Planner authority. It
 * receives the immutable projector state, lets the shopper select a pin-equivalent row, and
 * emits only the existing store pin through the two contextual actions.
 */
internal class ProductStoreMapSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {

    var onClose: (() -> Unit)? = null
    var onTripCost: ((ProductStoreMapPin) -> Unit)? = null
    var onDirections: ((ProductStoreMapPin) -> Unit)? = null
    var onUseForegroundLocation: (() -> Unit)? = null

    private var selectedStoreKey: String? = null
    private var currentState: ProductStoreMapSurfaceState? = null

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(4), 0, dp(ValuePilotDesignTokens.Spacing.MD_DP))
    }
    private val eyebrow = textLine(
        value = "NEARBY STORES",
        sizeSp = 11f,
        color = ValuePilotDesignTokens.Colors.PRIMARY_DARK,
        bold = true
    )
    private val title = textLine(
        value = "",
        sizeSp = 21f,
        color = ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        bold = true,
        topPadding = 6
    )
    private val packageText = textLine(
        value = "",
        sizeSp = 13f,
        color = ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = 4
    )
    private val status = textLine(
        value = "",
        sizeSp = 15f,
        color = ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
        bold = true,
        topPadding = 14
    )
    private val support = textLine(
        value = "",
        sizeSp = 13f,
        color = ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
        topPadding = 4
    )
    private val locationState = textLine(
        value = "",
        sizeSp = 12f,
        color = ValuePilotDesignTokens.Colors.TEXT_MUTED,
        topPadding = 7
    )
    private val mapFrame = FrameLayout(context).apply {
        // The map owns the useful viewport; the store list and selected-store
        // context stay below it as progressive disclosure.
        layoutParams = FrameLayout.LayoutParams(-1, dp(320))
        setBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE_SUBTLE))
        contentDescription = "Store map"
    }
    private val mapCard = ValuePilotProductCard(context).apply {
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(ValuePilotDesignTokens.Spacing.MD_DP)
        }
        radius = dp(ValuePilotDesignTokens.Shape.NORMAL_COMPONENT_RADIUS_DP).toFloat()
        strokeWidth = 0
        setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE_SUBTLE))
        addView(mapFrame)
    }
    private val mapCaption = textLine(
        value = "Store positions · straight-line estimate only · no road route",
        sizeSp = 12f,
        color = ValuePilotDesignTokens.Colors.TEXT_MUTED,
        topPadding = 7
    )
    private val selectedStoreContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(ValuePilotDesignTokens.Spacing.MD_DP)
        }
    }
    private val pins = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(ValuePilotDesignTokens.Spacing.SM_DP)
        }
    }
    private val foregroundLocationButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        text = "Use my location"
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(ValuePilotDesignTokens.Spacing.LG_DP)
        }
        contentDescription =
            "Use one foreground location reading to sort nearby stores approximately; no background tracking"
        setOnClickListener { onUseForegroundLocation?.invoke() }
    }
    private val closeButton = ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
        text = "Back to Search"
        contentDescription = "Return to Search without changing the selected product"
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(ValuePilotDesignTokens.Spacing.SM_DP)
        }
        setOnClickListener { onClose?.invoke() }
    }

    init {
        addView(content)
        content.addView(eyebrow)
        content.addView(title)
        content.addView(packageText)
        content.addView(status)
        content.addView(support)
        content.addView(locationState)
        content.addView(mapCard)
        content.addView(mapCaption)
        content.addView(selectedStoreContainer)
        content.addView(pins)
        content.addView(foregroundLocationButton)
        content.addView(closeButton)
    }

    fun showLoading(
        productName: String,
        packageQuantityLabel: String? = null
    ) {
        selectedStoreKey = null
        render(ProductStoreMapProjector.loading(productName, packageQuantityLabel))
    }

    fun showFailure(
        productName: String,
        packageQuantityLabel: String? = null
    ) {
        selectedStoreKey = null
        render(ProductStoreMapProjector.unavailable(productName, packageQuantityLabel))
    }

    fun render(state: ProductStoreMapSurfaceState) {
        currentState = state
        if (selectedStoreKey != null && state.pins.none { it.storeKey.value == selectedStoreKey }) {
            selectedStoreKey = null
        }
        title.text = state.productName
        packageText.text = state.packageQuantityLabel?.let { "Package · $it" }.orEmpty()
        packageText.visibility = if (state.packageQuantityLabel == null) GONE else VISIBLE
        status.text = state.headline
        support.text = state.support
        locationState.text = state.locationLabel
        mapFrame.removeAllViews()
        if (state.pins.isEmpty()) {
            mapFrame.addView(
                textLine(
                    value = if (state.mode == ProductStoreMapSurfaceMode.LOADING) {
                        "Finding store markers…"
                    } else {
                        "No store markers"
                    },
                    sizeSp = 14f,
                    color = ValuePilotDesignTokens.Colors.TEXT_MUTED,
                    bold = true
                ).apply {
                    gravity = Gravity.CENTER
                    contentDescription = "No nearby store markers"
                },
                FrameLayout.LayoutParams(-1, -1)
            )
        } else {
            mapFrame.addView(
                CoordinatePlotView(context).apply {
                    setPins(state.pins)
                    setSelectedStoreKey(selectedStoreKey)
                    contentDescription =
                        "Map showing ${state.pins.size} store markers for ${state.productName}. Select a store below."
                }
            )
        }
        selectedStoreContainer.removeAllViews()
        state.pins.firstOrNull { it.storeKey.value == selectedStoreKey }?.let { pin ->
            selectedStoreContainer.addView(selectedStoreCard(pin, state.productName))
        }
        pins.removeAllViews()
        state.pins.forEachIndexed { index, pin ->
            pins.addView(pinRow(index + 1, pin))
        }
        foregroundLocationButton.isEnabled = onUseForegroundLocation != null
        closeButton.isEnabled = onClose != null
        ValuePilotMotion.reveal(mapCard, animate = state.mode != ProductStoreMapSurfaceMode.LOADING)
    }

    fun restoreSelectedStore(storeKey: String?) {
        selectedStoreKey = storeKey
        currentState?.let(::render)
    }

    private fun selectStore(pin: ProductStoreMapPin) {
        selectedStoreKey = pin.storeKey.value
        currentState?.let(::render)
    }

    private fun selectedStoreCard(
        pin: ProductStoreMapPin,
        productName: String
    ): View =
        ValuePilotProductCard(context).apply {
            val origin = currentState?.origin ?: ProductStoreMapOrigin.CABA_FALLBACK
            setCardBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE_ACCENT))
            strokeColor = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.PRIMARY)
            strokeWidth = dp(2)
            contentDescription = buildString {
                append("Selected store: ").append(pin.storeName)
                ProductStoreMapProjector.distanceLabel(pin, origin)?.let { append(". ").append(it) }
                append(". Availability unknown")
            }
            val body = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            body.addView(textLine(pin.storeName, 17f, ValuePilotDesignTokens.Colors.TEXT_PRIMARY, bold = true))
            ProductStoreMapProjector.distanceLabel(pin, origin)?.let { distance ->
                body.addView(textLine(distance, 14f, ValuePilotDesignTokens.Colors.PRIMARY_DARK, bold = true, topPadding = 5))
            } ?: body.addView(textLine("Distance unavailable", 13f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 5))
            body.addView(textLine("For $productName · availability unknown", 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 5))
            pin.addressLabel?.let { address ->
                body.addView(TextView(context).apply {
                    text = address
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_MUTED))
                    setPadding(0, dp(5), 0, 0)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            body.addView(textLine(ProductStoreMapProjector.freshnessLabel(pin.freshness), 12f, ValuePilotDesignTokens.Colors.TEXT_MUTED, topPadding = 6))
            ProductStoreMapProjector.priceLabel(pin)?.let { price ->
                body.addView(textLine(price, 12f, ValuePilotDesignTokens.Colors.TEXT_SECONDARY, topPadding = 4))
            }
            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            actions.addView(
                ValuePilotProgrammaticButton.filled(context, compact = true).apply {
                    text = "Trip cost"
                    contentDescription = "Open Trip Planner for ${pin.storeName}"
                    isEnabled = onTripCost != null
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        marginEnd = dp(4)
                    }
                    setOnClickListener { onTripCost?.invoke(pin) }
                }
            )
            actions.addView(
                ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
                    text = "Directions"
                    contentDescription = "Open external directions for ${pin.storeName}"
                    isEnabled = onDirections != null
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                        marginStart = dp(4)
                    }
                    setOnClickListener { onDirections?.invoke(pin) }
                }
            )
            body.addView(actions)
            addView(body)
        }

    private fun pinRow(index: Int, pin: ProductStoreMapPin): View =
        ValuePilotProductCard(context).apply {
            val origin = currentState?.origin ?: ProductStoreMapOrigin.CABA_FALLBACK
            val selected = pin.storeKey.value == selectedStoreKey
            setCardBackgroundColor(
                ValuePilotPalette.color(
                    if (selected) ValuePilotDesignTokens.Colors.SURFACE_ACCENT
                    else ValuePilotDesignTokens.Colors.SURFACE
                )
            )
            strokeColor = ValuePilotPalette.color(
                if (selected) ValuePilotDesignTokens.Colors.PRIMARY
                else ValuePilotDesignTokens.Colors.OUTLINE
            )
            strokeWidth = dp(if (selected) 2 else 1)
            cardElevation = if (selected) dp(2).toFloat() else 0f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(ValuePilotDesignTokens.Spacing.XS_DP)
            }
            contentDescription = buildString {
                append("Store ").append(index).append(": ").append(pin.storeName)
                ProductStoreMapProjector.distanceLabel(pin, origin)?.let { append(". ").append(it) }
                if (selected) append(". Selected") else append(". Double tap to select")
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { selectStore(pin) }
            val body = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            body.addView(
                textLine(
                    value = if (selected) "Selected · ${pin.storeName}" else pin.storeName,
                    sizeSp = 16f,
                    color = ValuePilotDesignTokens.Colors.TEXT_PRIMARY,
                    bold = true
                )
            )
            ProductStoreMapProjector.distanceLabel(pin, origin)?.let { distance ->
                body.addView(textLine(distance, 12f, ValuePilotDesignTokens.Colors.PRIMARY_DARK, topPadding = 4))
            }
            body.addView(
                textLine(
                    value = if (selected) "Selected store" else "Tap to select",
                    sizeSp = 12f,
                    color = ValuePilotDesignTokens.Colors.TEXT_SECONDARY,
                    topPadding = 5
                )
            )
            addView(body)
        }

    private fun textLine(
        value: String,
        sizeSp: Float,
        color: String,
        bold: Boolean = false,
        topPadding: Int = 0
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(ValuePilotPalette.color(color))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        if (topPadding > 0) setPadding(0, dp(topPadding), 0, 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class CoordinatePlotView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var values: List<ProductStoreMapPin> = emptyList()
        private var selectedStoreKey: String? = null

        fun setPins(next: List<ProductStoreMapPin>) {
            values = next
            invalidate()
        }

        fun setSelectedStoreKey(value: String?) {
            selectedStoreKey = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE_SUBTLE)
            paint.strokeWidth = 2f
            val inset = 24f
            val width = width.toFloat()
            val height = height.toFloat()
            canvas.drawRoundRect(RectF(inset, inset, width - inset, height - inset), 18f, 18f, paint)
            if (values.isEmpty()) return
            val latitudes = values.map { it.coordinate.latitudeE7.toDouble() }
            val longitudes = values.map { it.coordinate.longitudeE7.toDouble() }
            val minLat = latitudes.minOrNull() ?: 0.0
            val maxLat = latitudes.maxOrNull() ?: 0.0
            val minLon = longitudes.minOrNull() ?: 0.0
            val maxLon = longitudes.maxOrNull() ?: 0.0
            val latSpan = (maxLat - minLat).coerceAtLeast(1.0)
            val lonSpan = (maxLon - minLon).coerceAtLeast(1.0)
            values.forEachIndexed { index, pin ->
                val x = inset + ((pin.coordinate.longitudeE7 - minLon) / lonSpan) * (width - 2 * inset)
                val y = height - inset - ((pin.coordinate.latitudeE7 - minLat) / latSpan) * (height - 2 * inset)
                val selected = pin.storeKey.value == selectedStoreKey
                paint.color = ValuePilotPalette.color(
                    if (selected) ValuePilotDesignTokens.Colors.PRIMARY_DARK
                    else ValuePilotDesignTokens.Colors.PRIMARY
                )
                canvas.drawCircle(x.toFloat(), y.toFloat(), if (selected) 13f else 9f, paint)
                paint.color = ValuePilotPalette.color(ValuePilotDesignTokens.Colors.ON_PRIMARY)
                paint.textSize = 11f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText((index + 1).toString(), x.toFloat(), y.toFloat() + 4f, paint)
            }
        }
    }
}
