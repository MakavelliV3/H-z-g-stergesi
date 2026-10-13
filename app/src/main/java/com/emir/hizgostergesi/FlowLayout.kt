package com.emir.hizgostergesi

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Çocukları yan yana dizer, sığmayanı alt satıra geçirir (hap seçenekleri için). */
class FlowLayout(c: Context, private val hGap: Int, private val vGap: Int) : ViewGroup(c) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val unbounded = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
        var x = 0
        var y = 0
        var rowH = 0
        var widest = 0
        for (i in 0 until childCount) {
            val ch = getChildAt(i)
            if (ch.visibility == View.GONE) continue
            ch.measure(MeasureSpec.makeMeasureSpec(maxOf(maxW, 0), MeasureSpec.AT_MOST).takeUnless { unbounded }
                ?: MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (!unbounded && x > 0 && x + ch.measuredWidth > maxW) {
                x = 0
                y += rowH + vGap
                rowH = 0
            }
            x += ch.measuredWidth + hGap
            widest = maxOf(widest, x - hGap)
            rowH = maxOf(rowH, ch.measuredHeight)
        }
        val w = if (unbounded) widest + paddingLeft + paddingRight else MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, y + rowH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val ch = getChildAt(i)
            if (ch.visibility == View.GONE) continue
            if (x > 0 && x + ch.measuredWidth > maxW) {
                x = 0
                y += rowH + vGap
                rowH = 0
            }
            ch.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + ch.measuredWidth, paddingTop + y + ch.measuredHeight)
            x += ch.measuredWidth + hGap
            rowH = maxOf(rowH, ch.measuredHeight)
        }
    }
}
