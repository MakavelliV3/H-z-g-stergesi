package com.emir.hizgostergesi

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/** Grafiklerde ortak: dokunulan yerin üstüne küçük bir bilgi balonu çizer. */
private fun drawTooltip(canvas: Canvas, view: View, text: String, cx: Float, top: Float, paint: Paint, bg: Int, fg: Int) {
    val d = view.resources.displayMetrics.density
    paint.textSize = 12 * d
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val lines = text.split("\n")
    val w = (lines.maxOf { paint.measureText(it) } + 20 * d)
    val lineH = 15 * d
    val h = lines.size * lineH + 10 * d
    var left = cx - w / 2
    left = left.coerceIn(0f, (view.width - w).coerceAtLeast(0f))
    val boxTop = (top - h - 6 * d).coerceAtLeast(0f)
    paint.color = bg
    paint.style = Paint.Style.FILL
    canvas.drawRoundRect(RectF(left, boxTop, left + w, boxTop + h), 10 * d, 10 * d, paint)
    paint.color = fg
    lines.forEachIndexed { i, l -> canvas.drawText(l, left + w / 2, boxTop + 5 * d + (i + 1) * lineH - 4 * d, paint) }
    paint.typeface = Typeface.DEFAULT
}

/**
 * Yay şeklinde, logaritmik ölçekli canlı hız göstergesi. Değer değişince ibre yumuşakça kayar.
 */
class SpeedGauge(c: Context) : View(c) {
    var bits = false
        set(v) {
            field = v
            invalidate()
        }
    var caption = "İndirme"

    private var speed = 0L
    private var shown = 0f
    private var animator: ValueAnimator? = null

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweep = 270f
    private val start = 135f

    init {
        contentDescription = "Anlık hız göstergesi"
    }

    /** 0..1 arası konum: 1 KB/s (ya da 1 Kbit/s) ile 1 Gbit/s arası logaritmik. */
    private fun fraction(bps: Long): Float {
        val v = if (bits) bps * 8.0 else bps.toDouble()
        val lo = 1_000.0
        val hi = if (bits) 1e9 else 125e6
        if (v <= lo) return (v / lo * 0.04).toFloat().coerceIn(0f, 0.04f)
        return ((log10(v) - log10(lo)) / (log10(hi) - log10(lo))).toFloat().coerceIn(0f, 1f)
    }

    fun setSpeed(bps: Long) {
        speed = bps
        val target = fraction(bps)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, target).apply {
            duration = 450
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        contentDescription = "İndirme hızı ${Fmt.speed(bps, bits)}"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * 0.78f).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val stroke = 14 * d
        val size = minOf(width.toFloat(), height / 0.86f) - stroke * 2
        val cx = width / 2f
        val cy = stroke + size / 2
        val rect = RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)

        arcPaint.strokeWidth = stroke
        arcPaint.shader = null
        arcPaint.color = 0x33FFFFFF
        canvas.drawArc(rect, start, sweep, false, arcPaint)

        // İlerleme yayı: beyazdan yarı saydama geçiş
        val positions = floatArrayOf(0f, 0.75f)
        arcPaint.shader = SweepGradient(cx, cy, intArrayOf(0x99FFFFFF.toInt(), Color.WHITE), positions).also {
            val m = android.graphics.Matrix()
            m.setRotate(start - 2, cx, cy)
            it.setLocalMatrix(m)
        }
        if (shown > 0.001f) canvas.drawArc(rect, start, sweep * shown, false, arcPaint)
        arcPaint.shader = null

        // Uçtaki parlak nokta
        val ang = Math.toRadians((start + sweep * shown).toDouble())
        val px = cx + (size / 2) * cos(ang).toFloat()
        val py = cy + (size / 2) * sin(ang).toFloat()
        dot.color = Color.WHITE
        canvas.drawCircle(px, py, stroke * 0.62f, dot)

        // Ölçek etiketleri
        text.color = 0x99FFFFFF.toInt()
        text.textSize = 10 * d
        text.typeface = Typeface.DEFAULT
        val labels = if (bits) listOf("1K", "10K", "100K", "1M", "10M", "100M", "1G") else listOf("1K", "10K", "100K", "1M", "10M", "100M")
        labels.forEachIndexed { i, l ->
            val f = i.toFloat() / (if (bits) 6f else 5.097f)
            if (f > 1f) return@forEachIndexed
            val a = Math.toRadians((start + sweep * f).toDouble())
            val r = size / 2 - stroke * 1.6f
            canvas.drawText(l, cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat() + 4 * d, text)
        }

        // Ortadaki değer
        val (num, unit) = Fmt.speedParts(speed, bits)
        text.color = Color.WHITE
        text.typeface = Typeface.DEFAULT_BOLD
        text.textSize = 46 * d
        canvas.drawText(num, cx, cy + 10 * d, text)
        text.typeface = Ui.MEDIUM
        text.textSize = 16 * d
        text.color = 0xE6FFFFFF.toInt()
        canvas.drawText(unit, cx, cy + 34 * d, text)
        text.textSize = 12 * d
        text.color = 0xB3FFFFFF.toInt()
        canvas.drawText(caption, cx, cy + size / 2 - 2 * d, text)
    }
}

/** Yumuşak çizgili, altı dolgulu mini hız grafiği. */
class Sparkline(c: Context) : View(c) {
    var data: LongArray = LongArray(0)
        set(v) {
            field = v
            invalidate()
        }
    var lineColor = Color.WHITE

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.size < 2 || width == 0) return
        val d = resources.displayMetrics.density
        val max = (data.maxOrNull() ?: 0L).coerceAtLeast(1024L).toFloat()
        val h = height - 3 * d
        val step = width.toFloat() / (data.size - 1)
        fun y(v: Long) = 2 * d + h - h * (v / max)

        val path = Path()
        path.moveTo(0f, y(data[0]))
        for (i in 1 until data.size) {
            val x0 = (i - 1) * step
            val x1 = i * step
            val mx = (x0 + x1) / 2
            path.cubicTo(mx, y(data[i - 1]), mx, y(data[i]), x1, y(data[i]))
        }
        val area = Path(path).apply {
            lineTo(width.toFloat(), height.toFloat())
            lineTo(0f, height.toFloat())
            close()
        }
        fill.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            (lineColor and 0x00FFFFFF) or 0x55000000, lineColor and 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        canvas.drawPath(area, fill)
        line.color = lineColor
        line.strokeWidth = 2.5f * d
        canvas.drawPath(path, line)
    }
}

/** Halka (donut) grafik. */
class DonutChart(c: Context) : View(c) {
    private var slices: List<Pair<Long, Int>> = emptyList()
    private var center = ""
    private var centerSub = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val primaryColor = Ui.primary(c)
    private val secondaryColor = Ui.secondary(c)
    private val track = Ui.outline(c)

    fun set(values: List<Pair<Long, Int>>, centerText: String, sub: String) {
        slices = values
        center = centerText
        centerSub = sub
        contentDescription = "$sub: $centerText"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val stroke = 18 * d
        val size = minOf(width, height) - stroke
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val rect = RectF(left, top, left + size, top + size)
        paint.strokeWidth = stroke
        paint.color = track
        canvas.drawArc(rect, 0f, 360f, false, paint)

        val total = slices.sumOf { it.first }.toFloat()
        if (total > 0) {
            var start = -90f
            slices.forEach { (v, color) ->
                val sweep = 360f * v / total
                paint.color = color
                canvas.drawArc(rect, start, (sweep - 1.5f).coerceAtLeast(0.5f), false, paint)
                start += sweep
            }
        }
        textPaint.color = primaryColor
        textPaint.textSize = 18 * d
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(center, width / 2f, height / 2f + 2 * d, textPaint)
        textPaint.color = secondaryColor
        textPaint.textSize = 11 * d
        textPaint.typeface = Typeface.DEFAULT
        canvas.drawText(centerSub, width / 2f, height / 2f + 18 * d, textPaint)
    }
}

/**
 * Üst üste iki dilimli çubuk grafik (ör. mobil + Wi-Fi). Bir çubuğa dokununca değeri balonda gösterir.
 */
class BarChart(c: Context) : View(c) {
    var data: List<Triple<String, Long, Long>> = emptyList()
        set(v) {
            field = v
            selected = -1
            invalidate()
        }
    var mobileColor = 0xFFFF9800.toInt()
    var wifiColor = 0xFF2196F3.toInt()
    var textColor = 0xFF888888.toInt()

    /** Üstteki "en yüksek" yazısı. */
    var topLabel: (Long) -> String = { "En yüksek: ${Fmt.bytes(it)}" }

    /** Dokunulan çubuk için balon metni. */
    var tooltip: (Int) -> String = { i ->
        val (l, m, w) = data[i]
        "$l: ${Fmt.bytes(m + w)}"
    }

    /** Çok çubuk varken her kaçıncı çubuğun altına etiket yazılsın. */
    var labelEvery = 5

    private var selected = -1
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tipBg = Ui.primary(c)
    private val tipFg = Ui.surface(c)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (data.isEmpty()) return false
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val i = (e.x / (width.toFloat() / data.size)).toInt().coerceIn(0, data.lastIndex)
                if (i != selected) {
                    selected = i
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return
        val d = resources.displayMetrics.density
        val topPad = 34 * d
        val labelH = 18 * d
        val chartH = height - topPad - labelH
        val bottom = topPad + chartH
        val max = data.maxOf { it.second + it.third }.coerceAtLeast(1L)
        val slot = width.toFloat() / data.size
        val barW = (slot * 0.62f).coerceAtLeast(2 * d)
        val r = minOf(barW / 2, 6 * d)

        paint.textSize = 10 * d
        paint.style = Paint.Style.FILL
        data.forEachIndexed { i, (label, m, w) ->
            val cx = slot * i + slot / 2
            val left = cx - barW / 2
            val right = cx + barW / 2
            val mh = chartH * m / max
            val wh = chartH * w / max
            val dim = selected >= 0 && i != selected
            paint.alpha = 255
            if (m + w == 0L) {
                paint.color = textColor
                paint.alpha = 40
                canvas.drawRoundRect(RectF(left, bottom - 2 * d, right, bottom), r, r, paint)
            } else {
                // Wi-Fi üstte, mobil altta; üstte yuvarlak köşe
                paint.color = wifiColor
                if (dim) paint.alpha = 90
                canvas.drawRoundRect(RectF(left, bottom - mh - wh, right, bottom), r, r, paint)
                paint.color = mobileColor
                if (dim) paint.alpha = 90
                if (mh > 0) canvas.drawRoundRect(RectF(left, bottom - mh, right, bottom), r, r, paint)
            }
            paint.alpha = 255
            if (data.size <= 12 || i % labelEvery == 0 || i == data.lastIndex || i == selected) {
                paint.color = textColor
                paint.textAlign = Paint.Align.CENTER
                paint.typeface = if (i == selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                canvas.drawText(label, cx, height - 4 * d, paint)
            }
        }
        paint.typeface = Typeface.DEFAULT
        if (selected in data.indices) {
            val cx = slot * selected + slot / 2
            val (_, m, w) = data[selected]
            drawTooltip(canvas, this, tooltip(selected), cx, bottom - chartH * (m + w) / max, paint, tipBg, tipFg)
        } else {
            paint.color = textColor
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(topLabel(max), 0f, 12 * d, paint)
        }
    }
}

/** İnce, yuvarlak uçlu yüzde çubuğu. */
class PercentBar(c: Context, private val fraction: Float, private val color: Int) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Ui.outline(c)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = height / 2f
        paint.color = track
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, paint)
        if (fraction <= 0f) return
        paint.color = color
        val w = (width * fraction.coerceIn(0f, 1f)).coerceAtLeast(height.toFloat())
        canvas.drawRoundRect(RectF(0f, 0f, w, height.toFloat()), r, r, paint)
    }
}

/** Mobil / Wi-Fi payını tek çubukta gösterir. */
class SplitBar(c: Context) : View(c) {
    private var a = 0L
    private var b = 0L
    var colorA = 0xFFFF9800.toInt()
    var colorB = 0xFF2196F3.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = Ui.outline(c)

    fun set(first: Long, second: Long) {
        a = first
        b = second
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = height / 2f
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = track
        canvas.drawRoundRect(rect, r, r, paint)
        val total = a + b
        if (total <= 0) return
        val split = width * (a.toFloat() / total)
        canvas.save()
        val clip = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
        canvas.clipPath(clip)
        paint.color = colorA
        canvas.drawRect(0f, 0f, split, height.toFloat(), paint)
        paint.color = colorB
        canvas.drawRect(split, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.restore()
    }
}

/** Haftanın günleri × günün saatleri ısı haritası; hücreye dokununca değeri gösterir. */
class HeatMap(c: Context) : View(c) {
    private var grid: Array<LongArray> = Array(7) { LongArray(24) }
    private var rows: Array<String> = emptyArray()
    private var sel: Pair<Int, Int>? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelColor = Ui.secondary(c)
    private val emptyColor = Ui.outline(c)
    private val tipBg = Ui.primary(c)
    private val tipFg = Ui.surface(c)

    var dayNames: Array<String> = emptyArray()

    fun setData(g: Array<LongArray>, labels: Array<String>) {
        grid = g
        rows = labels
        sel = null
        invalidate()
    }

    private fun geometry(): FloatArray {
        val d = resources.displayMetrics.density
        val left = 30 * d
        val topPad = 26 * d
        val bottomLabel = 16 * d
        return floatArrayOf(left, topPad, (width - left) / 24f, (height - bottomLabel - topPad) / 7f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val (left, top, cw, ch) = geometry().let { arrayOf(it[0], it[1], it[2], it[3]) }
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val h = ((e.x - left) / cw).toInt().coerceIn(0, 23)
                val r = ((e.y - top) / ch).toInt().coerceIn(0, 6)
                if (sel != r to h) {
                    sel = r to h
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val g = geometry()
        val left = g[0]
        val top = g[1]
        val cellW = g[2]
        val cellH = g[3]
        val gap = 1.5f * d
        val max = grid.maxOf { r -> r.maxOrNull() ?: 0L }.coerceAtLeast(1L)

        paint.textSize = 10 * d
        paint.textAlign = Paint.Align.LEFT
        for (r in 0 until 7) {
            paint.color = labelColor
            if (r < rows.size) canvas.drawText(rows[r], 0f, top + r * cellH + cellH / 2 + 4 * d, paint)
            for (h in 0 until 24) {
                val v = grid[r][h]
                paint.color = if (v == 0L) emptyColor else {
                    val t = sqrt(v.toDouble() / max).toFloat()
                    Color.argb((50 + 205 * t).toInt(), Color.red(Ui.ACCENT), Color.green(Ui.ACCENT), Color.blue(Ui.ACCENT))
                }
                val x = left + h * cellW
                val y = top + r * cellH
                canvas.drawRoundRect(RectF(x + gap, y + gap, x + cellW - gap, y + cellH - gap), 3 * d, 3 * d, paint)
            }
        }
        paint.color = labelColor
        paint.textAlign = Paint.Align.CENTER
        for (h in 0 until 24 step 6) {
            canvas.drawText("%02d".format(h), left + h * cellW + cellW / 2, height - 3 * d, paint)
        }
        sel?.let { (r, h) ->
            val x = left + h * cellW
            val y = top + r * cellH
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2 * d
            paint.color = Ui.primary(context)
            canvas.drawRoundRect(RectF(x + gap, y + gap, x + cellW - gap, y + cellH - gap), 3 * d, 3 * d, paint)
            paint.style = Paint.Style.FILL
            val name = dayNames.getOrElse(r) { rows.getOrElse(r) { "" } }
            drawTooltip(canvas, this, "$name %02d:00\n${Fmt.bytes(grid[r][h])}".format(h), x + cellW / 2, y, paint, tipBg, tipFg)
        }
    }
}
