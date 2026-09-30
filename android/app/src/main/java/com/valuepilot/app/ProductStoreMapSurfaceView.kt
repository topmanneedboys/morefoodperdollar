package com.valuepilot.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.valuepilot.core.ProductStoreMapFreshness
import com.valuepilot.core.ProductStoreMapPin
import com.valuepilot.core.ProductStoreMapResult
import com.valuepilot.core.ProductStoreMapResultStatus
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A provider-neutral, tile-free map surface.  It plots qualified store
 * coordinates and keeps the evidence explanation visible; it is intentionally
 * not a navigation map and never claims road distance, stock, or a price ranking.
 */
internal class ProductStoreMapSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {
    var onClose: (() -> Unit)? = null
    var onSelectStore: ((ProductStoreMapPin) -> Unit)? = null
    var onUseForegroundLocation: (() -> Unit)? = null

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(16))
    }
    private val title = TextView(context).apply {
        setTextColor(Color.rgb(17, 24, 39))
        textSize = 21f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }
    private val status = TextView(context).apply {
        setTextColor(Color.rgb(75, 85, 99))
        textSize = 13f
        setPadding(0, dp(8), 0, dp(8))
    }
    private val mapFrame = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(-1, dp(190)).apply { topMargin = dp(8) }
        setBackgroundColor(Color.rgb(255, 248, 244))
    }
    private val pins = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }
    private val foregroundLocationButton = MaterialButton(context).apply {
        text = "Use one-shot location"
        isAllCaps = false
        minHeight = dp(48)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
        contentDescription =
            "Use one foreground location reading to sort nearby stores approximately; no background tracking"
        setOnClickListener { onUseForegroundLocation?.invoke() }
    }

    init {
        addView(content)
        content.addView(title)
        content.addView(status)
        content.addView(mapFrame)
        content.addView(pins)
        content.addView(foregroundLocationButton)
        val close = MaterialButton(context).apply {
            text = "Close map"
            isAllCaps = false
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
            setOnClickListener { onClose?.invoke() }
        }
        content.addView(close)
    }

    fun showLoading(productName: String) {
        title.text = "Nearby stores for $productName"
        status.text = "Checking the qualified Argentina SEPA release around the Buenos Aires (CABA) search area… No live inventory is being inferred."
        mapFrame.removeAllViews()
        pins.removeAllViews()
    }

    fun showFailure(message: String) {
        title.text = "Nearby stores"
        status.text = message
        mapFrame.removeAllViews()
        pins.removeAllViews()
    }

    fun render(
        productName: String,
        result: ProductStoreMapResult,
        originLabel: String = "the qualified CABA search-area center (not device location)"
    ) {
        title.text = "Nearby stores for $productName"
        status.text = statusText(result, originLabel)
        mapFrame.removeAllViews()
        pins.removeAllViews()
        if (result.pins.isNotEmpty()) {
            mapFrame.addView(CoordinatePlotView(context).apply { setPins(result.pins) })
            result.pins.forEachIndexed { index, pin -> pins.addView(pinRow(index + 1, pin)) }
        }
    }

    private fun pinRow(index: Int, pin: ProductStoreMapPin): View =
        MaterialCardView(context).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            strokeColor = Color.rgb(232, 205, 191)
            strokeWidth = dp(1)
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            contentDescription = buildString {
                append("Store $index: ")
                append(pin.storeName)
                pin.addressLabel?.let { append(". $it") }
                pin.approximateDistanceMetres?.let { append(". Approximately ").append(formatDistance(it)).append(" straight-line distance") }
                append(". Availability unknown.")
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onSelectStore?.invoke(pin) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                addView(TextView(context).apply {
                    text = "$index. ${pin.storeName}"
                    textSize = 16f
                    setTextColor(Color.rgb(17, 24, 39))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                pin.addressLabel?.let { address ->
                    addView(TextView(context).apply {
                        text = address
                        textSize = 13f
                        setTextColor(Color.rgb(75, 85, 99))
                        setPadding(0, dp(4), 0, 0)
                    })
                }
                addView(TextView(context).apply {
                    text = buildString {
                        pin.approximateDistanceMetres?.let { append(formatDistance(it)).append(" straight-line · ") }
                        append("availability unknown")
                        append(" · ").append(freshnessText(pin.freshness))
                        pin.releaseDate?.let { append(" · release ").append(it) }
                    }
                    textSize = 12f
                    setTextColor(Color.rgb(75, 85, 99))
                    setPadding(0, dp(6), 0, 0)
                })
                if (pin.priceConflict) {
                    addView(TextView(context).apply {
                        text = "Conflicting price evidence at this store; price hidden."
                        textSize = 12f
                        setTextColor(Color.rgb(180, 68, 50))
                        setPadding(0, dp(4), 0, 0)
                    })
                } else {
                    pin.price?.let { price ->
                        addView(TextView(context).apply {
                            text = "Published release price: ${priceText(price.minorUnits, price.currencyCode, price.fractionDigits)} (not inventory)"
                            textSize = 12f
                            setTextColor(Color.rgb(75, 85, 99))
                            setPadding(0, dp(4), 0, 0)
                        })
                    }
                }
            })
        }

    private fun statusText(result: ProductStoreMapResult, originLabel: String): String =
        when (result.status) {
            ProductStoreMapResultStatus.READY ->
                "${result.pins.size} exact store relationship(s) around the Buenos Aires (CABA) search area. Approximate straight-line distance from $originLabel only; no road route or live stock. ${result.relationshipCount} qualified relationship(s) checked."
            ProductStoreMapResultStatus.NO_EXACT_RELATIONSHIP ->
                "No exact product-to-store relationship was returned for this identity. A missing relationship does not prove the product is unavailable."
            ProductStoreMapResultStatus.NO_VALID_COORDINATES ->
                "The exact identity has store evidence, but no valid coordinates. It is excluded from distance/map display."
        }

    private fun freshnessText(value: ProductStoreMapFreshness): String =
        when (value) {
            ProductStoreMapFreshness.FRESH -> "qualified release evidence"
            ProductStoreMapFreshness.STALE -> "stale release evidence · not current"
            ProductStoreMapFreshness.UNKNOWN -> "freshness unknown · not current"
        }

    private fun formatDistance(metres: Long): String =
        if (metres < 1_000) "$metres m" else "${BigDecimal.valueOf(metres).movePointLeft(3).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} km"

    private fun priceText(minor: Long, currency: String, fractionDigits: Int): String =
        "$currency ${BigDecimal.valueOf(minor).movePointLeft(fractionDigits).setScale(fractionDigits).toPlainString()}"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class CoordinatePlotView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var values: List<ProductStoreMapPin> = emptyList()

        fun setPins(next: List<ProductStoreMapPin>) {
            values = next
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.color = Color.rgb(234, 215, 204)
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
                paint.color = Color.rgb(143, 54, 32)
                canvas.drawCircle(x.toFloat(), y.toFloat(), 10f, paint)
                paint.color = Color.WHITE
                paint.textSize = 11f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText((index + 1).toString(), x.toFloat(), y.toFloat() + 4f, paint)
            }
        }
    }
}
