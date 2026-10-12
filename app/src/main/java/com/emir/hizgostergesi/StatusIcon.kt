package com.emir.hizgostergesi

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * Durum çubuğu simgesini çizer. Android simgeyi tek renge boyar,
 * bu yüzden burada sadece şekil (yazı, boyut, düzen) belirlenir.
 */
object StatusIcon {
    const val MODE_DOWN = 0
    const val MODE_UP = 1
    const val MODE_TOTAL = 2
    const val MODE_BOTH = 3

    const val UNIT_FULL = 0
    const val UNIT_SHORT = 1
    const val UNIT_HIDDEN = 2

    private const val S = 96

    fun draw(p: SharedPreferences, down: Long, up: Long, bits: Boolean, ignoreIdle: Boolean = false): Bitmap {
        val mode = p.getInt(Prefs.SB_MODE, MODE_DOWN)
        val level = p.getInt(Prefs.SB_SIZE, 1).coerceIn(0, 2)
        val unit = p.getInt(Prefs.SB_UNIT, UNIT_FULL)
        val bold = p.getBoolean(Prefs.SB_BOLD, true)
        val hideIdle = p.getBoolean(Prefs.SB_HIDE_IDLE, false)

        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        // Boştayken gizle: tamamen şeffaf simge
        if (!ignoreIdle && hideIdle && down + up < 1024) return bmp

        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        if (mode == MODE_BOTH) {
            // Üstte indirme, altta yükleme
            val max = floatArrayOf(44f, 50f, 56f)[level]
            fit(canvas, paint, line(down, bits, unit), 24f, max)
            fit(canvas, paint, line(up, bits, unit), 72f, max)
            return bmp
        }

        val v = when (mode) {
            MODE_UP -> up
            MODE_TOTAL -> down + up
            else -> down
        }
        when (unit) {
            UNIT_FULL -> {
                val (num, u) = Fmt.speedParts(v, bits)
                val split = floatArrayOf(58f, 64f, 70f)[level]
                fit(canvas, paint, num, split / 2, split * 1.15f)
                fit(canvas, paint, u, split + (S - split) / 2, (S - split) * 1.1f)
            }
            UNIT_SHORT -> fit(canvas, paint, Fmt.compact(v, bits), S / 2f, floatArrayOf(60f, 74f, 90f)[level])
            else -> fit(canvas, paint, Fmt.speedParts(v, bits).first, S / 2f, floatArrayOf(66f, 80f, 96f)[level])
        }
        return bmp
    }

    private fun line(v: Long, bits: Boolean, unit: Int) =
        if (unit == UNIT_HIDDEN) Fmt.speedParts(v, bits).first else Fmt.compact(v, bits)

    /** Yazıyı genişliğe sığana kadar küçültüp verilen dikey merkeze çizer. */
    private fun fit(c: Canvas, p: Paint, text: String, cy: Float, maxSize: Float) {
        p.textSize = maxSize
        while (p.measureText(text) > S - 4 && p.textSize > 12f) p.textSize -= 2f
        val b = Rect()
        p.getTextBounds(text, 0, text.length, b)
        c.drawText(text, S / 2f, cy - b.exactCenterY(), p)
    }
}
