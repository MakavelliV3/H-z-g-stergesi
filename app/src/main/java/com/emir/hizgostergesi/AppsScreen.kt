package com.emir.hizgostergesi

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Uygulamalar: hangi uygulama ne kadar veri kullandı. Arama, filtre, sıralama, zaman aralığı,
 * önceki döneme göre kıyas ve sayımı sıfırlama. "Kullanım erişimi" izni ister.
 */
class AppsScreen(a: MainActivity) : Screen(a) {

    private val tr = Locale("tr", "TR")
    private val stamp = SimpleDateFormat("d MMM HH:mm", tr)

    private lateinit var status: TextView
    private lateinit var resetText: TextView
    private lateinit var rangeHolder: LinearLayout
    private lateinit var content: LinearLayout

    private var range = 3
    private var sort = 0          // 0: toplam, 1: en çok artan, 2: arka plan
    private var network = 0       // 0: tümü, 1: mobil, 2: Wi-Fi
    private var hideSystem = true
    private var query = ""
    private var customStart = 0L
    private var customEnd = 0L
    private var loadId = 0

    private var rows: List<AppUsage> = emptyList()
    private var prev: Map<Int, Long> = emptyMap()
    private var label = ""

    private val ranges = listOf(
        "1 sa" to 0, "3 sa" to 1, "12 sa" to 2, "Bugün" to 3, "Dün" to 4, "7 gün" to 5, "30 gün" to 6,
        "Sayaç dönemi" to 7, "Sıfırlamadan beri" to 8, "Özel…" to 9
    )

    override fun build() {
        root.addView(Ui.screenTitle(a, "Uygulamalar", "Hangi uygulama ne kadar veri kullandı"))
        root.addView(Ui.searchField(a, "Uygulama ara") {
            query = it.trim()
            render()
        })

        rangeHolder = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rangeHolder)
        buildRange()
        root.addView(Ui.choiceRow(a, listOf("Tüm ağlar" to 0, "Mobil" to 1, "Wi-Fi" to 2), network) {
            network = it
            render()
        })
        root.addView(Ui.choiceRow(a, listOf("En çok kullanan" to 0, "En çok artan" to 1, "En çok arka plan" to 2), sort) {
            sort = it
            render()
        })

        root.addView(Ui.card(a, "Sayım ve filtre", "🧮").apply {
            addView(Ui.switch(a, "Sistem bileşenlerini gizle", hideSystem) {
                hideSystem = it
                render()
            })
            resetText = Ui.hint(a, "")
            addView(Ui.button(a, "Sayımı şimdi sıfırla") {
                prefs.edit().putLong(Prefs.APPS_RESET_AT, System.currentTimeMillis()).apply()
                range = 8
                buildRange()
                updateResetText()
                load()
                Toast.makeText(a, "Sayım sıfırlandı, bundan sonrası sayılacak", Toast.LENGTH_SHORT).show()
            })
            addView(resetText)
        })
        status = Ui.hint(a, "").apply { setPadding(dp(4), dp(2), dp(4), dp(10)) }
        root.addView(status)
        content = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)

        updateResetText()
    }

    override fun onShow() {
        if (NetStats.hasAccess(a)) load() else askAccess()
    }

    private fun askAccess() {
        status.text = ""
        content.removeAllViews()
        content.addView(Ui.card(a).apply {
            addView(Ui.emptyState(a, "🔐", "Kullanım erişimi gerekiyor",
                "Uygulamaların ne kadar veri kullandığını Android'in kendi kayıtlarından okuruz. Açılan listede Hız Göstergesi'ni bulup izni aç, sonra geri dön.",
                "İzin ver" to { a.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }))
        })
    }

    private fun buildRange() {
        rangeHolder.removeAllViews()
        rangeHolder.addView(Ui.choiceRow(a, ranges, range) {
            if (it == 9) pickCustom() else {
                range = it
                load()
            }
        })
    }

    private fun updateResetText() {
        val at = prefs.getLong(Prefs.APPS_RESET_AT, 0L)
        resetText.text = if (at == 0L) {
            "Hiç sıfırlanmadı. Sıfırlarsan \"Sıfırlamadan beri\" aralığı o andan itibaren sayar; Android'in kayıtları silinmez."
        } else {
            "Son sıfırlama: ${stamp.format(Date(at))}"
        }
    }

    private fun load() {
        if (!NetStats.hasAccess(a)) {
            askAccess()
            return
        }
        val id = ++loadId
        status.text = "Yükleniyor…"
        if (rows.isEmpty()) {
            content.removeAllViews()
            content.addView(Ui.card(a).apply { addView(Ui.skeleton(a, 6)) })
        }
        val (start, end) = rangeMillis()
        val lbl = rangeLabel(start, end)
        Thread {
            val r = NetStats.queryApps(a, start, end)
            val len = (minOf(end, System.currentTimeMillis()) - start).coerceAtLeast(3_600_000L)
            val p = NetStats.queryApps(a, start - len, start, withNames = false).associate { it.uid to it.total }
            a.runOnUiThread {
                if (id != loadId || a.isFinishing) return@runOnUiThread
                rows = r
                prev = p
                label = lbl
                render()
            }
        }.start()
    }

    private fun pickCustom() {
        val now = System.currentTimeMillis()
        pickDateTime("Başlangıç", if (customStart > 0) customStart else now - 86_400_000L) { start ->
            pickDateTime("Bitiş", if (customEnd > start) customEnd else now) { end ->
                if (end <= start) {
                    Toast.makeText(a, "Bitiş, başlangıçtan sonra olmalı", Toast.LENGTH_SHORT).show()
                    buildRange()
                } else {
                    customStart = start
                    customEnd = end
                    range = 9
                    buildRange()
                    load()
                }
            }
        }
    }

    private fun pickDateTime(title: String, initial: Long, done: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(a, { _, y, m, d ->
            TimePickerDialog(a, { _, h, min ->
                cal.set(y, m, d, h, min, 0)
                cal.set(Calendar.MILLISECOND, 0)
                done(cal.timeInMillis)
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).apply {
                setTitle("$title saati")
                setOnCancelListener { buildRange() }
            }.show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            setTitle("$title tarihi")
            setOnCancelListener { buildRange() }
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun rangeMillis(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val today = Insights.startOfToday()
        val day = 86_400_000L
        return when (range) {
            0 -> now - 3_600_000L to now
            1 -> now - 3 * 3_600_000L to now
            2 -> now - 12 * 3_600_000L to now
            4 -> today - day to today
            5 -> now - 7 * day to now
            6 -> now - 30 * day to now
            7 -> prefs.getLong(Prefs.WINDOW_START, now - 3_600_000L) to now
            8 -> prefs.getLong(Prefs.APPS_RESET_AT, 0L).let { if (it > 0) it else today } to now
            9 -> customStart to customEnd
            else -> today to now
        }
    }

    private fun rangeLabel(start: Long, end: Long): String = when (range) {
        8 -> "Sıfırlamadan beri (${stamp.format(Date(start))})"
        9 -> "${stamp.format(Date(start))} – ${stamp.format(Date(end))}"
        else -> ranges.first { it.second == range }.first
    }

    /** Seçili ağa göre bir uygulamanın değeri. */
    private fun value(u: AppUsage) = when (network) {
        1 -> u.mobile
        2 -> u.wifi
        else -> u.total
    }

    private fun change(now: Long, before: Long?): Pair<String, Int>? {
        val b = before ?: 0L
        if (b < 1_000_000L) return if (now >= 5_000_000L) "yeni" to Ui.ACCENT else null
        val pct = ((now - b) * 100 / b).toInt()
        return when {
            pct >= 5 -> "↑ %$pct" to 0xFFE53935.toInt()
            pct <= -5 -> "↓ %${-pct}" to 0xFF2E7D32.toInt()
            else -> null
        }
    }

    private fun render() {
        if (!::content.isInitialized || label.isEmpty()) return
        content.removeAllViews()

        var list = rows.filter { value(it) > 0 }
        if (hideSystem) list = list.filter { it.category != "Sistem" }
        if (query.isNotEmpty()) {
            val q = query.lowercase(tr)
            list = list.filter { it.name.lowercase(tr).contains(q) || it.category.lowercase(tr).contains(q) }
        }
        list = when (sort) {
            1 -> list.sortedByDescending { it.total - (prev[it.uid] ?: 0L) }
            2 -> list.sortedByDescending { it.background }
            else -> list.sortedByDescending { value(it) }
        }

        val total = list.sumOf { value(it) }
        val netName = when (network) {
            1 -> "mobil"
            2 -> "Wi-Fi"
            else -> "toplam"
        }
        status.text = "$label · $netName ${Fmt.bytes(total)} · ${list.size} uygulama"

        if (list.isEmpty()) {
            content.addView(Ui.card(a).apply {
                addView(if (query.isNotEmpty()) Ui.emptyState(a, "🔍", "Sonuç yok", "\"$query\" ile eşleşen uygulama bulunamadı.")
                else Ui.emptyState(a, "📭", "Bu aralıkta kayıt yok",
                    "Android bu kayıtları birkaç saatlik dilimler hâlinde tutar; çok kısa aralıklar boş görünebilir."))
            })
            return
        }

        // İlk 5 halka grafiği (arama yokken)
        if (query.isEmpty()) {
            val palette = intArrayOf(Ui.ACCENT, Ui.ACCENT_2, 0xFFFF9800.toInt(), 0xFF4CAF50.toInt(), 0xFFE91E63.toInt(), 0xFF9E9E9E.toInt())
            val top5 = list.sortedByDescending { value(it) }.take(5)
            val rest = total - top5.sumOf { value(it) }
            val slices = top5.mapIndexed { i, u -> value(u) to palette[i] } + (if (rest > 0) listOf(rest to palette[5]) else emptyList())
            content.addView(Ui.card(a, "En çok kullanan 5 uygulama", "🍩").apply {
                val row = LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(DonutChart(a).apply { set(slices, Fmt.bytes(total), netName) },
                    LinearLayout.LayoutParams(dp(132), dp(132)).apply { rightMargin = dp(16) })
                val legend = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
                top5.forEachIndexed { i, u ->
                    legend.addView(Ui.text(a, "● ${u.name}", 13f, color = palette[i]).apply { maxLines = 1 })
                    legend.addView(Ui.hint(a, "   ${Fmt.bytes(value(u))} · %${value(u) * 100 / total.coerceAtLeast(1)}"))
                }
                if (rest > 0) legend.addView(Ui.text(a, "● Diğerleri  ${Fmt.bytes(rest)}", 13f, color = Ui.secondary(a)))
                row.addView(legend, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(row)
            })
        }

        val card = Ui.card(a)
        list.take(80).forEachIndexed { i, u ->
            if (i > 0) card.addView(Ui.divider(a))
            card.addView(appRow(u))
        }
        content.addView(card)
        val notes = ArrayList<String>()
        notes += "↑↓: hemen önceki eşit uzunluktaki döneme göre değişim. \"yeni\": önceki dönemde neredeyse hiç kullanmamış."
        if (list.any { it.heavyUploader }) notes += "⬆: aldığından fazla veri gönderiyor (yedekleme, yükleme ya da görüntülü arama olabilir)."
        content.addView(Ui.hint(a, notes.joinToString("\n")).apply { setPadding(dp(4), 0, dp(4), dp(12)) })
    }

    private fun appRow(u: AppUsage): View {
        val line = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            background = Ui.ripple(Ui.rounded(0, dp(12).toFloat()))
            isClickable = true
            setOnClickListener {
                a.startActivity(Intent(a, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_UID, u.uid))
            }
        }
        line.addView(ImageView(a).apply {
            if (u.icon != null) setImageDrawable(u.icon) else setImageResource(android.R.drawable.sym_def_app_icon)
            contentDescription = u.name
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) })

        val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(Ui.text(a, if (u.heavyUploader) "${u.name}  ⬆" else u.name, 15f).apply {
            typeface = Ui.MEDIUM
            maxLines = 1
            setPadding(0, 0, 0, 0)
        })
        texts.addView(Ui.hint(a, "Mobil ${Fmt.bytes(u.mobile)} · Wi-Fi ${Fmt.bytes(u.wifi)}").apply { setPadding(0, 0, 0, 0) })
        texts.addView(Ui.hint(a, "Ön plan ${Fmt.bytes(u.foreground)} · Arka plan ${Fmt.bytes(u.background)}").apply { setPadding(0, 0, 0, 0) })
        line.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val right = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val shown = if (sort == 2) u.background else value(u)
        right.addView(Ui.text(a, Fmt.bytes(shown), 15f, bold = true, color = Ui.accentText(a)).apply {
            gravity = Gravity.END
            setPadding(0, 0, 0, dp(2))
        })
        change(u.total, prev[u.uid])?.let { (txt, col) ->
            right.addView(TextView(a).apply {
                text = txt
                textSize = 11f
                typeface = Ui.MEDIUM
                setTextColor(col)
                setPadding(dp(8), dp(2), dp(8), dp(2))
                background = Ui.rounded((col and 0x00FFFFFF) or 0x22000000, dp(50).toFloat())
            })
        }
        line.addView(right)
        return line
    }
}
