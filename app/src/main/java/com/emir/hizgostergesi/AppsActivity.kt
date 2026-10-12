package com.emir.hizgostergesi

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Hangi uygulama ne kadar veri kullandı. "Kullanım erişimi" izni ister. Dokununca ayrıntı açılır. */
class AppsActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private var range = 3 // varsayılan: bugün
    private var customStart = 0L
    private var customEnd = 0L
    private lateinit var rangeHolder: LinearLayout
    private lateinit var resetText: TextView
    private val stamp = SimpleDateFormat("d MMM HH:mm", Locale("tr"))

    private val ranges = listOf(
        "1 sa" to 0, "3 sa" to 1, "12 sa" to 2, "Bugün" to 3, "Dün" to 4, "7 gün" to 5, "30 gün" to 6,
        "Sayaç dönemi" to 7, "Sıfırlamadan beri" to 8, "Özel…" to 9
    )
    private var sort = 0 // 0: toplam, 1: arka plan, 2: mobil, 3: en çok artan
    private var loadId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, root) = Ui.page(this)
        root.addView(Ui.topBar(this, "Uygulamalar"))

        // Sıfırlama: Android'in kayıtları silinmez, o andan itibaren yeni sayım başlar
        root.addView(Ui.card(this, "Sayım").apply {
            resetText = Ui.hint(this@AppsActivity, "")
            addView(resetText)
            addView(Ui.button(this@AppsActivity, "Sayımı şimdi sıfırla") {
                Prefs.get(this@AppsActivity).edit().putLong(Prefs.APPS_RESET_AT, System.currentTimeMillis()).apply()
                range = 8
                buildRange()
                updateResetText()
                load()
                Toast.makeText(this@AppsActivity, "Sayım sıfırlandı, bundan sonrası sayılacak", Toast.LENGTH_SHORT).show()
            })
        })
        rangeHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(rangeHolder)
        buildRange()
        updateResetText()
        root.addView(Ui.choiceRow(
            this, listOf("En çok toplam" to 0, "En çok artan" to 3, "En çok arka plan" to 1, "En çok mobil" to 2), sort
        ) {
            sort = it
            load()
        })
        status = Ui.hint(this, "")
        root.addView(status)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (NetStats.hasAccess(this)) load() else askAccess()
    }

    private fun askAccess() {
        status.text = ""
        content.removeAllViews()
        content.addView(Ui.card(this, "İzin gerekiyor").apply {
            addView(Ui.text(this@AppsActivity,
                "Uygulamaların ne kadar veri kullandığını görmek için Android'in \"Kullanım erişimi\" izni lazım. " +
                    "Açılan listede Hız Göstergesi'ni bulup izni aç, sonra geri dön.", 15f))
            addView(Ui.button(this@AppsActivity, "İzin ver", filled = true) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
        })
    }

    private fun load() {
        if (!NetStats.hasAccess(this)) {
            askAccess()
            return
        }
        val id = ++loadId
        status.text = "Yükleniyor…"
        val (start, end) = rangeMillis()
        val label = rangeLabel(start, end)
        Thread {
            val rows = NetStats.queryApps(this, start, end)
            // Kıyas için hemen önceki eşit uzunluktaki dönem
            val len = (minOf(end, System.currentTimeMillis()) - start).coerceAtLeast(3_600_000L)
            val prev = NetStats.queryApps(this, start - len, start, withNames = false).associate { it.uid to it.total }
            runOnUiThread { if (id == loadId && !isFinishing) render(rows, prev, label) }
        }.start()
    }

    private fun buildRange() {
        rangeHolder.removeAllViews()
        rangeHolder.addView(Ui.choiceRow(this, ranges, range) {
            if (it == 9) {
                pickCustom()
            } else {
                range = it
                load()
            }
        })
    }

    private fun updateResetText() {
        val at = Prefs.get(this).getLong(Prefs.APPS_RESET_AT, 0L)
        resetText.text = if (at == 0L) {
            "Hiç sıfırlanmadı. Sıfırlarsan \"Sıfırlamadan beri\" aralığı o andan itibaren sayar; Android'in kayıtları silinmez."
        } else {
            "Son sıfırlama: ${stamp.format(Date(at))}"
        }
    }

    /** Özel aralık: önce başlangıç, sonra bitiş tarihi ve saati. */
    private fun pickCustom() {
        val now = System.currentTimeMillis()
        val initialStart = if (customStart > 0) customStart else now - 86_400_000L
        pickDateTime("Başlangıç", initialStart) { start ->
            pickDateTime("Bitiş", if (customEnd > start) customEnd else now) { end ->
                if (end <= start) {
                    Toast.makeText(this, "Bitiş, başlangıçtan sonra olmalı", Toast.LENGTH_SHORT).show()
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
        DatePickerDialog(this, { _, y, m, d ->
            TimePickerDialog(this, { _, h, min ->
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
            7 -> Prefs.get(this).getLong(Prefs.WINDOW_START, now - 3_600_000L) to now
            8 -> Prefs.get(this).getLong(Prefs.APPS_RESET_AT, 0L).let { if (it > 0) it else today } to now
            9 -> customStart to customEnd
            else -> today to now
        }
    }

    private fun rangeLabel(start: Long, end: Long): String = when (range) {
        8 -> "Sıfırlamadan beri (${stamp.format(Date(start))})"
        9 -> "${stamp.format(Date(start))} – ${stamp.format(Date(end))}"
        else -> ranges.first { it.second == range }.first
    }

    /** Önceki döneme göre değişim etiketi: metin ve renk. Önemsizse null. */
    private fun change(now: Long, before: Long?): Pair<String, Int>? {
        val b = before ?: 0L
        if (b < 1048576L) return if (now >= 5L * 1048576) "yeni" to Ui.ACCENT else null
        val pct = ((now - b) * 100 / b).toInt()
        return when {
            pct >= 5 -> "↑ %$pct" to 0xFFE53935.toInt()
            pct <= -5 -> "↓ %${-pct}" to 0xFF2E7D32.toInt()
            else -> null
        }
    }

    private fun render(all: List<AppUsage>, prev: Map<Int, Long>, label: String) {
        content.removeAllViews()
        if (all.isEmpty()) {
            status.text = "Bu aralık için kayıt bulunamadı. (Android kayıtları birkaç saatlik dilimler hâlinde tutulur; çok kısa aralıklar boş görünebilir.)"
            return
        }
        val rows = when (sort) {
            1 -> all.sortedByDescending { it.background }
            2 -> all.sortedByDescending { it.mobile }
            3 -> all.sortedByDescending { it.total - (prev[it.uid] ?: 0L) }
            else -> all
        }.take(60)

        val total = all.sumOf { it.total }
        val bg = all.sumOf { it.background }
        status.text = "$label: toplam ${Fmt.bytes(total)} • arka planda ${Fmt.bytes(bg)}\nAyrıntı için bir uygulamaya dokun."

        // İlk 5 uygulama halka grafiği
        val palette = intArrayOf(Ui.ACCENT, Ui.ACCENT_2, 0xFFFF9800.toInt(), 0xFF4CAF50.toInt(), 0xFFE91E63.toInt(), 0xFF9E9E9E.toInt())
        val top5 = all.take(5)
        val rest = total - top5.sumOf { it.total }
        val slices = top5.mapIndexed { i, a -> a.total to palette[i] } + (if (rest > 0) listOf(rest to palette[5]) else emptyList())
        content.addView(Ui.card(this, "En çok kullanan 5 uygulama", "🍩").apply {
            val row = LinearLayout(this@AppsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(DonutChart(this@AppsActivity).apply { set(slices, Fmt.bytes(total), "toplam") },
                LinearLayout.LayoutParams(Ui.dp(this@AppsActivity, 140), Ui.dp(this@AppsActivity, 140)).apply {
                    rightMargin = Ui.dp(this@AppsActivity, 16)
                })
            val legend = LinearLayout(this@AppsActivity).apply { orientation = LinearLayout.VERTICAL }
            top5.forEachIndexed { i, a ->
                legend.addView(Ui.text(this@AppsActivity, "● ${a.name}", 13f, color = palette[i]).apply { maxLines = 1 })
                legend.addView(Ui.hint(this@AppsActivity, "   ${Fmt.bytes(a.total)} · %${a.total * 100 / total.coerceAtLeast(1)}"))
            }
            if (rest > 0) legend.addView(Ui.text(this@AppsActivity, "● Diğerleri  ${Fmt.bytes(rest)}", 13f, color = Ui.secondary(this@AppsActivity)))
            row.addView(legend, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(row)
        })

        val iconSize = Ui.dp(this, 40)
        val list = Ui.card(this)
        rows.forEach { r ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val v = Ui.dp(this@AppsActivity, 8)
                setPadding(0, v, 0, v)
                background = Ui.ripple(Ui.rounded(0, Ui.dp(this@AppsActivity, 12).toFloat()))
                isClickable = true
                setOnClickListener {
                    startActivity(Intent(this@AppsActivity, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_UID, r.uid))
                }
            }
            val icon = ImageView(this).apply {
                if (r.icon != null) setImageDrawable(r.icon) else setImageResource(android.R.drawable.sym_def_app_icon)
            }
            line.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize).apply {
                rightMargin = Ui.dp(this@AppsActivity, 12)
            })

            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(this).apply {
                text = if (r.heavyUploader) "${r.name}  ⬆" else r.name
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.primary(this@AppsActivity))
            })
            texts.addView(Ui.text(this, "Mobil ${Fmt.bytes(r.mobile)} • Wi-Fi ${Fmt.bytes(r.wifi)}", 12f, color = Ui.secondary(this)))
            texts.addView(Ui.text(this, "Ön plan ${Fmt.bytes(r.foreground)} • Arka plan ${Fmt.bytes(r.background)}", 12f, color = Ui.secondary(this)))
            line.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val value = when (sort) {
                1 -> r.background
                2 -> r.mobile
                else -> r.total
            }
            val right = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            right.addView(Ui.text(this, Fmt.bytes(value), 15f, bold = true, color = Ui.ACCENT).apply { gravity = Gravity.END })
            change(r.total, prev[r.uid])?.let { (txt, col) ->
                right.addView(TextView(this).apply {
                    text = txt
                    textSize = 11f
                    typeface = Ui.MEDIUM
                    setTextColor(col)
                    setPadding(Ui.dp(this@AppsActivity, 8), Ui.dp(this@AppsActivity, 2), Ui.dp(this@AppsActivity, 8), Ui.dp(this@AppsActivity, 2))
                    background = Ui.rounded((col and 0x00FFFFFF) or 0x22000000, Ui.dp(this@AppsActivity, 50).toFloat())
                })
            }
            line.addView(right)
            list.addView(line)
        }
        content.addView(list)
        if (rows.any { it.heavyUploader }) {
            content.addView(Ui.hint(this, "⬆ işaretli uygulamalar aldığından fazla veri gönderiyor (yedekleme, yükleme ya da görüntülü arama olabilir)."))
        }
        content.addView(Ui.hint(this, "↑↓ etiketleri, hemen önceki eşit uzunluktaki döneme göre değişimi gösterir. \"yeni\": önceki dönemde neredeyse hiç kullanmamış."))
    }
}
