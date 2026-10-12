package com.emir.hizgostergesi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Ana ekran: canlı hız kartı, diğer ekranlara geçiş ve tüm ayarlar. */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var heroDown: TextView
    private lateinit var heroUp: TextView
    private lateinit var heroUsage: TextView

    private lateinit var usageText: TextView
    private lateinit var quotaBar: ProgressBar
    private lateinit var quotaText: TextView

    private lateinit var preview: TextView
    private lateinit var sbPreview: ImageView
    private lateinit var overlaySwitch: Switch
    private val swatches = mutableListOf<Pair<View, Int>>()
    private val handler = Handler(Looper.getMainLooper())

    private val colors = listOf(
        "Beyaz" to 0xFFFFFFFF, "Yeşil" to 0xFF4CAF50, "Camgöbeği" to 0xFF00BCD4,
        "Mavi" to 0xFF2196F3, "Mor" to 0xFF9C27B0, "Pembe" to 0xFFE91E63,
        "Kırmızı" to 0xFFF44336, "Turuncu" to 0xFFFF9800, "Sarı" to 0xFFFFEB3B,
        "Siyah" to 0xFF000000
    ).map { it.first to it.second.toInt() }

    private val refresh = object : Runnable {
        override fun run() {
            refreshLive()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        prefs = Prefs.get(this)
        setContentView(buildUi())

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startForegroundService(Intent(this, SpeedService::class.java))
    }

    override fun onResume() {
        super.onResume()
        // İzin ekranından dönüldüyse anahtarı gerçek duruma göre ayarla
        overlaySwitch.isChecked = prefs.getBoolean(Prefs.OVERLAY, false) && Settings.canDrawOverlays(this)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun buildUi(): View {
        val (scroll, root) = Ui.page(this, edgeToEdge = true)

        root.addView(heroCard())
        root.addView(navRow())

        // ----- Kullanım sayacı -----
        root.addView(Ui.card(this, "Kullanım sayacı").apply {
            usageText = Ui.text(this@MainActivity, "", 15f)
            addView(usageText)

            quotaBar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progressTintList = ColorStateList.valueOf(Ui.ACCENT)
            }
            addView(quotaBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)).apply {
                topMargin = dp(6)
            })
            quotaText = Ui.hint(this@MainActivity, "")
            addView(quotaText)

            addView(Ui.label(this@MainActivity, "NEYİ SAYSIN?"))
            addView(Ui.choiceRow(
                this@MainActivity, listOf("Tümü" to false, "Sadece mobil veri" to true),
                prefs.getBoolean(Prefs.MOBILE_ONLY, false)
            ) {
                prefs.edit().putBoolean(Prefs.MOBILE_ONLY, it).apply()
                refreshLive()
            })
            addView(Ui.label(this@MainActivity, "KAÇ SAATTE BİR SIFIRLANSIN?"))
            addView(Ui.choiceRow(this@MainActivity, Prefs.HOUR_OPTIONS.map { "$it sa" to it }, prefs.getInt(Prefs.HOURS, 1)) {
                prefs.edit().putInt(Prefs.HOURS, it).apply()
                refreshLive()
            })
            addView(Ui.button(this@MainActivity, "Sayacı şimdi sıfırla") {
                startForegroundService(
                    Intent(this@MainActivity, SpeedService::class.java).setAction(SpeedService.ACTION_RESET)
                )
                toast("Sayaç sıfırlandı")
            })
        })

        // ----- Veri sınırı -----
        root.addView(Ui.card(this, "Veri sınırı uyarısı").apply {
            addView(Ui.hint(this@MainActivity, "Dönem içinde bu sınır aşılınca bildirim gelir. \"Sadece mobil veri\" seçiliyse yalnızca mobil veri sayılır."))
            addView(Ui.choiceRow(
                this@MainActivity,
                listOf("Kapalı" to 0, "250 MB" to 250, "500 MB" to 500, "1 GB" to 1024, "2 GB" to 2048, "5 GB" to 5120, "10 GB" to 10240),
                prefs.getInt(Prefs.QUOTA_MB, 0)
            ) {
                prefs.edit().putInt(Prefs.QUOTA_MB, it).putLong(Prefs.QUOTA_WARNED, 0L).apply()
                refreshLive()
            })
        })

        // ----- Görünüm -----
        root.addView(Ui.card(this, "Görünüm").apply {
            addView(Ui.label(this@MainActivity, "HIZ BİRİMİ"))
            addView(Ui.choiceRow(this@MainActivity, listOf("MB/s" to false, "Mbps" to true), prefs.getBoolean(Prefs.UNIT_BITS, false)) {
                prefs.edit().putBoolean(Prefs.UNIT_BITS, it).apply()
                updatePreview()
                refreshLive()
            })
            addView(Ui.label(this@MainActivity, "RENK · BİLDİRİM PANELİ, YÜZEN PENCERE, WIDGET"))

            preview = TextView(this@MainActivity).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat() }
            }
            addView(preview, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(6) })

            colors.chunked(5).forEach { row ->
                val line = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
                row.forEach { (name, col) ->
                    val swatch = View(this@MainActivity).apply {
                        contentDescription = name
                        setOnClickListener { setColor(col) }
                    }
                    line.addView(swatch, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                        setMargins(dp(4), dp(4), dp(8), dp(4))
                    })
                    swatches += swatch to col
                }
                addView(line)
            }

            val hexRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val hexInput = EditText(this@MainActivity).apply {
                hint = "Özel renk, ör. #FF9800"
                isSingleLine = true
                textSize = 15f
                setTextColor(Ui.primary(this@MainActivity))
                setHintTextColor(Ui.secondary(this@MainActivity))
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = Ui.rounded(Color.TRANSPARENT, dp(50).toFloat(), dp(1), Ui.outline(this@MainActivity))
            }
            hexRow.addView(hexInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
                topMargin = dp(10)
            })
            hexRow.addView(Ui.button(this@MainActivity, "Uygula") {
                val raw = hexInput.text.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                try {
                    setColor(Color.parseColor(raw))
                } catch (e: IllegalArgumentException) {
                    toast("Geçersiz renk kodu")
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
            addView(hexRow)
        })

        // ----- Durum çubuğu -----
        root.addView(Ui.card(this, "Durum çubuğu").apply {
            addView(Ui.hint(this@MainActivity, "Oradaki rengi Android belirler; içeriği ve boyutu buradan ayarlanır."))
            sbPreview = ImageView(this@MainActivity).apply {
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = Ui.rounded(0xFF000000.toInt(), dp(14).toFloat())
            }
            addView(sbPreview, LinearLayout.LayoutParams(dp(68), dp(68)).apply {
                topMargin = dp(8)
                bottomMargin = dp(2)
            })
            addView(Ui.label(this@MainActivity, "NE GÖSTERİLSİN?"))
            addView(Ui.choiceRow(
                this@MainActivity,
                listOf("İndirme" to StatusIcon.MODE_DOWN, "Yükleme" to StatusIcon.MODE_UP,
                    "Toplam" to StatusIcon.MODE_TOTAL, "İkisi alt alta" to StatusIcon.MODE_BOTH),
                prefs.getInt(Prefs.SB_MODE, StatusIcon.MODE_DOWN)
            ) {
                prefs.edit().putInt(Prefs.SB_MODE, it).apply()
                updateSbPreview()
            })
            addView(Ui.label(this@MainActivity, "BOYUT"))
            addView(Ui.choiceRow(this@MainActivity, listOf("Normal" to 0, "Büyük" to 1, "En büyük" to 2), prefs.getInt(Prefs.SB_SIZE, 1)) {
                prefs.edit().putInt(Prefs.SB_SIZE, it).apply()
                updateSbPreview()
            })
            addView(Ui.label(this@MainActivity, "BİRİM"))
            addView(Ui.choiceRow(
                this@MainActivity,
                listOf("Tam (KB/s)" to StatusIcon.UNIT_FULL, "Kısa (4,2M)" to StatusIcon.UNIT_SHORT, "Gizli" to StatusIcon.UNIT_HIDDEN),
                prefs.getInt(Prefs.SB_UNIT, StatusIcon.UNIT_FULL)
            ) {
                prefs.edit().putInt(Prefs.SB_UNIT, it).apply()
                updateSbPreview()
            })
            addView(Ui.switch(this@MainActivity, "Kalın yazı", prefs.getBoolean(Prefs.SB_BOLD, true)) {
                prefs.edit().putBoolean(Prefs.SB_BOLD, it).apply()
                updateSbPreview()
            })
            addView(Ui.switch(this@MainActivity, "Boştayken gizle", prefs.getBoolean(Prefs.SB_HIDE_IDLE, false)) {
                prefs.edit().putBoolean(Prefs.SB_HIDE_IDLE, it).apply()
            })
            addView(Ui.hint(this@MainActivity, "\"İkisi alt alta\"da üstteki indirme, alttaki yükleme. \"Boştayken gizle\" açıkken veri akmıyorsa simge boş kalır."))
        })

        // ----- Yüzen pencere -----
        root.addView(Ui.card(this, "Yüzen pencere").apply {
            overlaySwitch = Ui.switch(this@MainActivity, "Ekranda yüzen hız penceresi", prefs.getBoolean(Prefs.OVERLAY, false)) { checked ->
                prefs.edit().putBoolean(Prefs.OVERLAY, checked).apply()
                if (checked && !Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(
                        this@MainActivity,
                        "Hız Göstergesi'ne \"Diğer uygulamaların üzerinde göster\" izni ver, sonra geri dön",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
            addView(overlaySwitch)
            addView(Ui.seekRow(this@MainActivity, "Yazı boyutu", 9, 24, prefs.getInt(Prefs.OVERLAY_SIZE, 12), "") {
                prefs.edit().putInt(Prefs.OVERLAY_SIZE, it).apply()
                updatePreview()
            })
            addView(Ui.seekRow(this@MainActivity, "Arka plan koyuluğu", 0, 100, prefs.getInt(Prefs.OVERLAY_ALPHA, 60), "%") {
                prefs.edit().putInt(Prefs.OVERLAY_ALPHA, it).apply()
                updatePreview()
            })
            addView(Ui.hint(this@MainActivity, "Parmağınla sürükleyip taşı. Çift dokununca gizlenir; bildirimdeki \"Pencereyi göster\" ile geri açılır."))
        })

        // ----- Pil -----
        root.addView(Ui.card(this, "Pil tasarrufu").apply {
            addView(Ui.label(this@MainActivity, "GÜNCELLEME ARALIĞI"))
            addView(Ui.choiceRow(this@MainActivity, listOf("1 sn" to 1, "2 sn" to 2, "5 sn" to 5), prefs.getInt(Prefs.INTERVAL_SEC, 1)) {
                prefs.edit().putInt(Prefs.INTERVAL_SEC, it).apply()
            })
            addView(Ui.switch(this@MainActivity, "Ekran kapalıyken yavaşlat (önerilen)", prefs.getBoolean(Prefs.SLOW_WHEN_OFF, true)) {
                prefs.edit().putBoolean(Prefs.SLOW_WHEN_OFF, it).apply()
            })
            addView(Ui.hint(this@MainActivity, "Aralık uzadıkça pil daha az harcanır. Ekran kapalıyken sayaçlar yine doğru tutulur, sadece göstergeler çizilmez."))
        })

        // ----- Widget -----
        root.addView(Ui.card(this, "Ana ekran widget'ı").apply {
            addView(Ui.hint(this@MainActivity, "Ana ekranda boş bir yere basılı tut → Widget'lar → Hız Göstergesi'ni sürükle."))
        })

        root.addView(Ui.hint(this, "🔒 İnternet izni yok · Tüm veriler sadece bu telefonda kalır").apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })

        setColor(prefs.getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR), save = false)
        updatePreview()
        refreshLive()
        return scroll
    }

    /** Üstteki renkli kart: canlı hız ve dönem kullanımı. */
    private fun heroCard(): View {
        val pad = dp(20)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(Ui.ACCENT, Ui.ACCENT_2)
            ).apply { cornerRadius = dp(24).toFloat() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }

            addView(Ui.text(this@MainActivity, "Hız Göstergesi", 15f, bold = true, color = 0xD9FFFFFF.toInt()))
            heroDown = Ui.text(this@MainActivity, "↓ —", 36f, bold = true, topDp = 6, color = Color.WHITE)
            heroUp = Ui.text(this@MainActivity, "↑ —", 18f, bold = true, color = 0xE6FFFFFF.toInt())
            heroUsage = Ui.text(this@MainActivity, "", 13f, topDp = 8, color = 0xD9FFFFFF.toInt())
            addView(heroDown)
            addView(heroUp)
            addView(heroUsage)
        }
    }

    /** İstatistik ve uygulamalar için yan yana iki kutu. */
    private fun navRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        row.addView(navTile("📊", "İstatistik", "Günlük · aylık · yıllık") {
            startActivity(Intent(this, HistoryActivity::class.java))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        row.addView(navTile("📱", "Uygulamalar", "Kim ne kadar harcadı") {
            startActivity(Intent(this, AppsActivity::class.java))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        return row
    }

    private fun navTile(emoji: String, title: String, sub: String, onClick: () -> Unit): View {
        val pad = dp(16)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = Ui.ripple(Ui.rounded(Ui.surface(this@MainActivity), dp(20).toFloat()))
            isClickable = true
            setOnClickListener { onClick() }
            addView(Ui.text(this@MainActivity, emoji, 26f))
            addView(Ui.text(this@MainActivity, title, 16f, bold = true, topDp = 4))
            addView(Ui.hint(this@MainActivity, sub))
        }
    }

    private fun updateSbPreview() {
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        sbPreview.setImageBitmap(StatusIcon.draw(prefs, 4_404_019, 122_880, bits, ignoreIdle = true))
    }

    private fun updatePreview() {
        updateSbPreview()
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        val alpha = prefs.getInt(Prefs.OVERLAY_ALPHA, 60)
        preview.text = "↓ ${Fmt.speed(4_404_019, bits)}  ↑ ${Fmt.speed(122_880, bits)}"
        preview.textSize = prefs.getInt(Prefs.OVERLAY_SIZE, 12).toFloat()
        (preview.background as? GradientDrawable)?.setColor(Color.argb(alpha * 255 / 100, 0, 0, 0))
    }

    private fun setColor(color: Int, save: Boolean = true) {
        if (save) prefs.edit().putInt(Prefs.COLOR, color).apply()
        preview.setTextColor(color)
        swatches.forEach { (view, col) ->
            val selected = col == color
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(col)
                setStroke(if (selected) dp(4) else dp(1), if (selected) Ui.ACCENT else Ui.outline(this@MainActivity))
            }
        }
    }

    /** Canlı hız kartını, sayaç metnini ve sınır çubuğunu yeniler. */
    private fun refreshLive() {
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        heroDown.text = "↓ ${Fmt.speed(SpeedService.lastDown, bits)}"
        heroUp.text = "↑ ${Fmt.speed(SpeedService.lastUp, bits)}"

        val hours = prefs.getInt(Prefs.HOURS, 1)
        val mobileOnly = prefs.getBoolean(Prefs.MOBILE_ONLY, false)
        val now = System.currentTimeMillis()
        val start = prefs.getLong(Prefs.WINDOW_START, now)
        val mobile = prefs.getLong(Prefs.USED_MOBILE, 0L)
        val wifi = prefs.getLong(Prefs.USED_WIFI, 0L)
        val left = Fmt.remaining(start + hours * 3_600_000L - now)
        val used = if (mobileOnly) mobile else mobile + wifi

        heroUsage.text = "Son $hours saat: ${Fmt.bytes(used)} ${if (mobileOnly) "(mobil)" else ""} • $left sonra sıfırlanır"
        usageText.text = "Bu dönem: ${Fmt.bytes(used)}\nMobil ${Fmt.bytes(mobile)}  •  Wi-Fi ${Fmt.bytes(wifi)}\n$left sonra sıfırlanır"

        val quotaMb = prefs.getInt(Prefs.QUOTA_MB, 0)
        if (quotaMb > 0) {
            val ratio = used.toDouble() / (quotaMb * 1048576.0)
            quotaBar.visibility = View.VISIBLE
            quotaText.visibility = View.VISIBLE
            quotaBar.progress = (ratio * 1000).toInt().coerceIn(0, 1000)
            quotaBar.progressTintList = ColorStateList.valueOf(if (ratio >= 1) 0xFFF44336.toInt() else Ui.ACCENT)
            quotaText.text = "Sınırın %%%d'i doldu (${Fmt.mb(quotaMb)})".format((ratio * 100).toInt())
        } else {
            quotaBar.visibility = View.GONE
            quotaText.visibility = View.GONE
        }
    }
}
