package com.emir.hizgostergesi

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import kotlin.math.sqrt

/** Kullanım analizi: kıyas, tahmin, kategoriler, saatlik dağılım, ekran kapalı kullanım, yoğun saat hızları. */
class AnalysisActivity : Activity() {

    private val days = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    private val dayNames = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    private val categoryColors = intArrayOf(
        0xFF4F8CFF.toInt(), 0xFF7B5CFF.toInt(), 0xFFFF9800.toInt(), 0xFF4CAF50.toInt(), 0xFFE91E63.toInt(),
        0xFF00BCD4.toInt(), 0xFFFFC107.toInt(), 0xFF9E9E9E.toInt()
    )

    private lateinit var compareBox: LinearLayout
    private lateinit var categoryBox: LinearLayout
    private lateinit var heatText: TextView
    private lateinit var heat: HeatMap
    private lateinit var screenOffBox: LinearLayout
    private lateinit var speedChart: BarChart
    private lateinit var speedText: TextView
    private var speedMobile = true
    private lateinit var connBox: LinearLayout
    private lateinit var habitBox: LinearLayout
    private lateinit var risingBox: LinearLayout
    private lateinit var balanceBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, root) = Ui.page(this)
        root.addView(Ui.topBar(this, "Analiz"))

        root.addView(Ui.card(this, "Karşılaştırma ve tahmin", "📈").apply {
            compareBox = LinearLayout(this@AnalysisActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(compareBox)
        })

        root.addView(Ui.card(this, "Günlük alışkanlıklar · son 4 hafta", "📅").apply {
            habitBox = LinearLayout(this@AnalysisActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(habitBox)
        })

        root.addView(Ui.card(this, "Kategoriler · son 30 gün", "🗂").apply {
            categoryBox = LinearLayout(this@AnalysisActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(categoryBox)
        })

        root.addView(Ui.card(this, "Yükselen ve yeni uygulamalar", "🚀").apply {
            addView(Ui.hint(this@AnalysisActivity, "Son 7 gün, önceki 7 günle karşılaştırılır."))
            risingBox = LinearLayout(this@AnalysisActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(risingBox)
        })

        root.addView(Ui.card(this, "Bağlantı türü · son 30 gün", "📡").apply {
            addView(Ui.hint(this@AnalysisActivity, "Hangi şebekede ne kadar veri harcadın ve ortalama hızın ne oldu."))
            connBox = LinearLayout(this@AnalysisActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(connBox)
        })

        root.addView(Ui.card(this, "Saatlik dağılım · son 4 hafta", "🕒").apply {
            addView(Ui.hint(this@AnalysisActivity, "Koyu kareler en çok veri harcadığın saatler."))
            heat = HeatMap(this@AnalysisActivity)
            addView(heat, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this@AnalysisActivity, 190)))
            heatText = Ui.text(this@AnalysisActivity, "", 14f, topDp = 6)
            addView(heatText)
        })

        root.addView(Ui.card(this, "Ekran kapalıyken", "🌙").apply {
            addView(Ui.hint(this@AnalysisActivity, "Telefon cebindeyken / masadayken harcanan veri."))
            screenOffBox = LinearLayout(this@AnalysisActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(screenOffBox)
        })

        root.addView(Ui.card(this, "Yoğun saat hız analizi · son 30 gün", "⚡").apply {
            addView(Ui.choiceRow(this@AnalysisActivity, listOf("Mobil veri" to true, "Wi-Fi" to false), speedMobile) {
                speedMobile = it
                renderSpeed()
            })
            speedChart = BarChart(this@AnalysisActivity).apply {
                textColor = Ui.secondary(this@AnalysisActivity)
                labelEvery = 3
                mobileColor = Ui.ACCENT
            }
            addView(speedChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this@AnalysisActivity, 170)))
            speedText = Ui.text(this@AnalysisActivity, "", 14f, topDp = 6)
            addView(speedText)
        })

        root.addView(Ui.card(this, "İndirme / yükleme dengesi · son 30 gün", "⇅").apply {
            balanceBox = LinearLayout(this@AnalysisActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(balanceBox)
        })

        root.addView(Ui.card(this, "Otomatik bildirimler", "📬").apply {
            val p = Prefs.get(this@AnalysisActivity)
            addView(Ui.switch(this@AnalysisActivity, "Haftalık özet (pazartesi sabahı)", p.getBoolean(Prefs.WEEKLY_ON, true)) {
                p.edit().putBoolean(Prefs.WEEKLY_ON, it).apply()
            })
            addView(Ui.switch(this@AnalysisActivity, "Olağandışı kullanım uyarısı", p.getBoolean(Prefs.ANOMALY_ON, true)) {
                p.edit().putBoolean(Prefs.ANOMALY_ON, it).apply()
            })
            addView(Ui.hint(this@AnalysisActivity,
                "Bir uygulama bugün 100 MB'ı geçip son 7 günlük ortalamasının 3 katından fazla veri çekerse uyarır. Bunun için \"Kullanım erişimi\" izni gerekir."))
        })

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        renderHeat()
        renderScreenOff()
        renderSpeed()
        renderConn()
        loadAsync()
    }

    private fun accessButton(box: LinearLayout) {
        box.addView(Ui.hint(this, "Bu bölüm için Android'in \"Kullanım erişimi\" izni gerekiyor."))
        box.addView(Ui.button(this, "İzin ver", filled = true) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
    }

    // ---------- Kıyas, tahmin, kategoriler (Android kayıtlarını okuduğu için arka planda) ----------

    private fun loadAsync() {
        compareBox.removeAllViews()
        compareBox.addView(Ui.hint(this, "Hesaplanıyor…"))
        categoryBox.removeAllViews()
        habitBox.removeAllViews()
        habitBox.addView(Ui.hint(this, "Hesaplanıyor…"))
        risingBox.removeAllViews()
        balanceBox.removeAllViews()
        val access = NetStats.hasAccess(this)
        if (!access) {
            accessButton(risingBox)
            accessButton(balanceBox)
        }
        if (!access) accessButton(categoryBox) else categoryBox.addView(Ui.hint(this, "Hesaplanıyor…"))

        Thread {
            val now = System.currentTimeMillis()
            val day = 86_400_000L

            // Hafta: pazartesiden bugüne vs. geçen haftanın aynı süresi
            val weekStart = Insights.mondayOf(now).timeInMillis
            val weekLen = now - weekStart
            val (wm, ww) = Insights.total(this, weekStart, now)
            val (pwm, pww) = Insights.total(this, weekStart - 7 * day, weekStart - 7 * day + weekLen)

            // Ay: ayın 1'inden bugüne vs. geçen ayın aynı günleri
            val monthCal = Calendar.getInstance().apply {
                timeInMillis = Insights.startOfToday()
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val monthStart = monthCal.timeInMillis
            val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val prevCal = (monthCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            val prevStart = prevCal.timeInMillis
            val prevEnd = minOf(prevStart + (now - monthStart), monthStart)
            val (mm, mw) = Insights.total(this, monthStart, now)
            val (pmm, pmw) = Insights.total(this, prevStart, prevEnd)

            // Ay sonu tahmini: bugüne kadarki hızla devam ederse
            val elapsedDays = ((now - monthStart).toDouble() / day).coerceAtLeast(0.25)
            val forecastTotal = ((mm + mw) / elapsedDays * daysInMonth).toLong()
            val forecastMobile = (mm / elapsedDays * daysInMonth).toLong()

            val apps30 = if (access) NetStats.queryApps(this, now - 30 * day, now) else emptyList()
            val categories = apps30
                .groupBy { it.category }
                .map { (k, v) -> k to v.sumOf { it.total } }
                .sortedByDescending { it.second }

            // Son 28 günün gün gün toplamları (bugün hariç)
            val todayStart = Insights.startOfToday()
            val perDay = (28 downTo 1).map { i ->
                val s = todayStart - i * day
                val t = Insights.total(this, s, s + day)
                Triple(s, t.first, t.second)
            }

            // Son 7 gün vs önceki 7 gün
            val last7 = if (access) NetStats.queryApps(this, now - 7 * day, now) else emptyList()
            val prev7 = if (access) NetStats.queryApps(this, now - 14 * day, now - 7 * day, withNames = false)
                .associate { it.uid to it.total } else emptyMap()

            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                compareBox.removeAllViews()
                stat(compareBox, "Bu hafta", Fmt.bytes(wm + ww),
                    "Geçen haftanın aynı günlerine göre ${Insights.percentChange(wm + ww, pwm + pww)}")
                stat(compareBox, "Bu ay", Fmt.bytes(mm + mw),
                    "Geçen ayın aynı günlerine göre ${Insights.percentChange(mm + mw, pmm + pmw)}")
                stat(compareBox, "Ay sonu tahmini", "~${Fmt.bytes(forecastTotal)}",
                    "Bu tempoyla devam edersen. Mobil veri: ~${Fmt.bytes(forecastMobile)}")
                compareBox.addView(Ui.hint(this,
                    if (access) "Kaynak: Android'in kendi ağ kayıtları." else "Kaynak: uygulamanın kendi kayıtları (kurulumdan beri). \"Kullanım erişimi\" izni verirsen geçmiş günler de dahil olur."))

                if (access) {
                    renderCategories(categories)
                    renderRising(last7, prev7)
                    renderBalance(apps30)
                }
                renderHabits(perDay)
            }
        }.start()
    }

    private fun stat(box: LinearLayout, title: String, value: String, sub: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(this@AnalysisActivity, 6), 0, Ui.dp(this@AnalysisActivity, 6))
        }
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        line.addView(Ui.text(this, title, 15f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(Ui.text(this, value, 17f, bold = true, color = Ui.ACCENT))
        row.addView(line)
        row.addView(Ui.hint(this, sub))
        box.addView(row)
    }

    private fun renderCategories(cats: List<Pair<String, Long>>) {
        categoryBox.removeAllViews()
        val total = cats.sumOf { it.second }
        if (total == 0L) {
            categoryBox.addView(Ui.hint(this, "Kayıt bulunamadı."))
            return
        }
        cats.forEachIndexed { i, (name, bytes) ->
            val color = categoryColors[i.coerceAtMost(categoryColors.size - 1)]
            val pct = bytes * 100.0 / total
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            line.addView(Ui.text(this, name, 14f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(Ui.text(this, "${Fmt.bytes(bytes)} · %${"%.0f".format(pct)}", 14f, color = Ui.secondary(this)))
            categoryBox.addView(line)
            categoryBox.addView(PercentBar(this, (pct / 100).toFloat(), color),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)).apply {
                    bottomMargin = Ui.dp(this@AnalysisActivity, 10)
                })
        }
        val top = cats.first()
        categoryBox.addView(Ui.hint(this, "Verinin %${(top.second * 100 / total)}'i \"${top.first}\" kategorisine gitmiş."))
    }

    private fun renderHabits(perDay: List<Triple<Long, Long, Long>>) {
        habitBox.removeAllViews()
        val withData = perDay.filter { it.second + it.third > 0 }
        if (withData.size < 2) {
            habitBox.addView(Ui.hint(this, "Henüz yeterli gün yok. \"Kullanım erişimi\" izni verirsen geçmiş günler de hesaba katılır."))
            return
        }
        val totals = withData.map { it.second + it.third }
        val avg = totals.sum() / withData.size
        stat(habitBox, "Günlük ortalama", Fmt.bytes(avg), "${withData.size} günün ortalaması")

        val cal = Calendar.getInstance()
        val weekday = ArrayList<Long>()
        val weekend = ArrayList<Long>()
        withData.forEach { (s, m, w) ->
            cal.timeInMillis = s
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) weekend += m + w else weekday += m + w
        }
        if (weekday.isNotEmpty() && weekend.isNotEmpty()) {
            val wd = weekday.sum() / weekday.size
            val we = weekend.sum() / weekend.size
            val diff = if (wd > 0) ((we - wd) * 100 / wd).toInt() else 0
            val note = when {
                diff >= 10 -> "Hafta sonu %$diff daha fazla kullanıyorsun"
                diff <= -10 -> "Hafta içi %${-diff} daha fazla kullanıyorsun"
                else -> "Hafta içi ve hafta sonu birbirine yakın"
            }
            stat(habitBox, "Hafta içi / hafta sonu", "${Fmt.bytes(wd)} / ${Fmt.bytes(we)}", note)
        }

        val fmt = java.text.SimpleDateFormat("d MMMM EEEE", java.util.Locale("tr"))
        val maxDay = withData.maxByOrNull { it.second + it.third }!!
        val minDay = withData.minByOrNull { it.second + it.third }!!
        stat(habitBox, "En çok kullandığın gün", Fmt.bytes(maxDay.second + maxDay.third), fmt.format(java.util.Date(maxDay.first)))
        stat(habitBox, "En az kullandığın gün", Fmt.bytes(minDay.second + minDay.third), fmt.format(java.util.Date(minDay.first)))

        val mob = withData.sumOf { it.second }
        val all = withData.sumOf { it.second + it.third }.coerceAtLeast(1L)
        val pct = mob * 100 / all
        habitBox.addView(Ui.text(this, "Mobil veri payı: %$pct", 15f, topDp = 8).apply { typeface = Ui.MEDIUM })
        habitBox.addView(PercentBar(this, pct / 100f, 0xFFFF9800.toInt()),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)).apply { topMargin = Ui.dp(this@AnalysisActivity, 4) })
        habitBox.addView(Ui.hint(this, "Kalanı Wi-Fi: mobil ${Fmt.bytes(mob)} • Wi-Fi ${Fmt.bytes(all - mob)}"))
    }

    private fun renderRising(last7: List<AppUsage>, prev7: Map<Int, Long>) {
        risingBox.removeAllViews()
        val candidates = last7.filter { it.category != "Sistem" }
        val newApps = candidates.filter { (prev7[it.uid] ?: 0L) < 1048576L && it.total >= 20L * 1048576 }.take(3)
        val rising = candidates
            .filter { (prev7[it.uid] ?: 0L) >= 1048576L && it.total > (prev7[it.uid] ?: 0L) }
            .sortedByDescending { it.total - (prev7[it.uid] ?: 0L) }
            .take(5)
        if (newApps.isEmpty() && rising.isEmpty()) {
            risingBox.addView(Ui.hint(this, "Belirgin bir artış yok, kullanımın dengeli görünüyor."))
            return
        }
        rising.forEach { a ->
            val before = prev7[a.uid] ?: 0L
            val pct = ((a.total - before) * 100 / before.coerceAtLeast(1)).toInt()
            stat(risingBox, a.name, "↑ %$pct", "${Fmt.bytes(before)} → ${Fmt.bytes(a.total)}")
        }
        newApps.forEach { a ->
            stat(risingBox, a.name, "yeni", "Bu hafta ${Fmt.bytes(a.total)}, önceki hafta neredeyse hiç")
        }
    }

    private fun renderBalance(apps: List<AppUsage>) {
        balanceBox.removeAllViews()
        val rx = apps.sumOf { it.rx }
        val tx = apps.sumOf { it.tx }
        if (rx + tx == 0L) {
            balanceBox.addView(Ui.hint(this, "Kayıt bulunamadı."))
            return
        }
        stat(balanceBox, "İndirilen", Fmt.bytes(rx), "Telefona gelen veri")
        stat(balanceBox, "Gönderilen", Fmt.bytes(tx), "Telefondan çıkan veri (yedekleme, yükleme, görüntülü arama…)")
        balanceBox.addView(PercentBar(this, rx.toFloat() / (rx + tx), Ui.ACCENT),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)).apply { topMargin = Ui.dp(this@AnalysisActivity, 4) })
        val topUp = apps.filter { it.category != "Sistem" }.maxByOrNull { it.tx }
        if (topUp != null && topUp.tx > 0) {
            balanceBox.addView(Ui.hint(this, "En çok veri gönderen: ${topUp.name} (${Fmt.bytes(topUp.tx)})"))
        }
    }

    // ---------- Uygulamanın kendi kayıtlarından ----------

    private fun renderConn() {
        connBox.removeAllViews()
        val sum = HashMap<String, ConnStat>()
        lastDays(30).forEach { (_, s) ->
            s?.byConn?.forEach { (k, v) ->
                val t = sum.getOrPut(k) { ConnStat() }
                t.bytes += v.bytes
                t.activeSec += v.activeSec
                t.activeBytes += v.activeBytes
            }
        }
        val total = sum.values.sumOf { it.bytes }
        if (total == 0L) {
            connBox.addView(Ui.hint(this, "Henüz kayıt yok. Uygulama çalıştıkça bağlantı türüne göre kullanım birikecek."))
            return
        }
        val bits = Prefs.get(this).getBoolean(Prefs.UNIT_BITS, false)
        val order = listOf("5G", "4.5G", "4G", "3G", "2G", "Mobil", "Wi-Fi", "Ethernet")
        sum.entries.sortedBy { order.indexOf(it.key).let { i -> if (i < 0) 99 else i } }.forEach { (k, v) ->
            if (v.bytes == 0L) return@forEach
            val pct = v.bytes * 100.0 / total
            val color = if (k == "Wi-Fi" || k == "Ethernet") 0xFF2196F3.toInt() else 0xFFFF9800.toInt()
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            line.addView(Ui.text(this, k, 15f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(Ui.text(this, "${Fmt.bytes(v.bytes)} · %${"%.0f".format(pct)}", 14f, color = Ui.secondary(this)))
            connBox.addView(line)
            connBox.addView(PercentBar(this, (pct / 100).toFloat(), color),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)))
            connBox.addView(Ui.hint(this, "Ortalama hız: ${if (v.avg > 0) Fmt.speed(v.avg, bits) else "—"}").apply {
                setPadding(0, Ui.dp(this@AnalysisActivity, 2), 0, Ui.dp(this@AnalysisActivity, 10))
            })
        }
    }


    /** Son n günün istatistikleri, en eskiden yeniye. */
    private fun lastDays(n: Int): List<Pair<Calendar, DayStats?>> {
        val all = StatsStore.all(this)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -(n - 1))
        return List(n) {
            val c = cal.clone() as Calendar
            val s = all[StatsStore.key(cal.timeInMillis)]
            cal.add(Calendar.DAY_OF_MONTH, 1)
            c to s
        }
    }

    private fun renderHeat() {
        val grid = Array(7) { LongArray(24) }
        lastDays(28).forEach { (cal, s) ->
            if (s == null) return@forEach
            val row = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
            for (h in 0 until 24) grid[row][h] += s.hourUse[h]
        }
        heat.setData(grid, days)
        var best = 0L
        var bestRow = 0
        var bestHour = 0
        for (r in 0 until 7) for (h in 0 until 24) if (grid[r][h] > best) {
            best = grid[r][h]; bestRow = r; bestHour = h
        }
        heatText.text = if (best == 0L) "Henüz yeterli kayıt yok. Uygulama çalıştıkça dolacak."
        else "En yoğun: ${dayNames[bestRow]} %02d:00–%02d:00".format(bestHour, (bestHour + 1) % 24)
    }

    private fun renderScreenOff() {
        screenOffBox.removeAllViews()
        val today = StatsStore.today(this)
        val week = DayStats()
        lastDays(7).forEach { (_, s) -> if (s != null) week.merge(s) }
        fun line(title: String, s: DayStats) {
            val total = s.mobile + s.wifi
            val pct = if (total > 0) s.screenOff * 100 / total else 0
            stat(screenOffBox, title, Fmt.bytes(s.screenOff), "Toplam kullanımın %$pct'i ekran kapalıyken")
        }
        line("Bugün", today)
        line("Son 7 gün", week)
    }

    private fun renderSpeed() {
        val sec = DoubleArray(24)
        val bytes = LongArray(24)
        lastDays(30).forEach { (_, s) ->
            if (s == null) return@forEach
            for (h in 0 until 24) {
                if (speedMobile) {
                    sec[h] += s.hourMobSec[h]; bytes[h] += s.hourMobBytes[h]
                } else {
                    sec[h] += s.hourWifiSec[h]; bytes[h] += s.hourWifiBytes[h]
                }
            }
        }
        val avg = LongArray(24) { if (sec[it] >= 30) (bytes[it] / sec[it]).toLong() else 0L }
        val bits = Prefs.get(this).getBoolean(Prefs.UNIT_BITS, false)
        speedChart.mobileColor = if (speedMobile) 0xFFFF9800.toInt() else 0xFF2196F3.toInt()
        speedChart.topLabel = { "En hızlı saat ortalaması: ${Fmt.speed(it, bits)}" }
        speedChart.data = List(24) { Triple("%02d".format(it), avg[it], 0L) }

        val measured = (0 until 24).filter { avg[it] > 0 }
        if (measured.size < 3) {
            speedText.text = "Henüz yeterli ölçüm yok. Veri aktıkça saat saat ortalama hızlar burada birikecek."
            return
        }
        val fast = measured.maxByOrNull { avg[it] }!!
        val slow = measured.minByOrNull { avg[it] }!!
        val ratio = if (avg[fast] > 0) avg[slow] * 100 / avg[fast] else 100
        speedText.text = "En hızlı: %02d:00 civarı (${Fmt.speed(avg[fast], bits)})\n".format(fast) +
            "En yavaş: %02d:00 civarı (${Fmt.speed(avg[slow], bits)}) — en hızlı saatin %%$ratio'i".format(slow)
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
        paint.color = color
        val w = (width * fraction.coerceIn(0f, 1f)).coerceAtLeast(height.toFloat())
        canvas.drawRoundRect(RectF(0f, 0f, w, height.toFloat()), r, r, paint)
    }
}

/** Haftanın günleri × günün saatleri ısı haritası. */
class HeatMap(c: Context) : View(c) {
    private var grid: Array<LongArray> = Array(7) { LongArray(24) }
    private var rows: Array<String> = emptyArray()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelColor = Ui.secondary(c)
    private val emptyColor = Ui.outline(c)

    fun setData(g: Array<LongArray>, labels: Array<String>) {
        grid = g
        rows = labels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val left = 30 * d
        val bottomLabel = 16 * d
        val gap = 1.5f * d
        val cellW = (width - left) / 24f
        val cellH = (height - bottomLabel) / 7f
        val max = grid.maxOf { r -> r.maxOrNull() ?: 0L }.coerceAtLeast(1L)

        paint.textSize = 10 * d
        paint.textAlign = Paint.Align.LEFT
        for (r in 0 until 7) {
            paint.color = labelColor
            if (r < rows.size) canvas.drawText(rows[r], 0f, r * cellH + cellH / 2 + 4 * d, paint)
            for (h in 0 until 24) {
                val v = grid[r][h]
                paint.color = if (v == 0L) emptyColor else {
                    // karekök ölçek: küçük değerler de görünür olsun
                    val t = sqrt(v.toDouble() / max).toFloat()
                    Color.argb((50 + 205 * t).toInt(), Color.red(Ui.ACCENT), Color.green(Ui.ACCENT), Color.blue(Ui.ACCENT))
                }
                val x = left + h * cellW
                val y = r * cellH
                canvas.drawRoundRect(RectF(x + gap, y + gap, x + cellW - gap, y + cellH - gap), 3 * d, 3 * d, paint)
            }
        }
        paint.color = labelColor
        paint.textAlign = Paint.Align.CENTER
        for (h in 0 until 24 step 6) {
            canvas.drawText("%02d".format(h), left + h * cellW + cellW / 2, height - 3 * d, paint)
        }
    }
}
