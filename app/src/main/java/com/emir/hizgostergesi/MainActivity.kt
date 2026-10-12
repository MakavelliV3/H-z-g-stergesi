package com.emir.hizgostergesi

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** Ana ekran: canlı hız, özet kutuları, ekranlar arası geçiş ve bugün en çok kullananlar. */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var heroDown: TextView
    private lateinit var heroUp: TextView
    private lateinit var heroConn: TextView
    private lateinit var heroUsage: TextView
    private lateinit var spark: Sparkline

    private lateinit var tileToday: Ui.StatTile
    private lateinit var tilePeriod: Ui.StatTile
    private lateinit var tileMonth: Ui.StatTile
    private lateinit var tilePeak: Ui.StatTile
    private lateinit var quotaBar: ProgressBar

    private lateinit var topApps: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var tick = 0

    private val refresh = object : Runnable {
        override fun run() {
            refreshLive()
            if (tick++ % 30 == 0) loadTopApps()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        if (!prefs.getBoolean(Prefs.ONBOARDED, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(buildUi())
        startForegroundService(Intent(this, SpeedService::class.java))
    }

    override fun onResume() {
        super.onResume()
        if (!::prefs.isInitialized || !::heroDown.isInitialized) return
        tick = 0
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun buildUi(): View {
        val (scroll, root) = Ui.page(this)
        root.addView(Ui.topBar(this, "Hız Göstergesi", back = false, action = "⚙" to {
            startActivity(Intent(this, SettingsActivity::class.java))
        }))

        root.addView(heroCard())

        tileToday = Ui.StatTile(this, "Bugün")
        tilePeriod = Ui.StatTile(this, "Bu dönem")
        quotaBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Ui.ACCENT)
            visibility = View.GONE
        }
        tilePeriod.addView(quotaBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(6) })
        tileMonth = Ui.StatTile(this, "Bu ay")
        tilePeak = Ui.StatTile(this, "Bugünün rekoru")
        root.addView(Ui.pairRow(this, tileToday, tilePeriod))
        root.addView(Ui.pairRow(this, tileMonth, tilePeak))

        root.addView(navRow())

        root.addView(Ui.card(this, "Bugün en çok kullananlar", "🏆").apply {
            topApps = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(topApps)
        })

        root.addView(Ui.hint(this, "🔒 İnternet izni yok · Tüm veriler sadece bu telefonda kalır").apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })
        return scroll
    }

    /** Üstteki renkli kart: canlı hız, bağlantı türü ve son bir dakikanın grafiği. */
    private fun heroCard(): View {
        val pad = dp(20)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.ACCENT, Ui.ACCENT_2)
            ).apply { cornerRadius = dp(28).toFloat() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }

            val top = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(Ui.label(this@MainActivity, "Anlık hız").apply {
                setTextColor(0xD9FFFFFF.toInt())
                setPadding(0, 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            heroConn = TextView(this@MainActivity).apply {
                textSize = 13f
                typeface = Ui.MEDIUM
                setTextColor(Color.WHITE)
                setPadding(dp(12), dp(5), dp(12), dp(5))
                background = Ui.rounded(0x33FFFFFF, dp(50).toFloat())
            }
            top.addView(heroConn)
            addView(top)

            heroDown = Ui.text(this@MainActivity, "↓ —", 40f, bold = true, topDp = 6, color = Color.WHITE)
            heroUp = Ui.text(this@MainActivity, "↑ —", 18f, color = 0xE6FFFFFF.toInt()).apply { typeface = Ui.MEDIUM }
            addView(heroDown)
            addView(heroUp)

            spark = Sparkline(this@MainActivity)
            addView(spark, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)).apply {
                topMargin = dp(10)
            })
            heroUsage = Ui.text(this@MainActivity, "", 13f, topDp = 8, color = 0xD9FFFFFF.toInt())
            addView(heroUsage)
        }
    }

    private fun navRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        val items = listOf(
            Triple("📊", "İstatistik", HistoryActivity::class.java),
            Triple("📱", "Uygulamalar", AppsActivity::class.java),
            Triple("🔍", "Analiz", AnalysisActivity::class.java)
        )
        items.forEachIndexed { i, (emoji, title, cls) ->
            row.addView(navTile(emoji, title) { startActivity(Intent(this, cls)) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) leftMargin = dp(5)
                    if (i < items.lastIndex) rightMargin = dp(5)
                })
        }
        return row
    }

    private fun navTile(emoji: String, title: String, onClick: () -> Unit): View {
        val pad = dp(14)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
            background = Ui.ripple(Ui.rounded(Ui.surface(this@MainActivity), dp(22).toFloat()))
            isClickable = true
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                text = emoji
                textSize = 20f
                gravity = Gravity.CENTER
                background = Ui.rounded(Ui.accentSoft(), dp(22).toFloat())
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(Ui.text(this@MainActivity, title, 13f, topDp = 8).apply {
                typeface = Ui.MEDIUM
                maxLines = 1
                gravity = Gravity.CENTER
            })
        }
    }

    /** Canlı hız kartını ve özet kutularını yeniler. */
    private fun refreshLive() {
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        heroDown.text = "↓ ${Fmt.speed(SpeedService.lastDown, bits)}"
        heroUp.text = "↑ ${Fmt.speed(SpeedService.lastUp, bits)}"
        val conn = ConnInfo.label
        heroConn.text = if (conn.isEmpty()) "Bağlantı yok" else conn
        spark.data = SpeedService.recent()

        val hours = prefs.getInt(Prefs.HOURS, 1)
        val mobileOnly = prefs.getBoolean(Prefs.MOBILE_ONLY, false)
        val now = System.currentTimeMillis()
        val start = prefs.getLong(Prefs.WINDOW_START, now)
        val mobile = prefs.getLong(Prefs.USED_MOBILE, 0L)
        val wifi = prefs.getLong(Prefs.USED_WIFI, 0L)
        val left = Fmt.remaining(start + hours * 3_600_000L - now)
        val used = if (mobileOnly) mobile else mobile + wifi
        heroUsage.text = "Son 60 saniye  ·  dönem ${Fmt.bytes(used)}"

        val today = StatsStore.today(this)
        tileToday.value.text = Fmt.bytes(today.mobile + today.wifi)
        tileToday.sub.text = "Mobil ${Fmt.bytes(today.mobile)}\nWi-Fi ${Fmt.bytes(today.wifi)}"

        tilePeriod.value.text = Fmt.bytes(used)
        val quotaMb = prefs.getInt(Prefs.QUOTA_MB, 0)
        if (quotaMb > 0) {
            val ratio = used.toDouble() / (quotaMb * 1048576.0)
            quotaBar.visibility = View.VISIBLE
            quotaBar.progress = (ratio * 1000).toInt().coerceIn(0, 1000)
            quotaBar.progressTintList = ColorStateList.valueOf(if (ratio >= 1) 0xFFE53935.toInt() else Ui.ACCENT)
            tilePeriod.sub.text = "Son $hours sa · sınırın %${(ratio * 100).toInt()}'i\n$left sonra sıfırlanır"
        } else {
            quotaBar.visibility = View.GONE
            tilePeriod.sub.text = "Son $hours sa${if (mobileOnly) " (mobil)" else ""}\n$left sonra sıfırlanır"
        }

        val month = StatsStore.aggregate(this, StatsStore.key().take(7))
        tileMonth.value.text = Fmt.bytes(month.mobile + month.wifi)
        tileMonth.sub.text = "Mobil ${Fmt.bytes(month.mobile)}\nWi-Fi ${Fmt.bytes(month.wifi)}"

        val peak = maxOf(today.peakMobile, today.peakWifi)
        tilePeak.value.text = Fmt.speed(peak, bits)
        tilePeak.sub.text = "Mobil ${Fmt.speed(today.peakMobile, bits)}\nWi-Fi ${Fmt.speed(today.peakWifi, bits)}"
    }

    /** Bugün en çok veri kullanan 3 uygulama (Android kayıtlarından). */
    private fun loadTopApps() {
        if (!NetStats.hasAccess(this)) {
            topApps.removeAllViews()
            topApps.addView(Ui.hint(this, "Uygulamaların ne kadar veri harcadığını görmek için \"Kullanım erişimi\" iznini ver."))
            topApps.addView(Ui.button(this, "İzin ver") {
                startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            })
            return
        }
        Thread {
            val apps = NetStats.queryApps(this, Insights.startOfToday(), System.currentTimeMillis() + 60_000L)
                .filter { it.category != "Sistem" }
                .take(3)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                topApps.removeAllViews()
                if (apps.isEmpty()) {
                    topApps.addView(Ui.hint(this, "Bugün henüz kayıt yok."))
                    return@runOnUiThread
                }
                val max = apps.first().total.coerceAtLeast(1L)
                apps.forEach { a -> topApps.addView(appRow(a, max)) }
                topApps.addView(Ui.button(this, "Tüm uygulamalar") {
                    startActivity(Intent(this, AppsActivity::class.java))
                })
            }
        }.start()
    }

    private fun appRow(a: AppUsage, max: Long): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            background = Ui.ripple(Ui.rounded(0, dp(12).toFloat()))
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@MainActivity, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_UID, a.uid))
            }
        }
        row.addView(ImageView(this).apply {
            if (a.icon != null) setImageDrawable(a.icon) else setImageResource(android.R.drawable.sym_def_app_icon)
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(Ui.text(this, a.name, 15f).apply {
            typeface = Ui.MEDIUM
            maxLines = 1
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Ui.text(this, Fmt.bytes(a.total), 14f, bold = true, color = Ui.ACCENT))
        mid.addView(head)
        mid.addView(PercentBar(this, a.total.toFloat() / max, Ui.ACCENT), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6)
        ).apply { topMargin = dp(2) })
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }
}
