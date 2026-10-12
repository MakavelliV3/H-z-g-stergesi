package com.emir.hizgostergesi

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Hangi uygulama ne kadar veri kullandı. "Kullanım erişimi" izni ister. Dokununca ayrıntı açılır. */
class AppsActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private var range = 0
    private var sort = 0 // 0: toplam, 1: arka plan, 2: mobil
    private var loadId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, root) = Ui.page(this)

        root.addView(Ui.choiceRow(
            this, listOf("Bugün" to 0, "Bu dönem" to 1, "Son 7 gün" to 2, "Son 30 gün" to 3), range
        ) {
            range = it
            load()
        })
        root.addView(Ui.choiceRow(
            this, listOf("En çok toplam" to 0, "En çok arka plan" to 1, "En çok mobil" to 2), sort
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
        Thread {
            val rows = NetStats.queryApps(this, start, end)
            runOnUiThread { if (id == loadId && !isFinishing) render(rows) }
        }.start()
    }

    private fun rangeMillis(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val start = when (range) {
            0 -> Insights.startOfToday()
            1 -> Prefs.get(this).getLong(Prefs.WINDOW_START, now - 3_600_000L)
            2 -> now - 7 * 86_400_000L
            else -> now - 30 * 86_400_000L
        }
        return start to now + 60_000L
    }

    private fun render(all: List<AppUsage>) {
        content.removeAllViews()
        if (all.isEmpty()) {
            status.text = "Bu aralık için kayıt bulunamadı."
            return
        }
        val rows = when (sort) {
            1 -> all.sortedByDescending { it.background }
            2 -> all.sortedByDescending { it.mobile }
            else -> all
        }.take(60)

        val total = all.sumOf { it.total }
        val bg = all.sumOf { it.background }
        status.text = "Toplam ${Fmt.bytes(total)} • arka planda ${Fmt.bytes(bg)} • dokun: ayrıntı"

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
            line.addView(Ui.text(this, Fmt.bytes(value), 15f, bold = true, color = Ui.ACCENT))
            list.addView(line)
        }
        content.addView(list)
        if (rows.any { it.heavyUploader }) {
            content.addView(Ui.hint(this, "⬆ işaretli uygulamalar aldığından fazla veri gönderiyor (yedekleme, yükleme ya da görüntülü arama olabilir)."))
        }
    }
}
