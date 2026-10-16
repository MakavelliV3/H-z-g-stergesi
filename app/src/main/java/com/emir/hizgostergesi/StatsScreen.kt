package com.emir.hizgostergesi

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** İstatistik: bugün (mobil/Wi-Fi ayrı), 7/30 gün, aylık ve yıllık raporlar, dokunulabilir grafik. */
class StatsScreen(a: MainActivity) : Screen(a) {

    private val mobileColor get() = Ui.MOBILE
    private val wifiColor get() = Ui.WIFI
    private val tr = Locale("tr")

    private lateinit var todayMobile: Ui.StatTile
    private lateinit var todayWifi: Ui.StatTile
    private lateinit var totalValue: TextView
    private lateinit var totalSub: TextView
    private lateinit var totalSplit: SplitBar
    private lateinit var chart: BarChart
    private lateinit var list: LinearLayout
    private var mode = 0 // 0: 7 gün, 1: 30 gün, 2: aylık, 3: yıllık
    private var ticks = 0

    private class Bucket(val label: String, val title: String, val stats: DayStats)

    override fun build() {
        root.addView(Ui.screenTitle(a, "İstatistik", "Mobil veri ve Wi-Fi kullanımın"))

        todayMobile = Ui.StatTile(a, "Bugün · mobil")
        todayWifi = Ui.StatTile(a, "Bugün · Wi-Fi")
        root.addView(Ui.pairRow(a, todayMobile, todayWifi))

        root.addView(Ui.choiceRow(a, listOf("7 gün" to 0, "30 gün" to 1, "Aylık" to 2, "Yıllık" to 3), mode) {
            mode = it
            render()
        })

        root.addView(Ui.card(a).apply {
            totalValue = Ui.mono(Ui.text(a, "", 32f))
            totalSub = Ui.hint(a, "")
            totalSplit = SplitBar(a).apply {
                colorA = mobileColor
                colorB = wifiColor
            }
            addView(totalValue)
            addView(totalSub)
            addView(totalSplit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)).apply {
                topMargin = dp(10)
                bottomMargin = dp(14)
            })
            val legend = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            legend.addView(Ui.text(a, "● Mobil   ", 13f, color = mobileColor))
            legend.addView(Ui.text(a, "● Wi-Fi", 13f, color = wifiColor))
            legend.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
            legend.addView(Ui.hint(a, "Çubuğa dokun"))
            addView(legend)
            chart = BarChart(a).apply {
                mobileColor = this@StatsScreen.mobileColor
                wifiColor = this@StatsScreen.wifiColor
                textColor = Ui.secondary(a)
            }
            addView(chart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)))
        })

        list = Ui.card(a, "Ayrıntılar", "📋")
        root.addView(list)
        root.addView(Ui.hint(a, "Ortalama hız, sadece veri akarken geçen süreye göre hesaplanır (boşta geçen saniyeler sayılmaz).").apply {
            setPadding(dp(4), 0, dp(4), dp(8))
        })
    }

    override fun onShow() {
        ticks = 0
        render()
    }

    override fun tick() {
        if (++ticks % 5 == 0) renderToday()
    }

    private fun renderToday() {
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        val t = StatsStore.today(a)
        todayMobile.value.text = Fmt.bytes(t.mobile)
        todayMobile.value.setTextColor(mobileColor)
        todayMobile.sub.text = "Rekor ${Fmt.speed(t.peakMobile, bits)}\nOrt. ${if (t.avgMobile > 0) Fmt.speed(t.avgMobile, bits) else "—"}"
        todayWifi.value.text = Fmt.bytes(t.wifi)
        todayWifi.value.setTextColor(wifiColor)
        todayWifi.sub.text = "Rekor ${Fmt.speed(t.peakWifi, bits)}\nOrt. ${if (t.avgWifi > 0) Fmt.speed(t.avgWifi, bits) else "—"}"
    }

    private fun render() {
        renderToday()
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        val buckets = when (mode) {
            0 -> daily(7)
            1 -> daily(30)
            2 -> monthly()
            else -> yearly()
        }
        chart.labelEvery = if (mode == 1) 5 else 1
        chart.data = buckets.map { Triple(it.label, it.stats.mobile, it.stats.wifi) }
        chart.tooltip = { i ->
            val s = buckets[i].stats
            "${buckets[i].title}\n${Fmt.bytes(s.mobile + s.wifi)}  (mobil ${Fmt.bytes(s.mobile)})"
        }

        val sum = DayStats()
        buckets.forEach { sum.merge(it.stats) }
        val span = when (mode) {
            0 -> "Son 7 gün"
            1 -> "Son 30 gün"
            2 -> "Son 12 ay"
            else -> "Tüm yıllar"
        }
        val used = buckets.count { it.stats.mobile + it.stats.wifi > 0 }
        totalValue.text = Fmt.bytes(sum.mobile + sum.wifi)
        val unitName = when (mode) {
            2 -> "ay"
            3 -> "yıl"
            else -> "gün"
        }
        totalSub.text = "$span toplamı · mobil ${Fmt.bytes(sum.mobile)} · Wi-Fi ${Fmt.bytes(sum.wifi)}" +
            if (used > 0) "\nKayıtlı $unitName başına ortalama ${Fmt.bytes((sum.mobile + sum.wifi) / used)}" else ""
        totalSplit.set(sum.mobile, sum.wifi)

        while (list.childCount > 1) list.removeViewAt(1)
        val rows = buckets.asReversed().filter { it.stats.mobile + it.stats.wifi > 0 }
        if (rows.isEmpty()) {
            list.addView(Ui.emptyState(a, "📭", "Henüz kayıt yok", "Uygulama çalıştıkça günler burada birikir."))
            return
        }
        val max = rows.maxOf { it.stats.mobile + it.stats.wifi }.coerceAtLeast(1)
        rows.forEachIndexed { i, b ->
            if (i > 0) list.addView(Ui.divider(a))
            list.addView(row(b, max, bits))
        }
    }

    private fun row(b: Bucket, max: Long, bits: Boolean): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        val s = b.stats
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(Ui.text(a, b.title, 15f).apply { typeface = Ui.MEDIUM }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Ui.text(a, Fmt.bytes(s.mobile + s.wifi), 15f, bold = true))
        addView(head)
        val bar = SplitBar(a).apply {
            colorA = mobileColor
            colorB = wifiColor
            set(s.mobile, s.wifi)
        }
        val frac = (s.mobile + s.wifi).toFloat() / max
        addView(LinearLayout(a).apply {
            addView(bar, LinearLayout.LayoutParams(0, dp(6), frac.coerceAtLeast(0.02f)))
            addView(View(a), LinearLayout.LayoutParams(0, dp(6), (1 - frac).coerceAtLeast(0f)))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)).apply { topMargin = dp(4); bottomMargin = dp(6) })
        addView(Ui.text(a, "Mobil ${Fmt.bytes(s.mobile)} · rekor ${Fmt.speed(s.peakMobile, bits)} · ort. ${avg(s.avgMobile, bits)}", 12.5f, color = mobileColor))
        addView(Ui.text(a, "Wi-Fi ${Fmt.bytes(s.wifi)} · rekor ${Fmt.speed(s.peakWifi, bits)} · ort. ${avg(s.avgWifi, bits)}", 12.5f, color = wifiColor))
    }

    private fun avg(v: Long, bits: Boolean) = if (v > 0) Fmt.speed(v, bits) else "—"

    private fun daily(n: Int): List<Bucket> {
        val all = StatsStore.all(a)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -(n - 1))
        val label = SimpleDateFormat("d", tr)
        val title = SimpleDateFormat("d MMMM EEEE", tr)
        return List(n) {
            val s = synchronized(StatsStore) { all[StatsStore.key(cal.timeInMillis)] } ?: DayStats()
            val b = Bucket(label.format(cal.time), title.format(cal.time), s)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            b
        }
    }

    private fun monthly(): List<Bucket> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.MONTH, -11)
        val key = SimpleDateFormat("yyyy-MM", Locale.US)
        val label = SimpleDateFormat("MMM", tr)
        val title = SimpleDateFormat("MMMM yyyy", tr)
        return List(12) {
            val b = Bucket(label.format(cal.time), title.format(cal.time), StatsStore.aggregate(a, key.format(cal.time)))
            cal.add(Calendar.MONTH, 1)
            b
        }
    }

    private fun yearly(): List<Bucket> {
        val now = Calendar.getInstance().get(Calendar.YEAR)
        val first = StatsStore.firstYear(a).coerceAtMost(now)
        return (first..now).map { y -> Bucket("$y", "$y yılı", StatsStore.aggregate(a, "$y")) }
    }
}
