package com.emir.hizgostergesi

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Günlük istatistik (mobil / Wi-Fi ayrı) ve 7-30 günlük geçmiş grafiği. */
class HistoryActivity : Activity() {

    private val mobileColor = 0xFFFF9800.toInt()
    private val wifiColor = 0xFF2196F3.toInt()

    private lateinit var todayBox: LinearLayout
    private lateinit var chart: BarChart
    private lateinit var list: LinearLayout
    private var days = 7
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, root) = Ui.page(this)

        root.addView(Ui.header(this, "Bugün"))
        todayBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(todayBox)
        root.addView(Ui.hint(this, "Ortalama hız, sadece veri akarken geçen süreye göre hesaplanır (boşta geçen saniyeler sayılmaz)."))

        root.addView(Ui.header(this, "Geçmiş"))
        root.addView(Ui.choiceRow(this, listOf("Son 7 gün" to 7, "Son 30 gün" to 30), days) {
            days = it
            render()
        })
        val legend = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        legend.addView(Ui.text(this, "■ Mobil veri   ", 14f, color = mobileColor))
        legend.addView(Ui.text(this, "■ Wi-Fi", 14f, color = wifiColor))
        root.addView(legend)

        chart = BarChart(this).apply {
            mobileColor = this@HistoryActivity.mobileColor
            wifiColor = this@HistoryActivity.wifiColor
            textColor = TextView(this@HistoryActivity).currentTextColor
        }
        root.addView(chart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 200)))

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun render() {
        val bits = Prefs.get(this).getBoolean(Prefs.UNIT_BITS, false)
        val all = StatsStore.all(this)
        val today = StatsStore.today(this)

        // Bugün kartları
        todayBox.removeAllViews()
        todayBox.addView(card("📱 Mobil veri", mobileColor, today.mobile, today.peakMobile, today.avgMobile, bits))
        todayBox.addView(card("📶 Wi-Fi", wifiColor, today.wifi, today.peakWifi, today.avgWifi, bits))

        // Grafik: eskiden yeniye
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1))
        val dayNum = SimpleDateFormat("d", Locale("tr"))
        val bars = ArrayList<Triple<String, Long, Long>>()
        val keys = ArrayList<Pair<String, Long>>()
        repeat(days) {
            val key = StatsStore.key(cal.timeInMillis)
            val s = all[key]
            bars += Triple(dayNum.format(cal.time), s?.mobile ?: 0L, s?.wifi ?: 0L)
            keys += key to cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        chart.data = bars

        // Liste: yeniden eskiye
        list.removeAllViews()
        val title = SimpleDateFormat("d MMMM EEEE", Locale("tr"))
        var any = false
        keys.asReversed().forEach { (key, time) ->
            val s = all[key] ?: return@forEach
            if (s.mobile + s.wifi == 0L) return@forEach
            any = true
            list.addView(Ui.text(this, title.format(java.util.Date(time)), 15f, bold = true, topDp = 14))
            list.addView(Ui.text(this, line("Mobil", s.mobile, s.peakMobile, s.avgMobile, bits), 13f, color = mobileColor))
            list.addView(Ui.text(this, line("Wi-Fi", s.wifi, s.peakWifi, s.avgWifi, bits), 13f, color = wifiColor))
        }
        if (!any) list.addView(Ui.hint(this, "Henüz kayıt yok. Uygulama çalıştıkça günler burada birikir."))
    }

    private fun line(name: String, used: Long, peak: Long, avg: Long, bits: Boolean): String =
        "$name: ${Fmt.bytes(used)}  •  rekor ${Fmt.speed(peak, bits)}  •  ort. ${if (avg > 0) Fmt.speed(avg, bits) else "—"}"

    private fun card(title: String, color: Int, used: Long, peak: Long, avg: Long, bits: Boolean): View {
        val pad = Ui.dp(this, 14)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                cornerRadius = pad.toFloat()
                setStroke(Ui.dp(this@HistoryActivity, 2), color)
            }
        }
        box.addView(Ui.text(this, title, 17f, bold = true, color = color))
        box.addView(Ui.text(this, "Kullanım: ${Fmt.bytes(used)}", 15f))
        box.addView(Ui.text(this, "Rekor hız: ${Fmt.speed(peak, bits)}", 15f))
        box.addView(Ui.text(this, "Ortalama hız: ${if (avg > 0) Fmt.speed(avg, bits) else "—"}", 15f))
        box.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = Ui.dp(this@HistoryActivity, 10) }
        return box
    }
}

/** Mobil (altta) ve Wi-Fi (üstte) üst üste çubuk grafik. */
class BarChart(c: Context) : View(c) {
    var data: List<Triple<String, Long, Long>> = emptyList()
        set(v) {
            field = v
            invalidate()
        }
    var mobileColor = 0xFFFF9800.toInt()
    var wifiColor = 0xFF2196F3.toInt()
    var textColor = 0xFF888888.toInt()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return
        val d = resources.displayMetrics.density
        val topPad = 20 * d
        val labelH = 18 * d
        val chartH = height - topPad - labelH
        val bottom = topPad + chartH
        val max = data.maxOf { it.second + it.third }.coerceAtLeast(1L)
        val slot = width.toFloat() / data.size
        val barW = slot * 0.65f

        paint.textSize = 10 * d
        data.forEachIndexed { i, (label, m, w) ->
            val cx = slot * i + slot / 2
            val left = cx - barW / 2
            val right = cx + barW / 2
            val mh = chartH * m / max
            val wh = chartH * w / max
            paint.color = mobileColor
            canvas.drawRect(left, bottom - mh, right, bottom, paint)
            paint.color = wifiColor
            canvas.drawRect(left, bottom - mh - wh, right, bottom - mh, paint)
            if (data.size <= 10 || i % 5 == 0 || i == data.lastIndex) {
                paint.color = textColor
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(label, cx, height - 4 * d, paint)
            }
        }
        paint.color = textColor
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("En yüksek gün: ${Fmt.bytes(max)}", 0f, 12 * d, paint)
    }
}
