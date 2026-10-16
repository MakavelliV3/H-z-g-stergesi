package com.emir.hizgostergesi

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import kotlin.math.sqrt

/** Kullanım analizi: kıyas, tahmin, kategoriler, saatlik dağılım, ekran kapalı kullanım, yoğun saat hızları. */
class AnalysisScreen(a: MainActivity) : Screen(a) {

    private val days = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
    private val dayNames = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    private val categoryColors = intArrayOf(
        Ui.ACCENT, 0xFF7AA7FF.toInt(), 0xFFFF9F43.toInt(), 0xFFFF5C8A.toInt(), 0xFF2EE6C5.toInt(),
         0xFFB48CFF.toInt(), 0xFFFFD166.toInt(), 0xFF6B7280.toInt()
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

    override fun build() {
        root.addView(Ui.screenTitle(a, "Analiz", "Alışkanlıkların, eğilimler ve tahminler"))

        root.addView(Ui.card(a, "Karşılaştırma ve tahmin", "📈").apply {
            compareBox = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            addView(compareBox)
        })

        root.addView(Ui.card(a, "Günlük alışkanlıklar · son 4 hafta", "📅").apply {
            habitBox = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            addView(habitBox)
        })

        root.addView(Ui.card(a, "Kategoriler · son 30 gün", "🗂").apply {
            categoryBox = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            addView(categoryBox)
        })

        root.addView(Ui.card(a, "Yükselen ve yeni uygulamalar", "🚀").apply {
            addView(Ui.hint(a, "Son 7 gün, önceki 7 günle karşılaştırılır."))
            risingBox = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            addView(risingBox)
        })

        root.addView(Ui.card(a, "Bağlantı türü · son 30 gün", "📡").apply {
            addView(Ui.hint(a, "Hangi şebekede ne kadar veri harcadın ve ortalama hızın ne oldu."))
            connBox = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            addView(connBox)
        })

        root.addView(Ui.card(a, "Saatlik dağılım · son 4 hafta", "🕒").apply {
            addView(Ui.hint(a, "Koyu kareler en çok veri harcadığın saatler."))
            heat = HeatMap(a).apply { dayNames = this@AnalysisScreen.dayNames }
            addView(heat, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 190)))
            heatText = Ui.text(a, "", 14f, topDp = 6)
            addView(heatText)
        })

        root.addView(Ui.card(a, "Ekran kapalıyken", "🌙").apply {
            addView(Ui.hint(a, "Telefon cebindeyken / masadayken harcanan veri."))
            screenOffBox = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            addView(screenOffBox)
        })

        root.addView(Ui.card(a, "Yoğun saat hız analizi · son 30 gün", "⚡").apply {
            addView(Ui.choiceRow(a, listOf("Mobil veri" to true, "Wi-Fi" to false), speedMobile) {
                speedMobile = it
                renderSpeed()
            })
            speedChart = BarChart(a).apply {
                textColor = Ui.secondary(a)
                labelEvery = 3
                mobileColor = Ui.ACCENT
            }
            addView(speedChart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 170)))
            speedText = Ui.text(a, "", 14f, topDp = 6)
            addView(speedText)
        })

        root.addView(Ui.card(a, "İndirme / yükleme dengesi · son 30 gün", "⇅").apply {
            balanceBox = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            addView(balanceBox)
        })

        root.addView(Ui.hint(a, "Haftalık özet ve olağandışı kullanım bildirimlerini Ayarlar → Uyarılar'dan açıp kapatabilirsin.").apply {
            setPadding(dp(4), 0, dp(4), dp(8))
        })
    }

    override fun onShow() {
        renderHeat()
        renderScreenOff()
        renderSpeed()
        renderConn()
        loadAsync()
    }

    private fun accessButton(box: LinearLayout) {
        box.addView(Ui.hint(a, "Bu bölüm için Android'in \"Kullanım erişimi\" izni gerekiyor."))
        box.addView(Ui.button(a, "İzin ver", filled = true) {
            a.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
    }

    // ---------- Kıyas, tahmin, kategoriler (Android kayıtlarını okuduğu için arka planda) ----------

    private fun loadAsync() {
        compareBox.removeAllViews()
        compareBox.addView(Ui.hint(a, "Hesaplanıyor…"))
        categoryBox.removeAllViews()
        habitBox.removeAllViews()
        habitBox.addView(Ui.hint(a, "Hesaplanıyor…"))
        risingBox.removeAllViews()
        balanceBox.removeAllViews()
        val access = NetStats.hasAccess(a)
        if (!access) {
            accessButton(risingBox)
            accessButton(balanceBox)
        }
        if (!access) accessButton(categoryBox) else categoryBox.addView(Ui.hint(a, "Hesaplanıyor…"))

        Thread {
            val now = System.currentTimeMillis()
            val day = 86_400_000L

            // Hafta: pazartesiden bugüne vs. geçen haftanın aynı süresi
            val weekStart = Insights.mondayOf(now).timeInMillis
            val weekLen = now - weekStart
            val (wm, ww) = Insights.total(a, weekStart, now)
            val (pwm, pww) = Insights.total(a, weekStart - 7 * day, weekStart - 7 * day + weekLen)

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
            val (mm, mw) = Insights.total(a, monthStart, now)
            val (pmm, pmw) = Insights.total(a, prevStart, prevEnd)

            // Ay sonu tahmini: bugüne kadarki hızla devam ederse
            val elapsedDays = ((now - monthStart).toDouble() / day).coerceAtLeast(0.25)
            val forecastTotal = ((mm + mw) / elapsedDays * daysInMonth).toLong()
            val forecastMobile = (mm / elapsedDays * daysInMonth).toLong()

            val apps30 = if (access) NetStats.queryApps(a, now - 30 * day, now) else emptyList()
            val categories = apps30
                .groupBy { it.category }
                .map { (k, v) -> k to v.sumOf { it.total } }
                .sortedByDescending { it.second }

            // Son 28 günün gün gün toplamları (bugün hariç)
            val todayStart = Insights.startOfToday()
            val perDay = (28 downTo 1).map { i ->
                val s = todayStart - i * day
                val t = Insights.total(a, s, s + day)
                Triple(s, t.first, t.second)
            }

            // Son 7 gün vs önceki 7 gün
            val last7 = if (access) NetStats.queryApps(a, now - 7 * day, now) else emptyList()
            val prev7 = if (access) NetStats.queryApps(a, now - 14 * day, now - 7 * day, withNames = false)
                .associate { it.uid to it.total } else emptyMap()

            a.runOnUiThread {
                if (a.isFinishing) return@runOnUiThread
                compareBox.removeAllViews()
                stat(compareBox, "Bu hafta", Fmt.bytes(wm + ww),
                    Insights.percentChangeOrNull(wm + ww, pwm + pww)?.let { "Geçen haftanın aynı günlerine göre $it" }
                        ?: "Geçen hafta için karşılaştıracak kayıt yok")
                stat(compareBox, "Bu ay", Fmt.bytes(mm + mw),
                    Insights.percentChangeOrNull(mm + mw, pmm + pmw)?.let { "Geçen ayın aynı günlerine göre $it" }
                        ?: "Geçen ay için karşılaştıracak kayıt yok")
                stat(compareBox, "Ay sonu tahmini", "~${Fmt.bytes(forecastTotal)}",
                    "Bu tempoyla devam edersen. Mobil veri: ~${Fmt.bytes(forecastMobile)}")
                compareBox.addView(Ui.hint(a,
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
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 6))
        }
        val line = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        line.addView(Ui.text(a, title, 15f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(Ui.text(a, value, 17f, bold = true, color = Ui.accentText(a)))
        row.addView(line)
        row.addView(Ui.hint(a, sub))
        box.addView(row)
    }

    private fun renderCategories(cats: List<Pair<String, Long>>) {
        categoryBox.removeAllViews()
        val total = cats.sumOf { it.second }
        if (total == 0L) {
            categoryBox.addView(Ui.hint(a, "Kayıt bulunamadı."))
            return
        }
        cats.forEachIndexed { i, (name, bytes) ->
            val color = categoryColors[i.coerceAtMost(categoryColors.size - 1)]
            val pct = bytes * 100.0 / total
            val line = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            line.addView(Ui.text(a, name, 14f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(Ui.text(a, "${Fmt.bytes(bytes)} · %${"%.0f".format(pct)}", 14f, color = Ui.secondary(a)))
            categoryBox.addView(line)
            categoryBox.addView(PercentBar(a, (pct / 100).toFloat(), color),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 8)).apply {
                    bottomMargin = Ui.dp(a, 10)
                })
        }
        val top = cats.first()
        categoryBox.addView(Ui.hint(a, "En çok veri \"${top.first}\" kategorisine gitmiş (toplamın %${(top.second * 100 / total)} kadarı)."))
    }

    private fun renderHabits(perDay: List<Triple<Long, Long, Long>>) {
        habitBox.removeAllViews()
        val withData = perDay.filter { it.second + it.third > 0 }
        if (withData.size < 2) {
            habitBox.addView(Ui.hint(a, if (NetStats.hasAccess(a)) "Henüz yeterli gün yok. Birkaç gün kullandıkça burası dolacak."
                else "Henüz yeterli gün yok. \"Kullanım erişimi\" izni verirsen geçmiş günler de hesaba katılır."))
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
        habitBox.addView(Ui.text(a, "Mobil veri payı: %$pct", 15f, topDp = 8).apply { typeface = Ui.MEDIUM })
        habitBox.addView(PercentBar(a, pct / 100f, Ui.ACCENT),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 8)).apply { topMargin = Ui.dp(a, 4) })
        habitBox.addView(Ui.hint(a, "Kalanı Wi-Fi: mobil ${Fmt.bytes(mob)} • Wi-Fi ${Fmt.bytes(all - mob)}"))
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
            risingBox.addView(Ui.hint(a, "Belirgin bir artış yok, kullanımın dengeli görünüyor."))
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
            balanceBox.addView(Ui.hint(a, "Kayıt bulunamadı."))
            return
        }
        stat(balanceBox, "İndirilen", Fmt.bytes(rx), "Telefona gelen veri")
        stat(balanceBox, "Gönderilen", Fmt.bytes(tx), "Telefondan çıkan veri (yedekleme, yükleme, görüntülü arama…)")
        balanceBox.addView(PercentBar(a, rx.toFloat() / (rx + tx), Ui.ACCENT),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 8)).apply { topMargin = Ui.dp(a, 4) })
        val topUp = apps.filter { it.category != "Sistem" }.maxByOrNull { it.tx }
        if (topUp != null && topUp.tx > 0) {
            balanceBox.addView(Ui.hint(a, "En çok veri gönderen: ${topUp.name} (${Fmt.bytes(topUp.tx)})"))
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
            connBox.addView(Ui.hint(a, "Henüz kayıt yok. Uygulama çalıştıkça bağlantı türüne göre kullanım birikecek."))
            return
        }
        val bits = Prefs.get(a).getBoolean(Prefs.UNIT_BITS, false)
        val order = listOf("5G", "4.5G", "4G", "3G", "2G", "Mobil", "Wi-Fi", "Ethernet")
        sum.entries.sortedBy { order.indexOf(it.key).let { i -> if (i < 0) 99 else i } }.forEach { (k, v) ->
            if (v.bytes == 0L) return@forEach
            val pct = v.bytes * 100.0 / total
            val color = if (k == "Wi-Fi" || k == "Ethernet") Ui.WIFI else Ui.MOBILE
            val line = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            line.addView(Ui.text(a, k, 15f, bold = true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(Ui.text(a, "${Fmt.bytes(v.bytes)} · %${"%.0f".format(pct)}", 14f, color = Ui.secondary(a)))
            connBox.addView(line)
            connBox.addView(PercentBar(a, (pct / 100).toFloat(), color),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 8)))
            connBox.addView(Ui.hint(a, "Ortalama hız: ${if (v.avg > 0) Fmt.speed(v.avg, bits) else "—"}").apply {
                setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 10))
            })
        }
    }


    /** Son n günün istatistikleri, en eskiden yeniye. */
    private fun lastDays(n: Int): List<Pair<Calendar, DayStats?>> {
        val all = StatsStore.all(a)
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
        val today = StatsStore.today(a)
        val week = DayStats()
        lastDays(7).forEach { (_, s) -> if (s != null) week.merge(s) }
        fun line(title: String, s: DayStats) {
            val total = s.mobile + s.wifi
            val pct = if (total > 0) s.screenOff * 100 / total else 0
            stat(screenOffBox, title, Fmt.bytes(s.screenOff), "Toplam kullanımdaki payı: %$pct")
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
        val bits = Prefs.get(a).getBoolean(Prefs.UNIT_BITS, false)
        speedChart.mobileColor = if (speedMobile) Ui.MOBILE else Ui.WIFI
        speedChart.topLabel = { "En hızlı saat ortalaması: ${Fmt.speed(it, bits)}" }
        speedChart.data = List(24) { Triple("%02d".format(it), avg[it], 0L) }
        speedChart.tooltip = { i -> if (avg[i] > 0) "%02d:00 civarı\nort. ${Fmt.speed(avg[i], bits)}".format(i) else "%02d:00\nölçüm yok".format(i) }

        val measured = (0 until 24).filter { avg[it] > 0 }
        if (measured.size < 3) {
            speedText.text = "Henüz yeterli ölçüm yok. Veri aktıkça saat saat ortalama hızlar burada birikecek."
            return
        }
        val fast = measured.maxByOrNull { avg[it] }!!
        val slow = measured.minByOrNull { avg[it] }!!
        val ratio = if (avg[fast] > 0) avg[slow] * 100 / avg[fast] else 100
        speedText.text = "En hızlı: %02d:00 civarı (${Fmt.speed(avg[fast], bits)})\n".format(fast) +
            "En yavaş: %02d:00 civarı (${Fmt.speed(avg[slow], bits)}) — hız, en hızlı saate göre %%$ratio seviyesinde".format(slow)
    }
}
