package com.emir.hizgostergesi

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Ekranları XML yazmadan, tutarlı bir görünümle kurmak için yardımcılar. */
object Ui {
    const val ACCENT = 0xFF4F8CFF.toInt()
    const val ACCENT_2 = 0xFF7B5CFF.toInt()

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun night(c: Context) =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun bg(c: Context) = if (night(c)) 0xFF0E1014.toInt() else 0xFFF2F3F7.toInt()
    fun surface(c: Context) = if (night(c)) 0xFF1A1D24.toInt() else 0xFFFFFFFF.toInt()
    fun primary(c: Context) = if (night(c)) 0xFFF1F3F7.toInt() else 0xFF15171C.toInt()
    fun secondary(c: Context) = if (night(c)) 0xFF9AA1AD.toInt() else 0xFF5E6573.toInt()
    fun outline(c: Context) = if (night(c)) 0xFF2C313B.toInt() else 0xFFDDE0E6.toInt()

    private val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())

    fun rounded(color: Int, radiusPx: Float, strokePx: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

    /** Dokununca dalga efekti veren arka plan. */
    fun ripple(content: GradientDrawable, rippleColor: Int = 0x33808080) =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, null)

    /**
     * Kaydırılabilir sayfa. edgeToEdge=true ise içerik durum çubuğunun altına taşmasın diye
     * sistem çubuklarının boyu kadar boşluk bırakılır.
     */
    fun page(a: Activity, edgeToEdge: Boolean = false): Pair<ScrollView, LinearLayout> {
        val p = dp(a, 16)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p, p, p * 2)
        }
        val scroll = ScrollView(a).apply {
            setBackgroundColor(bg(a))
            addView(root)
        }
        a.window.decorView.setBackgroundColor(bg(a))
        if (edgeToEdge) {
            if (Build.VERSION.SDK_INT >= 30) a.window.setDecorFitsSystemWindows(false)
            scroll.setOnApplyWindowInsetsListener { _, insets ->
                val top: Int
                val bottom: Int
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    top = bars.top
                    bottom = bars.bottom
                } else {
                    top = insets.systemWindowInsetTop
                    bottom = insets.systemWindowInsetBottom
                }
                root.setPadding(p, p + top, p, p * 2 + bottom)
                insets
            }
        }
        return scroll to root
    }

    /** Yuvarlak köşeli bölüm kartı. */
    fun card(c: Context, title: String? = null): LinearLayout {
        val pad = dp(c, 16)
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = rounded(surface(c), dp(c, 20).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(c, 12) }
            if (title != null) addView(text(c, title, 17f, bold = true).apply { setPadding(0, 0, 0, dp(c, 8)) })
        }
    }

    fun text(
        c: Context, s: String, size: Float = 15f, bold: Boolean = false,
        topDp: Int = 0, color: Int? = null
    ) = TextView(c).apply {
        text = s
        textSize = size
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setTextColor(color ?: primary(c))
        setPadding(0, dp(c, topDp), 0, dp(c, 4))
    }

    fun label(c: Context, s: String) = text(c, s, 13f, bold = true, topDp = 10, color = secondary(c))

    fun header(c: Context, s: String) = text(c, s, 19f, bold = true, topDp = 18)

    fun hint(c: Context, s: String) = text(c, s, 13f, color = secondary(c))

    /** Hap şeklinde düğme. filled=true: dolu mavi, false: hafif mavi. */
    fun button(c: Context, label: String, filled: Boolean = false, onClick: () -> Unit) = TextView(c).apply {
        text = label
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (filled) Color.WHITE else ACCENT)
        val v = dp(c, 12)
        setPadding(v, v, v, v)
        background = ripple(rounded(if (filled) ACCENT else 0x264F8CFF, dp(c, 50).toFloat()))
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(c, 10) }
    }

    /** Seçenekler hap düğmeler olarak; sığmazsa yatay kayar. */
    fun <T> choiceRow(c: Context, options: List<Pair<String, T>>, current: T, onPick: (T) -> Unit): View {
        val group = RadioGroup(c).apply { orientation = RadioGroup.HORIZONTAL }
        val radius = dp(c, 50).toFloat()
        options.forEach { (label, value) ->
            val chip = RadioButton(c).apply {
                id = View.generateViewId()
                text = label
                textSize = 14f
                buttonDrawable = null
                gravity = Gravity.CENTER
                setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_checked), rounded(ACCENT, radius))
                    addState(intArrayOf(), rounded(Color.TRANSPARENT, radius, dp(c, 1), outline(c)))
                }
                setTextColor(ColorStateList(checkedStates, intArrayOf(Color.WHITE, primary(c))))
                isChecked = value == current
                setOnClickListener { onPick(value) }
            }
            group.addView(chip, RadioGroup.LayoutParams(
                RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(c, 8) })
        }
        return HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(c, 2), 0, dp(c, 4))
            addView(group)
        }
    }

    fun switch(c: Context, label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(c).apply {
        text = label
        textSize = 15f
        setTextColor(primary(c))
        isChecked = checked
        thumbTintList = ColorStateList(checkedStates, intArrayOf(ACCENT, 0xFFBDBDBD.toInt()))
        trackTintList = ColorStateList(checkedStates, intArrayOf(0x804F8CFF.toInt(), 0x40888888))
        setPadding(0, dp(c, 8), 0, dp(c, 8))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    /** Başlığında değeri gösteren kaydırma çubuğu. */
    fun seekRow(c: Context, title: String, lo: Int, hi: Int, current: Int, suffix: String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(c, 8), 0, 0)
        }
        val label = text(c, "$title: $current$suffix", 14f)
        val bar = SeekBar(c).apply {
            max = hi
            min = lo
            progress = current
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(ACCENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                    label.text = "$title: $v$suffix"
                    if (fromUser) onChange(v)
                }

                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        box.addView(label)
        box.addView(bar)
        return box
    }
}
