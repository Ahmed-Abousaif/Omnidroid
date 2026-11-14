package com.omnidroid.app.shared.displays

import android.app.Presentation
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.omnidroid.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Native views keep the static output independent of an Activity's Compose lifecycle. */
internal class SecondaryDisplayContent(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density

    private fun dp(value: Int) = (value * density).roundToInt()

    private val column =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
    private val icon =
        ImageView(context).apply {
            setImageDrawable(context.packageManager.getApplicationIcon(context.applicationInfo))
            contentDescription = context.getString(R.string.displays_brand)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    private val title =
        TextView(context).apply {
            setText(R.string.displays_placeholder_title)
            setTextColor(context.getColor(R.color.secondary_display_text))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(24), 0, dp(12))
        }
    private val message =
        TextView(context).apply {
            setText(R.string.displays_placeholder_message)
            setTextColor(context.getColor(R.color.secondary_display_secondary_text))
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
        }

    init {
        setBackgroundColor(context.getColor(R.color.secondary_display_background))
        isFocusable = false
        val brand =
            TextView(context).apply {
                setText(R.string.displays_brand)
                setTextColor(context.getColor(R.color.secondary_display_accent))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                letterSpacing = 0.14f
                gravity = Gravity.CENTER
                setPadding(0, dp(20), 0, 0)
            }
        column.addView(icon, LinearLayout.LayoutParams(dp(104), dp(104)))
        column.addView(brand)
        column.addView(title, LinearLayout.LayoutParams(-1, -2))
        column.addView(message, LinearLayout.LayoutParams(-1, -2))
        val center =
            FrameLayout(context).apply {
                addView(column, LayoutParams(dp(520), -2, Gravity.CENTER))
            }
        addView(
            ScrollView(context).apply {
                isFillViewport = true
                isFocusable = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(center, FrameLayout.LayoutParams(-1, -2))
            },
            LayoutParams(-1, -1),
        )
        setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            setPadding(
                max(dp(24), insets.systemWindowInsetLeft),
                max(dp(24), insets.systemWindowInsetTop),
                max(dp(24), insets.systemWindowInsetRight),
                max(dp(24), insets.systemWindowInsetBottom),
            )
            insets
        }
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        // Size before measuring children, so the very first frame cannot clip resized text.
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val compact = min(w, h) / density < 420
        val iconSize = dp(if (compact) 88 else 120)
        icon.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        column.layoutParams.apply {
            width = min(dp(560), max(1, w - paddingLeft - paddingRight - dp(16)))
        }
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 22f else 28f)
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compact) 15f else 18f)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

internal class PlaceholderPresentation(context: Context, display: Display) : Presentation(context, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.configurePlaceholderWindow()
        setContentView(SecondaryDisplayContent(this.context))
    }
}

@Suppress("DEPRECATION")
internal fun Window.configurePlaceholderWindow() {
    // No input or keep-screen-on flags: the real app retains controls and normal sleep behavior.
    addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    setBackgroundDrawableResource(R.color.secondary_display_background)
    decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
}
