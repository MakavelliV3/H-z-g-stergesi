package com.emir.hizgostergesi

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** Ekranları XML yazmadan kurmak için küçük yardımcılar. */
object Ui {
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun page(c: Context): Pair<ScrollView, LinearLayout> {
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(c, 20), dp(c, 16), dp(c, 20), dp(c, 32))
        }
        return ScrollView(c).apply { addView(root) } to root
    }

    fun text(
        c: Context, s: String, size: Float = 15f, bold: Boolean = false,
        topDp: Int = 0, color: Int? = null
    ) = TextView(c).apply {
        text = s
        textSize = size
        if (bold) typeface = Typeface.DEFAULT_BOLD
        if (color != null) setTextColor(color)
        setPadding(0, dp(c, topDp), 0, dp(c, 4))
    }

    fun header(c: Context, s: String) = text(c, s, 19f, bold = true, topDp = 26)

    fun hint(c: Context, s: String) = text(c, s, 13f).apply { alpha = 0.7f }

    /** Yan yana radyo düğmeleri; sığmazsa yatay kayar. */
    fun <T> choiceRow(c: Context, options: List<Pair<String, T>>, current: T, onPick: (T) -> Unit): View {
        val group = RadioGroup(c).apply { orientation = RadioGroup.HORIZONTAL }
        options.forEach { (label, value) ->
            group.addView(RadioButton(c).apply {
                id = View.generateViewId()
                text = label
                isChecked = value == current
                setOnClickListener { onPick(value) }
            })
        }
        return HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(group)
        }
    }

    /** Başlığında değeri gösteren kaydırma çubuğu. */
    fun seekRow(c: Context, title: String, lo: Int, hi: Int, current: Int, suffix: String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(c, 8), 0, 0)
        }
        val label = text(c, "$title: $current$suffix", 15f)
        val bar = SeekBar(c).apply {
            max = hi
            min = lo
            progress = current
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
