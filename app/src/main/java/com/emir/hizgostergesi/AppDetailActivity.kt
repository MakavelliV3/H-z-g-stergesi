package com.emir.hizgostergesi

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Tek bir uygulamanın son 30 günü: grafik, mobil/Wi-Fi, ön/arka plan, indirme/yükleme. */
class AppDetailActivity : Activity() {

    companion object {
        const val EXTRA_UID = "uid"
    }

    private val mobileColor = 0xFFFF9800.toInt()
    private val wifiColor = 0xFF2196F3.toInt()

    private lateinit var head: LinearLayout
    private lateinit var chart: BarChart
    private lateinit var body: LinearLayout
    private lateinit var limitBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, root) = Ui.page(this)
        root.addView(Ui.topBar(this, "Uygulama"))

        head = Ui.card(this)
        root.addView(head)

        root.addView(Ui.card(this, "Son 30 gün").apply {
            val legend = LinearLayout(this@AppDetailActivity).apply { orientation = LinearLayout.HORIZONTAL }
            legend.addView(Ui.text(this@AppDetailActivity, "■ Mobil   ", 13f, color = mobileColor))
            legend.addView(Ui.text(this@AppDetailActivity, "■ Wi-Fi", 13f, color = wifiColor))
            addView(legend)
            chart = BarChart(this@AppDetailActivity).apply {
                mobileColor = this@AppDetailActivity.mobileColor
                wifiColor = this@AppDetailActivity.wifiColor
                textColor = Ui.secondary(this@AppDetailActivity)
            }
            addView(chart, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this@AppDetailActivity, 190)))
        })

        body = Ui.card(this, "Ayrıntılar", "📋")
        root.addView(body)
        limitBox = Ui.card(this, "Günlük sınır", "⏳")
        root.addView(limitBox)
        setContentView(scroll)
        load()
    }

    private fun load() {
        val uid = intent.getIntExtra(EXTRA_UID, -1)
        if (uid == -1 || !NetStats.hasAccess(this)) {
            head.addView(Ui.text(this, "Bu ekran için \"Kullanım erişimi\" izni gerekiyor.", 15f))
            return
        }
        body.addView(Ui.hint(this, "Yükleniyor…"))

        Thread {
            val now = System.currentTimeMillis()
            val starts = LongArray(30)
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_MONTH, -29)
            }
            for (i in 0 until 30) {
                starts[i] = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            val daily = NetStats.appDaily(this, uid, starts, now)
            val summary = NetStats.queryApps(this, starts[0], now, withNames = false).firstOrNull { it.uid == uid }
                ?: AppUsage(uid)
            NetStats.describe(this, summary)
            val today = daily.last().let { it[0] + it[1] }

            runOnUiThread {
                if (!isFinishing) {
                    render(summary, starts, daily)
                    renderLimit(uid, today)
                }
            }
        }.start()
    }

    private fun renderLimit(uid: Int, today: Long) {
        while (limitBox.childCount > 1) limitBox.removeViewAt(1)
        val current = Prefs.appLimits(this)[uid] ?: 0
        limitBox.addView(Ui.hint(this, "Bu uygulama bir günde seçtiğin miktarı geçerse bildirim gelir (yaklaşık 15 dakikada bir kontrol edilir)."))
        limitBox.addView(Ui.choiceRow(
            this,
            listOf("Kapalı" to 0, "100 MB" to 100, "250 MB" to 250, "500 MB" to 500, "1 GB" to 1024, "2 GB" to 2048, "5 GB" to 5120),
            current
        ) {
            Prefs.setAppLimit(this, uid, it)
            renderLimit(uid, today)
        })
        if (current > 0) {
            val ratio = today.toDouble() / Fmt.mbToBytes(current)
            limitBox.addView(Ui.text(this, "Bugün ${Fmt.bytes(today)} / ${Fmt.mb(current)}", 15f, topDp = 8).apply { typeface = Ui.MEDIUM })
            limitBox.addView(PercentBar(this, ratio.toFloat(), if (ratio >= 1) 0xFFE53935.toInt() else Ui.ACCENT),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 8)).apply { topMargin = Ui.dp(this@AppDetailActivity, 4) })
        }
        val pkg = packageManager.getPackagesForUid(uid)?.firstOrNull()
        if (pkg != null) {
            limitBox.addView(Ui.button(this, "Android'deki veri ayarlarını aç") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
            })
        }
    }

    private fun render(a: AppUsage, starts: LongArray, daily: Array<LongArray>) {
        title = a.name

        // Başlık kartı
        head.removeAllViews()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            if (a.icon != null) setImageDrawable(a.icon) else setImageResource(android.R.drawable.sym_def_app_icon)
        }
        row.addView(icon, LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)).apply {
            rightMargin = Ui.dp(this@AppDetailActivity, 14)
        })
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(Ui.text(this, a.name, 20f, bold = true))
        names.addView(Ui.hint(this, a.category))
        row.addView(names)
        head.addView(row)
        head.addView(Ui.text(this, "Son 30 günde ${Fmt.bytes(a.total)}", 17f, bold = true, topDp = 12, color = Ui.ACCENT))
        if (a.heavyUploader) {
            head.addView(Ui.text(this, "⬆ Bu uygulama aldığından fazla veri gönderiyor (${Fmt.bytes(a.tx)} gönderdi, ${Fmt.bytes(a.rx)} aldı).",
                14f, color = 0xFFF44336.toInt()))
        }

        // Grafik
        val dayLabel = SimpleDateFormat("d", Locale("tr"))
        chart.data = starts.indices.map { i ->
            Triple(dayLabel.format(java.util.Date(starts[i])), daily[i][0], daily[i][1])
        }

        // Ayrıntılar (kart başlığı kalsın)
        while (body.childCount > 1) body.removeViewAt(1)
        fun line(title: String, value: String) {
            val l = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, Ui.dp(this@AppDetailActivity, 4), 0, Ui.dp(this@AppDetailActivity, 4))
            }
            l.addView(Ui.text(this, title, 15f, color = Ui.secondary(this)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            l.addView(Ui.text(this, value, 15f, bold = true))
            body.addView(l)
        }
        fun pct(part: Long) = if (a.total > 0) " · %${part * 100 / a.total}" else ""

        line("Mobil veri", Fmt.bytes(a.mobile) + pct(a.mobile))
        line("Wi-Fi", Fmt.bytes(a.wifi) + pct(a.wifi))
        line("Ön planda (sen kullanırken)", Fmt.bytes(a.foreground) + pct(a.foreground))
        line("Arka planda", Fmt.bytes(a.background) + pct(a.background))
        line("İndirilen", Fmt.bytes(a.rx))
        line("Gönderilen", Fmt.bytes(a.tx))

        val totals = daily.map { it[0] + it[1] }
        val used = totals.count { it > 0 }
        line("Günlük ortalama", Fmt.bytes(if (used > 0) totals.sum() / used else 0))
        val maxIdx = totals.indices.maxByOrNull { totals[it] } ?: 0
        if (totals.getOrElse(maxIdx) { 0L } > 0) {
            val dayTitle = SimpleDateFormat("d MMMM EEEE", Locale("tr"))
            line("En çok kullandığı gün", "${dayTitle.format(java.util.Date(starts[maxIdx]))} · ${Fmt.bytes(totals[maxIdx])}")
        }
        if (a.background > a.foreground && a.background > 50L * 1048576) {
            body.addView(Ui.hint(this, "Bu uygulama verisinin çoğunu sen kullanmıyorken harcıyor. İstersen Ayarlar → Uygulamalar → ${a.name} → Mobil veri kısmından arka plan veri kullanımını kapatabilirsin."))
        }
    }
}
