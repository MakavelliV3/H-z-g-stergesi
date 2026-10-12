package com.emir.hizgostergesi

import android.app.NotificationManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ana sayfa: canlı hız göstergesi, sağlık uyarıları, özet kutuları ve bugün en çok kullananlar. */
class HomeScreen(a: MainActivity) : Screen(a) {

    private lateinit var connChip: TextView
    private lateinit var gauge: SpeedGauge
    private lateinit var upText: TextView
    private lateinit var spark: Sparkline
    private lateinit var health: LinearLayout

    private lateinit var tileToday: Ui.StatTile
    private lateinit var tileRoll: Ui.StatTile
    private lateinit var tilePeriod: Ui.StatTile
    private lateinit var tileMonth: Ui.StatTile
    private lateinit var quotaBar: ProgressBar

    private lateinit var split: SplitBar
    private lateinit var splitText: TextView
    private lateinit var peakText: TextView
    private lateinit var topApps: LinearLayout

    private var ticks = 0
    private var appsLoadedOnce = false

    override fun build() {
        // Başlık
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(14))
        }
        val titles = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(Ui.text(a, "Hız Göstergesi", 28f, bold = true).apply { setPadding(0, 0, 0, 0) })
        titles.addView(Ui.hint(a, SimpleDateFormat("d MMMM EEEE", Locale("tr")).format(Date())))
        head.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        connChip = TextView(a).apply {
            textSize = 13f
            typeface = Ui.MEDIUM
            setTextColor(Ui.accentText(a))
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = Ui.rounded(Ui.accentSoft(), dp(50).toFloat())
        }
        head.addView(connChip)
        root.addView(head)

        health = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        root.addView(health)

        root.addView(heroCard())

        tileToday = Ui.StatTile(a, "Bugün")
        tileRoll = Ui.StatTile(a, "Son 1 saat")
        tilePeriod = Ui.StatTile(a, "Bu dönem")
        tileMonth = Ui.StatTile(a, "Bu ay")
        quotaBar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Ui.ACCENT)
            visibility = View.GONE
        }
        tilePeriod.addView(quotaBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(6) })
        tileToday.setOnClickListener { a.select(MainActivity.TAB_STATS) }
        tileMonth.setOnClickListener { a.select(MainActivity.TAB_STATS) }
        tilePeriod.setOnClickListener { openSettings(SettingsActivity.CAT_COUNTERS) }
        tileRoll.setOnClickListener { openSettings(SettingsActivity.CAT_COUNTERS) }
        root.addView(Ui.pairRow(a, tileToday, tileRoll))
        root.addView(Ui.pairRow(a, tilePeriod, tileMonth))

        root.addView(Ui.card(a, "Bugün mobil / Wi-Fi", "📶").apply {
            split = SplitBar(a)
            addView(split, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)).apply { bottomMargin = dp(8) })
            splitText = Ui.text(a, "", 14f)
            addView(splitText)
            peakText = Ui.hint(a, "")
            addView(peakText)
        })

        root.addView(Ui.card(a, "Bugün en çok kullananlar", "🏆").apply {
            topApps = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            topApps.addView(Ui.skeleton(a, 3))
            addView(topApps)
        })

        root.addView(Ui.hint(a, "🔒 İnternet izni yok · Tüm veriler sadece bu telefonda kalır").apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        })
    }

    private fun heroCard(): View = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(14))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.ACCENT, Ui.ACCENT_2))
            .apply { cornerRadius = dp(28).toFloat() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }

        gauge = SpeedGauge(a)
        addView(gauge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        })
        upText = TextView(a).apply {
            textSize = 15f
            typeface = Ui.MEDIUM
            setTextColor(0xE6FFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        addView(upText)
        spark = Sparkline(a)
        addView(spark, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(10) })
        addView(Ui.text(a, "Son 60 saniye", 12f, topDp = 4, color = 0xB3FFFFFF.toInt()).apply { gravity = Gravity.CENTER })
    }

    private fun openSettings(cat: String) {
        a.startActivity(Intent(a, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, cat))
    }

    override fun onShow() {
        ticks = 0
        refreshHealth()
        tick()
    }

    override fun tick() {
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        gauge.bits = bits
        gauge.setSpeed(SpeedService.lastDown)
        upText.text = "↑ Yükleme ${Fmt.speed(SpeedService.lastUp, bits)}"
        spark.data = SpeedService.recent()
        connChip.text = ConnInfo.label.ifEmpty { "Bağlantı yok" }

        val today = StatsStore.today(a)
        tileToday.value.text = Fmt.bytes(today.mobile + today.wifi)
        tileToday.sub.text = "Mobil ${Fmt.bytes(today.mobile)}\nWi-Fi ${Fmt.bytes(today.wifi)}"

        val rollHours = prefs.getInt(Prefs.ROLL_HOURS, 1)
        tileRoll.title.text = "SON $rollHours SAAT"
        tileRoll.value.text = Fmt.bytes(SpeedService.rollValue)
        tileRoll.sub.text = when (prefs.getInt(Prefs.ROLL_SCOPE, 0)) {
            1 -> "Sadece mobil\nkayan sayaç"
            2 -> "Sadece Wi-Fi\nkayan sayaç"
            else -> "Kayan sayaç\nhiç sıfırlanmaz"
        }

        val hours = prefs.getInt(Prefs.HOURS, 1)
        val mobileOnly = prefs.getBoolean(Prefs.MOBILE_ONLY, false)
        val now = System.currentTimeMillis()
        val start = prefs.getLong(Prefs.WINDOW_START, now)
        val pm = prefs.getLong(Prefs.USED_MOBILE, 0L)
        val pw = prefs.getLong(Prefs.USED_WIFI, 0L)
        val used = if (mobileOnly) pm else pm + pw
        val left = Fmt.remaining(start + hours * 3_600_000L - now)
        tilePeriod.value.text = Fmt.bytes(used)
        val quotaMb = prefs.getInt(Prefs.QUOTA_MB, 0)
        if (quotaMb > 0) {
            val ratio = used.toDouble() / Fmt.mbToBytes(quotaMb)
            quotaBar.visibility = View.VISIBLE
            quotaBar.progress = (ratio * 1000).toInt().coerceIn(0, 1000)
            quotaBar.progressTintList = ColorStateList.valueOf(if (ratio >= 1) 0xFFE53935.toInt() else Ui.ACCENT)
            tilePeriod.sub.text = "Sınır doluluğu: %${(ratio * 100).toInt()}\n$left sonra sıfırlanır"
        } else {
            quotaBar.visibility = View.GONE
            tilePeriod.sub.text = "$hours saatlik${if (mobileOnly) " · mobil" else ""}\n$left sonra sıfırlanır"
        }

        val month = StatsStore.aggregate(a, StatsStore.key().take(7))
        tileMonth.value.text = Fmt.bytes(month.mobile + month.wifi)
        tileMonth.sub.text = "Mobil ${Fmt.bytes(month.mobile)}\nWi-Fi ${Fmt.bytes(month.wifi)}"

        split.set(today.mobile, today.wifi)
        val total = (today.mobile + today.wifi).coerceAtLeast(1)
        splitText.text = "● Mobil ${Fmt.bytes(today.mobile)} (%${today.mobile * 100 / total})    ● Wi-Fi ${Fmt.bytes(today.wifi)} (%${today.wifi * 100 / total})"
        peakText.text = "Bugünün rekoru: mobil ${Fmt.speed(today.peakMobile, bits)} · Wi-Fi ${Fmt.speed(today.peakWifi, bits)}"

        if (ticks % 10 == 0) refreshHealth()
        if (ticks % 30 == 0) loadTopApps()
        ticks++
    }

    /** Göstergenin düzgün çalışmasını engelleyen durumları üstte gösterir. */
    private fun refreshHealth() {
        health.removeAllViews()
        val nm = a.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(SpeedService.CHANNEL_ID)
        if (!nm.areNotificationsEnabled() || channel?.importance == NotificationManager.IMPORTANCE_NONE) {
            health.addView(banner("🔕", "Bildirimler kapalı", "Hız durum çubuğunda görünmüyor.", "Aç") {
                a.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName))
            })
        }
        if (prefs.getBoolean(Prefs.OVERLAY, false) && !Settings.canDrawOverlays(a)) {
            health.addView(banner("🪟", "Yüzen pencere izni yok", "Pencere açık ama gösterme izni verilmemiş.", "İzin ver") {
                a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
            })
        }
        val pm = a.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(a.packageName) && !prefs.getBoolean(Prefs.HEALTH_BATTERY_HIDDEN, false)) {
            health.addView(banner("🔋", "Arka planda kapanabilir", "Samsung pil tasarrufu göstergeyi durdurabilir.", "Muaf tut", dismiss = {
                prefs.edit().putBoolean(Prefs.HEALTH_BATTERY_HIDDEN, true).apply()
                refreshHealth()
            }) {
                @Suppress("BatteryLife")
                a.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${a.packageName}")))
            })
        }
        if (ConnInfo.vpn) {
            health.addView(banner("🛡", "VPN açık", "Android VPN trafiğini iki kez sayar; Wi-Fi kullanımı yaklaşık hesaplanıyor.", null) {})
        }
    }

    private fun banner(icon: String, title: String, text: String, action: String?, dismiss: (() -> Unit)? = null, onAction: () -> Unit): View =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = Ui.rounded(Ui.accentSoft(0x1F), dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
            addView(Ui.text(a, icon, 20f).apply { setPadding(0, 0, dp(12), 0) })
            val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(Ui.text(a, title, 14f).apply {
                typeface = Ui.MEDIUM
                setPadding(0, 0, 0, 0)
            })
            texts.addView(Ui.hint(a, text))
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (dismiss != null) {
                addView(TextView(a).apply {
                    this.text = "Gizle"
                    textSize = 13f
                    setTextColor(Ui.secondary(a))
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    isClickable = true
                    setOnClickListener { dismiss() }
                })
            }
            if (action != null) {
                addView(TextView(a).apply {
                    this.text = action
                    textSize = 13f
                    typeface = Ui.MEDIUM
                    setTextColor(Color.WHITE)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    background = Ui.ripple(Ui.rounded(Ui.ACCENT, dp(50).toFloat()))
                    isClickable = true
                    setOnClickListener { onAction() }
                })
            }
        }

    private fun loadTopApps() {
        if (!NetStats.hasAccess(a)) {
            topApps.removeAllViews()
            topApps.addView(Ui.emptyState(a, "📱", "Uygulama kullanımı kapalı",
                "Hangi uygulamanın ne kadar veri harcadığını görmek için \"Kullanım erişimi\" iznini ver.",
                "İzin ver" to { a.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }))
            return
        }
        Thread {
            val apps = NetStats.queryApps(a, Insights.startOfToday(), System.currentTimeMillis() + 60_000L)
                .filter { it.category != "Sistem" }
                .take(3)
            a.runOnUiThread {
                if (a.isFinishing) return@runOnUiThread
                topApps.removeAllViews()
                if (apps.isEmpty()) {
                    topApps.addView(Ui.emptyState(a, "🌱", "Bugün henüz kayıt yok", "Uygulamalar veri kullandıkça burada görünecek."))
                    return@runOnUiThread
                }
                val max = apps.first().total.coerceAtLeast(1L)
                apps.forEachIndexed { i, app ->
                    val row = appRow(app, max)
                    topApps.addView(row)
                    if (!appsLoadedOnce) Ui.fadeIn(row, i * 60L)
                }
                appsLoadedOnce = true
                topApps.addView(Ui.button(a, "Tüm uygulamalar") { a.select(MainActivity.TAB_APPS) })
            }
        }.start()
    }

    private fun appRow(app: AppUsage, max: Long): View {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
            background = Ui.ripple(Ui.rounded(0, dp(14).toFloat()))
            isClickable = true
            setOnClickListener {
                a.startActivity(Intent(a, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_UID, app.uid))
            }
        }
        row.addView(ImageView(a).apply {
            if (app.icon != null) setImageDrawable(app.icon) else setImageResource(android.R.drawable.sym_def_app_icon)
            contentDescription = app.name
        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(12) })
        val mid = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(Ui.text(a, app.name, 15f).apply {
            typeface = Ui.MEDIUM
            maxLines = 1
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(Ui.text(a, Fmt.bytes(app.total), 14f, bold = true, color = Ui.accentText(a)))
        mid.addView(head)
        mid.addView(PercentBar(a, app.total.toFloat() / max, Ui.ACCENT), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6)
        ).apply { topMargin = dp(2) })
        row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }
}
