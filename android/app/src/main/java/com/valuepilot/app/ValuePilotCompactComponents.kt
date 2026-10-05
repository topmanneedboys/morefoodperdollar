package com.valuepilot.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Small, presentation-only building blocks for the consumer surfaces.
 *
 * These components deliberately know nothing about products, prices, stores or
 * navigation. A surface owns the immutable values and typed callbacks it binds
 * to a row or sheet. Keeping the mechanics here prevents each screen from
 * inventing another card/button language.
 */
internal class ValuePilotCompactRow @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val leading = ImageView(context).apply {
        layoutParams = LayoutParams(dp(32), dp(48)).apply {
            marginEnd = dp(ValuePilotDesignTokens.Spacing.XS_DP)
        }
        scaleType = ImageView.ScaleType.CENTER
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val primary = TextView(context).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_PRIMARY))
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        maxLines = 2
    }
    private val secondary = TextView(context).apply {
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, ValuePilotDesignTokens.Typography.LABEL_SP)
        setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_SECONDARY))
        maxLines = 2
        visibility = GONE
    }
    private val body = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
    }
    private val trailing = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        isFocusable = true
        body.addView(primary)
        body.addView(secondary)
        addView(leading)
        addView(body)
        addView(trailing)
    }

    /** Binds only presentation values and foreground callbacks supplied by the owner. */
    fun bind(
        primaryText: String,
        secondaryText: String? = null,
        leadingDrawable: Int? = null,
        trailingView: View? = null,
        bodyDescription: String = primaryText,
        onBodyClick: (() -> Unit)? = null
    ) {
        require(primaryText.isNotBlank())
        primary.text = primaryText
        secondary.text = secondaryText.orEmpty()
        secondary.visibility = if (secondaryText.isNullOrBlank()) GONE else VISIBLE
        if (leadingDrawable == null) {
            leading.visibility = GONE
        } else {
            leading.setImageResource(leadingDrawable)
            leading.visibility = VISIBLE
        }
        trailing.removeAllViews()
        trailingView?.let {
            it.minimumHeight = dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
            it.minimumWidth = dp(ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
            trailing.addView(it)
        }
        contentDescription = bodyDescription
        isClickable = onBodyClick != null
        setOnClickListener { onBodyClick?.invoke() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

internal object ValuePilotCompactUi {
    fun divider(context: Context): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 1)
        ).apply {
            leftMargin = dp(context, 44)
        }
        setBackgroundColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun overflowButton(
        context: Context,
        description: String,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        text = "⋯"
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
        setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_PRIMARY))
        gravity = Gravity.CENTER
        minWidth = dp(context, ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
        minHeight = dp(context, ValuePilotDesignTokens.TouchTarget.MINIMUM_DP)
        contentDescription = description
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick() }
    }

    fun statusChip(context: Context, text: String, warning: Boolean = false): TextView = TextView(context).apply {
        require(text.isNotBlank())
        this.text = text
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(
            ValuePilotPalette.color(
                if (warning) ValuePilotDesignTokens.Colors.WARNING
                else ValuePilotDesignTokens.Colors.PRIMARY_DARK
            )
        )
        setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(
                ValuePilotPalette.color(
                    if (warning) ValuePilotDesignTokens.Colors.WARNING_SURFACE
                    else ValuePilotDesignTokens.Colors.SURFACE_ACCENT
                )
            )
            cornerRadius = dp(context, 12).toFloat()
        }
        contentDescription = text
    }

    /** Shows contextual details without taking the parent route away. */
    fun showSheet(
        context: Context,
        title: String,
        subtitle: String? = null,
        bodyText: String? = null,
        actions: List<SheetAction> = emptyList()
    ): BottomSheetDialog {
        require(title.isNotBlank())
        require(actions.size <= 6)
        val dialog = BottomSheetDialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 20))
            background = ColorDrawable(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.SURFACE))
        }
        root.addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 36), dp(context, 4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(context, 12)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.OUTLINE))
                    cornerRadius = dp(context, 2).toFloat()
                }
                contentDescription = "Drag handle"
            }
        )
        root.addView(TextView(context).apply {
            text = title
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_PRIMARY))
        })
        subtitle?.takeIf { it.isNotBlank() }?.let { value ->
            root.addView(TextView(context).apply {
                text = value
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_SECONDARY))
                setPadding(0, dp(context, 4), 0, 0)
            })
        }
        bodyText?.takeIf { it.isNotBlank() }?.let { value ->
            root.addView(TextView(context).apply {
                text = value
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, ValuePilotDesignTokens.Typography.BODY_SP)
                setTextColor(ValuePilotPalette.color(ValuePilotDesignTokens.Colors.TEXT_SECONDARY))
                setPadding(0, dp(context, 12), 0, dp(context, 4))
            })
        }
        actions.forEach { action ->
            root.addView(
                ValuePilotProgrammaticButton.outlined(context, compact = true).apply {
                    text = action.label
                    contentDescription = action.contentDescription
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(context, 6) }
                    setOnClickListener {
                        action.onClick()
                        dialog.dismiss()
                    }
                }
            )
        }
        dialog.setContentView(root)
        dialog.setOnShowListener { ValuePilotMotion.reveal(root, animate = true) }
        dialog.show()
        return dialog
    }

    data class SheetAction(
        val label: String,
        val contentDescription: String = label,
        val onClick: () -> Unit
    ) {
        init {
            require(label.isNotBlank())
            require(contentDescription.isNotBlank())
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
