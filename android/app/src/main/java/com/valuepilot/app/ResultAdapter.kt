package com.valuepilot.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

@SuppressLint("SetTextI18n")
class ResultAdapter(
    private val context: Context,
    private val onItemClick: (ValueItem) -> Unit
) : ListAdapter<RankedItem, ResultAdapter.ResultViewHolder>(DIFF) {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder = ResultViewHolder(createRow())

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) = holder.bind(getItem(position))

    private fun createRow(): MaterialCardView {
        val card = MaterialCardView(context).apply {
            radius = dp(14).toFloat()
            cardElevation = dp(1).toFloat()
            strokeWidth = dp(1)
            setStrokeColor(0xffe5e7eb.toInt())
            setCardBackgroundColor(0xffffffff.toInt())
            isClickable = true
            isFocusable = true
            minimumHeight = dp(104)
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(12), dp(4), dp(12), dp(4))
            }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        fun textView(size: Float, color: Int, style: Int = Typeface.NORMAL): TextView = TextView(context).apply {
            textSize = size
            setTextColor(color)
            setTypeface(typeface, style)
            includeFontPadding = false
        }

        val title = textView(15f, 0xff111827.toInt(), Typeface.BOLD).apply {
            tag = TAG_TITLE
        }
        val quantity = textView(12f, 0xff4b5563.toInt()).apply {
            tag = TAG_QUANTITY
            setPadding(0, dp(3), 0, 0)
        }
        val metricRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val metric = textView(16f, 0xff0f766e.toInt(), Typeface.BOLD).apply { tag = TAG_METRIC }
        val exactness = textView(10f, 0xff4b5563.toInt(), Typeface.BOLD).apply {
            tag = TAG_EXACTNESS
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setBackgroundColor(0xfff3f4f6.toInt())
        }
        metricRow.addView(metric, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        metricRow.addView(exactness)
        val prices = textView(12f, 0xff374151.toInt()).apply {
            tag = TAG_PRICES
            setPadding(0, dp(5), 0, 0)
        }
        val availability = textView(11f, 0xff0f7b4d.toInt()).apply {
            tag = TAG_AVAILABILITY
            setPadding(0, dp(6), 0, 0)
        }

        content.addView(title)
        content.addView(quantity)
        content.addView(metricRow)
        content.addView(prices)
        content.addView(availability)
        card.addView(content)
        return card
    }

    inner class ResultViewHolder(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        private val title = card.findViewWithTag<TextView>(TAG_TITLE)
        private val quantity = card.findViewWithTag<TextView>(TAG_QUANTITY)
        private val metric = card.findViewWithTag<TextView>(TAG_METRIC)
        private val exactness = card.findViewWithTag<TextView>(TAG_EXACTNESS)
        private val prices = card.findViewWithTag<TextView>(TAG_PRICES)
        private val availability = card.findViewWithTag<TextView>(TAG_AVAILABILITY)

        fun bind(ranked: RankedItem) {
            val item = ranked.item
            title.text = "${ranked.rank} · ${item.name}"
            quantity.text = item.quantity?.display.orEmpty()
            quantity.visibility = if (item.quantity == null) View.GONE else View.VISIBLE
            metric.text = ranked.metricLabel
            exactness.text = ranked.exactnessLabel
            exactness.setTextColor(if (ranked.exactnessLabel == "Estimate") 0xff92400e.toInt() else 0xff4b5563.toInt())
            prices.text = buildString {
                append(ValueEngine.money(item.offer.currentPrice, item.offer.currency))
                item.offer.memberPrice?.let { append(" · Member ${ValueEngine.money(it, item.offer.currency)}") }
                item.offer.previousPrice?.takeIf { it > item.offer.currentPrice + .005 }?.let {
                    append(" · Was ${ValueEngine.money(it, item.offer.currency)}")
                }
            }
            availability.text = item.availability ?: "Tap to open item"
            availability.setTextColor(if (item.availability == "Out of stock") 0xffb42318.toInt() else 0xff0f7b4d.toInt())
            card.contentDescription = buildString {
                append("Rank ${ranked.rank}, ${item.name}, ${ranked.metricLabel}, ${prices.text}")
                item.availability?.let { append(", $it") }
                append(". Double tap to open this product.")
            }
            card.setOnClickListener { onItemClick(item) }
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG_TITLE = "vp_result_title"
        private const val TAG_QUANTITY = "vp_result_quantity"
        private const val TAG_METRIC = "vp_result_metric"
        private const val TAG_EXACTNESS = "vp_result_exactness"
        private const val TAG_PRICES = "vp_result_prices"
        private const val TAG_AVAILABILITY = "vp_result_availability"

        private val DIFF = object : DiffUtil.ItemCallback<RankedItem>() {
            override fun areItemsTheSame(oldItem: RankedItem, newItem: RankedItem): Boolean = oldItem.stableId == newItem.stableId
            override fun areContentsTheSame(oldItem: RankedItem, newItem: RankedItem): Boolean = oldItem == newItem
        }
    }
}
