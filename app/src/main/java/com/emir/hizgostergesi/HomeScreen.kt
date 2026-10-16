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
    private lateinit var gauge: SegmentGauge
    private lateinit var bigNum: TextView
    private lateinit var bigUnit: TextView
    private lateinit var upText: TextView
    private lateinit var sigText: TextView
    private lateinit var sigSub: TextView
    private lateinit var peakNum: TextView
    private var shownDown = 0L
    private var numAnim: android.animation.ValueAnimator? = null
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
    private lateinit var planBox: LinearLayout

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
        titles.addView(Ui.label(a, "Hız Göstergesi").apply { setPadding(0, 0, 0, 0) })
        titles.addView(Ui.hint(a, SimpleDateFormat("d MMMM EEEE", Locale("tr")).format(Date())).apply { setPadding(0, dp(2), 0, 0) })
        head.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        connChip = Ui.mono(TextView(a).apply {
            textSize = 12f
            setTextColor(Ui.accentText(a))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = Ui.rounded(0, dp(12).toFloat(), dp(1), Ui.outline(a))
        })
        head.addView(connChip)
        root.addView(head)

        health = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        root.addView(health)

        root.addView(heroCard())

        planBox = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        root.addView(planBox)

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

    /** Kokpit göstergesi: dev rakam, segment çubuk, yükleme / sinyal / rekor ve son 60 saniye. */
    private fun heroCard(): View = Ui.card(a).apply {
        setPadding(dp(18), dp(16), dp(18), dp(12))
        addView(Ui.label(a, "İndirme").apply { setPadding(0, 0, 0, dp(2)) })
        val numRow = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        bigNum = Ui.mono(TextView(a).apply {
            text = "0"
            textSize = 60f
            setTextColor(Ui.primary(a))
            letterSpacing = -0.08f
            includeFontPadding = false
        })
        bigUnit = Ui.mono(TextView(a).apply {
            text = "KB/s"
            textSize = 16f
            setTextColor(Ui.accentText(a))
            setPadding(dp(8), 0, 0, dp(8))
        })
        numRow.addView(bigNum)
        numRow.addView(bigUnit)
        addView(numRow)
        gauge = SegmentGauge(a)
        addView(gauge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        // Üç sütun: yükleme, sinyal, rekor (aralarında ince çizgiler)
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(10))
        }
        fun column(title: String, gravityEnd: Boolean = false): Pair<TextView, TextView> {
            val col = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = if (gravityEnd) Gravity.END else Gravity.START
            }
            col.addView(Ui.label(a, title).apply { setPadding(0, 0, 0, dp(2)) })
            val v = Ui.mono(TextView(a).apply {
                textSize = 16f
                setTextColor(Ui.primary(a))
            })
            val sub = Ui.mono(TextView(a).apply {
                textSize = 11f
                setTextColor(Themes.LABEL)
            }, 0)
            col.addView(v)
            col.addView(sub)
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            return v to sub
        }
        upText = column("Yükleme").first
        val sig = column("Sinyal")
        sigText = sig.first
        sigSub = sig.second
        peakNum = column("Rekor", gravityEnd = true).first
        addView(Ui.divider(a))
        addView(row)
        addView(Ui.divider(a))
        spark = Sparkline(a).apply { lineColor = Ui.ACCENT }
        addView(spark, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(10) })
        addView(Ui.label(a, "Son 60 saniye").apply {
            gravity = Gravity.END
            setPadding(0, dp(4), 0, 0)
        })
    }

    /** Büyük rakamı sayarak günceller. */
    private fun showDown(bps: Long, bits: Boolean) {
        val from = shownDown
        numAnim?.cancel()
        numAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                val v = (from + (bps - from) * (it.animatedValue as Float)).toLong()
                shownDown = v
                val (num, unit) = Fmt.speedParts(v, bits)
                bigNum.text = num
                bigUnit.text = unit
            }
            start()
        }
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
        showDown(SpeedService.lastDown, bits)
        upText.text = Fmt.speed(SpeedService.lastUp, bits)
        spark.data = SpeedService.recent()
        connChip.text = "● " + ConnInfo.label.ifEmpty { "Bağlantı yok" }
        val (dbm, level) = ConnInfo.signal()
        sigText.text = if (dbm != null) "$dbm dBm" else "—"
        sigSub.text = if (level >= 0) "▮".repeat(level) + "▯".repeat(4 - level.coerceAtMost(4)) else ""

        val today = StatsStore.today(a)
        peakNum.text = Fmt.speed(maxOf(today.peakMobile, today.peakWifi), bits)
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
            quotaBar.progressTintList = ColorStateList.valueOf(if (ratio >= 1) 0xFFFF6B6B.toInt() else Ui.ACCENT)
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
        if (ticks % 30 == 0) {
            loadTopApps()
            loadPlan()
        }
        ticks++
    }

    private fun loadPlan() {
        if (!PlanState.enabled(a)) {
            renderPlan(null)
            return
        }
        PlanState.last?.let { renderPlan(it) }
        Thread {
            val s = try { PlanState.compute(a) } catch (e: Exception) { null }
            a.runOnUiThread { if (!a.isFinishing) renderPlan(s) }
        }.start()
    }

    private fun renderPlan(s: PlanState.Snapshot?) {
        planBox.removeAllViews()
        val open = { a.startActivity(Intent(a, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, SettingsActivity.CAT_PLAN)) }
        if (s == null) {
            if (prefs.getBoolean(Prefs.PLAN_PROMO_HIDDEN, false)) return
            planBox.addView(banner("📦", "Paketini takip et", "Kalan GB'ı, günlük payını ve paketin yetip yetmeyeceğini gör.", "Ekle", dismiss = {
                prefs.edit().putBoolean(Prefs.PLAN_PROMO_HIDDEN, true).apply()
                renderPlan(null)
            }) { open() })
            return
        }
        val now = System.currentTimeMillis()
        val days = PlanMath.daysLeft(now, s.end)
        val over = s.used >= s.limit
        val card = Ui.card(a, "Paketim", "📦").apply {
            isClickable = true
            Ui.pressable(this, 0.98f)
            setOnClickListener { open() }
        }
        val barColor = when {
            s.ratio >= 1 -> 0xFFE5484D.toInt()
            s.ratio >= 0.8 -> 0xFFFF9F43.toInt()
            else -> Ui.ACCENT
        }
        // Büyük kalan miktar + gün
        val top = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        top.addView(Ui.mono(TextView(a).apply {
            text = if (over) "+" + Fmt.bytes(s.used - s.limit) else Fmt.bytes(s.remaining)
            textSize = 30f
            includeFontPadding = false
            setTextColor(if (over) barColor else Ui.primary(a))
        }))
        top.addView(Ui.label(a, if (over) "aşıldı" else "kaldı").apply { setPadding(dp(8), 0, 0, dp(4)) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Ui.mono(TextView(a).apply {
            text = "$days gün"
            textSize = 15f
            setTextColor(Ui.accentText(a))
            setPadding(0, 0, 0, dp(2))
        }))
        card.addView(top)
        card.addView(SegmentBar(a, s.ratio.toFloat(), barColor), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)).apply {
            topMargin = dp(10)
            bottomMargin = dp(8)
        })
        // Kullanılan / paket / günlük pay: üç sütun
        val facts = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        fun fact(label: String, value: String, end: Boolean = false) {
            facts.addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = if (end) Gravity.END else Gravity.START
                addView(Ui.label(a, label).apply { setPadding(0, dp(4), 0, dp(2)) })
                addView(Ui.mono(TextView(a).apply {
                    text = value
                    textSize = 14f
                    setTextColor(Ui.primary(a))
                }))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        fact("Kullanılan", "%${(s.ratio * 100).toInt().coerceAtMost(999)}")
        fact("Paket", Fmt.bytes(s.limit))
        fact("Günlük pay", if (over) "—" else Fmt.bytes(PlanMath.dailyAllowance(s.remaining, now, s.end)), end = true)
        card.addView(facts, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })
        val forecast = PlanMath.forecast(s.used, s.start, now, s.end)
        val early = PlanMath.daysEarly(s.used, s.limit, s.start, now, s.end)
        card.addView(Ui.hint(a, when {
            over -> "Yenilenmeye $days gün var."
            early != null && early > 0 -> "⚠ Bu tempoyla paket yenilenmeden yaklaşık $early gün önce biter (dönem sonu tahmini ~${Fmt.bytes(forecast)})."
            early != null -> "⚠ Bu tempoyla paket dönemin son gününe yetişmeyebilir."
            else -> "✓ Bu tempoyla paket yeter (dönem sonu tahmini ~${Fmt.bytes(forecast)})."
        }))
        if (s.night > 0 || prefs.getBoolean(Prefs.PLAN_NIGHT, false)) {
            card.addView(Ui.hint(a, "🌙 Gece paketi saatlerinde ${Fmt.bytes(s.night)} kullanıldı (paketten düşülmedi)."))
        }
        if (s.hotspot > 0) card.addView(Ui.hint(a, "Hotspot ile paylaşılan: ${Fmt.bytes(s.hotspot)} (pakete dahil)."))
        if (s.extra > 0) card.addView(Ui.hint(a, "Bu dönem ${Fmt.bytes(s.extra)} ek paket eklendi."))
        if (s.calibrated) card.addView(Ui.hint(a, "Operatörün söylediği kalan miktara göre eşitlendi."))
        if (!s.fromSystem) card.addView(Ui.hint(a, "Not: \"Kullanım erişimi\" izni olmadığı için sadece uygulama kurulduktan sonraki kullanım sayılıyor."))
        card.addView(LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.button(a, "Operatörle eşitle") { open() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                topMargin = dp(10)
                rightMargin = dp(8)
            })
            addView(Ui.button(a, "Ek paket") { open() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { topMargin = dp(10) })
        })
        planBox.addView(card)
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
            health.addView(banner("🪟", "Yüzen pencere izni yok", "", "İzin ver", compact = true) {
                a.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}")))
            })
        }
        val pm = a.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(a.packageName) && !prefs.getBoolean(Prefs.HEALTH_BATTERY_HIDDEN, false)) {
            health.addView(banner("🔋", "Pil tasarrufu göstergeyi durdurabilir", "", "Muaf tut", compact = true, dismiss = {
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

    /** Uyarı/öneri şeridi. compact: tek satırlık küçük rozet (sağlık uyarıları için). */
    private fun banner(icon: String, title: String, text: String, action: String?, dismiss: (() -> Unit)? = null,
                       compact: Boolean = false, onAction: () -> Unit): View =
        LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (compact) setPadding(dp(12), dp(4), dp(4), dp(4)) else setPadding(dp(14), dp(12), dp(10), dp(12))
            background = Ui.rounded(Ui.accentSoft(0x1F), dp(20).toFloat())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
            addView(TextView(a).apply {
                textSize = if (compact) 18f else 22f
                Icons.apply(this, icon, Ui.accentText(a))
                setPadding(0, 0, dp(if (compact) 10 else 12), 0)
            })
            val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(Ui.text(a, title, if (compact) 13f else 14f).apply {
                typeface = Ui.MEDIUM
                setPadding(0, 0, 0, 0)
                if (compact) maxLines = 2
            })
            if (text.isNotEmpty()) texts.addView(Ui.hint(a, text))
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (dismiss != null) {
                addView(TextView(a).apply {
                    this.text = if (compact) "✕" else "Gizle"
                    contentDescription = "Gizle"
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
                    setTextColor(Ui.ON_ACCENT)
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
