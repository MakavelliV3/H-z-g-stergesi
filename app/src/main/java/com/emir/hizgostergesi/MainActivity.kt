package com.emir.hizgostergesi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Ana ekran: ayarlar ve diğer ekranlara geçiş. */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var usageText: TextView
    private lateinit var preview: TextView
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
            refreshUsage()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun buildUi(): View {
        val (scroll, root) = Ui.page(this)

        root.addView(Ui.text(this, "Hız Göstergesi", 26f, bold = true))
        root.addView(button("📊  Günlük istatistik ve geçmiş") {
            startActivity(Intent(this, HistoryActivity::class.java))
        })
        root.addView(button("📱  Uygulama bazlı kullanım") {
            startActivity(Intent(this, AppsActivity::class.java))
        })

        // ----- Kullanım sayacı -----
        root.addView(Ui.header(this, "Kullanım sayacı"))
        usageText = Ui.text(this, "", 16f)
        root.addView(usageText)
        root.addView(Ui.text(this, "Neyi saysın?", 14f, topDp = 10))
        root.addView(Ui.choiceRow(
            this, listOf("Tümü" to false, "Sadece mobil veri" to true),
            prefs.getBoolean(Prefs.MOBILE_ONLY, false)
        ) {
            prefs.edit().putBoolean(Prefs.MOBILE_ONLY, it).apply()
            refreshUsage()
        })
        root.addView(Ui.text(this, "Kaç saatte bir sıfırlansın?", 14f, topDp = 10))
        root.addView(Ui.choiceRow(this, Prefs.HOUR_OPTIONS.map { "$it sa" to it }, prefs.getInt(Prefs.HOURS, 1)) {
            prefs.edit().putInt(Prefs.HOURS, it).apply()
            refreshUsage()
        })
        root.addView(button("Sayacı şimdi sıfırla") {
            startForegroundService(Intent(this, SpeedService::class.java).setAction(SpeedService.ACTION_RESET))
            toast("Sayaç sıfırlandı")
        })

        // ----- Veri sınırı -----
        root.addView(Ui.header(this, "Veri sınırı uyarısı"))
        root.addView(Ui.hint(this, "Dönem içinde bu sınır aşılınca bildirim gelir. \"Sadece mobil veri\" seçiliyse yalnızca mobil veri sayılır."))
        root.addView(Ui.choiceRow(
            this,
            listOf("Kapalı" to 0, "250 MB" to 250, "500 MB" to 500, "1 GB" to 1024, "2 GB" to 2048, "5 GB" to 5120, "10 GB" to 10240),
            prefs.getInt(Prefs.QUOTA_MB, 0)
        ) {
            prefs.edit().putInt(Prefs.QUOTA_MB, it).putLong(Prefs.QUOTA_WARNED, 0L).apply()
        })

        // ----- Görünüm -----
        root.addView(Ui.header(this, "Görünüm"))
        root.addView(Ui.text(this, "Hız birimi", 14f))
        root.addView(Ui.choiceRow(this, listOf("MB/s" to false, "Mbps" to true), prefs.getBoolean(Prefs.UNIT_BITS, false)) {
            prefs.edit().putBoolean(Prefs.UNIT_BITS, it).apply()
            updatePreview()
        })
        root.addView(Ui.text(this, "Renk (bildirim paneli, yüzen pencere ve widget)", 14f, topDp = 10))

        preview = TextView(this).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat() }
        }
        root.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8); bottomMargin = dp(8) })

        colors.chunked(5).forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (name, col) ->
                val swatch = View(this).apply {
                    contentDescription = name
                    setOnClickListener { setColor(col) }
                }
                line.addView(swatch, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
                swatches += swatch to col
            }
            root.addView(line)
        }

        val hexRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hexInput = EditText(this).apply {
            hint = "Özel renk, ör. #FF9800"
            isSingleLine = true
        }
        hexRow.addView(hexInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        hexRow.addView(button("Uygula") {
            val raw = hexInput.text.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
            try {
                setColor(Color.parseColor(raw))
            } catch (e: IllegalArgumentException) {
                toast("Geçersiz renk kodu")
            }
        })
        root.addView(hexRow)

        // ----- Yüzen pencere -----
        root.addView(Ui.header(this, "Yüzen pencere"))
        overlaySwitch = Switch(this).apply {
            text = "Ekranda yüzen hız penceresi göster"
            textSize = 16f
            setOnCheckedChangeListener { _, checked ->
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
        }
        root.addView(overlaySwitch)
        root.addView(Ui.seekRow(this, "Yazı boyutu", 9, 24, prefs.getInt(Prefs.OVERLAY_SIZE, 12), "") {
            prefs.edit().putInt(Prefs.OVERLAY_SIZE, it).apply()
            updatePreview()
        })
        root.addView(Ui.seekRow(this, "Arka plan koyuluğu", 0, 100, prefs.getInt(Prefs.OVERLAY_ALPHA, 60), "%") {
            prefs.edit().putInt(Prefs.OVERLAY_ALPHA, it).apply()
            updatePreview()
        })
        root.addView(Ui.hint(this, "Pencereyi parmağınla sürükleyip taşı. Çift dokununca gizlenir; bildirimdeki \"Pencereyi göster\" düğmesiyle geri açılır."))

        // ----- Pil -----
        root.addView(Ui.header(this, "Pil tasarrufu"))
        root.addView(Ui.text(this, "Güncelleme aralığı", 14f))
        root.addView(Ui.choiceRow(this, listOf("1 sn" to 1, "2 sn" to 2, "5 sn" to 5), prefs.getInt(Prefs.INTERVAL_SEC, 1)) {
            prefs.edit().putInt(Prefs.INTERVAL_SEC, it).apply()
        })
        root.addView(Switch(this).apply {
            text = "Ekran kapalıyken yavaşlat (önerilen)"
            textSize = 16f
            isChecked = prefs.getBoolean(Prefs.SLOW_WHEN_OFF, true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(Prefs.SLOW_WHEN_OFF, checked).apply() }
        })
        root.addView(Ui.hint(this, "Aralık uzadıkça pil daha az harcanır. Ekran kapalıyken sayaçlar yine doğru tutulur, sadece göstergeler çizilmez."))

        // ----- Widget -----
        root.addView(Ui.header(this, "Ana ekran widget'ı"))
        root.addView(Ui.hint(this, "Ana ekranda boş bir yere basılı tut → Widget'lar → Hız Göstergesi'ni ana ekrana sürükle."))

        setColor(prefs.getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR), save = false)
        updatePreview()
        refreshUsage()
        return scroll
    }

    private fun updatePreview() {
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
                setStroke(if (selected) dp(4) else dp(1), if (selected) 0xFF9E9E9E.toInt() else 0x66888888)
            }
        }
    }

    private fun refreshUsage() {
        val hours = prefs.getInt(Prefs.HOURS, 1)
        val mobileOnly = prefs.getBoolean(Prefs.MOBILE_ONLY, false)
        val now = System.currentTimeMillis()
        val start = prefs.getLong(Prefs.WINDOW_START, now)
        val mobile = prefs.getLong(Prefs.USED_MOBILE, 0L)
        val wifi = prefs.getLong(Prefs.USED_WIFI, 0L)
        val left = start + hours * 3_600_000L - now
        val total = if (mobileOnly) "Mobil: ${Fmt.bytes(mobile)}" else "Toplam: ${Fmt.bytes(mobile + wifi)}"
        usageText.text = "Bu $hours saatlik dönem — $total\n" +
            "(Mobil ${Fmt.bytes(mobile)} • Wi-Fi ${Fmt.bytes(wifi)})\n" +
            "${Fmt.remaining(left)} sonra sıfırlanır"
    }
}
