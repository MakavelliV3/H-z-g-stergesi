package com.emir.hizgostergesi

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Tüm ayarlar, konularına göre kartlarda. */
class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var preview: TextView
    private lateinit var sbPreview: ImageView
    private lateinit var overlaySwitch: Switch
    private val swatches = mutableListOf<Pair<View, Int>>()

    private val colors = listOf(
        "Beyaz" to 0xFFFFFFFF, "Yeşil" to 0xFF4CAF50, "Camgöbeği" to 0xFF00BCD4,
        "Mavi" to 0xFF2196F3, "Mor" to 0xFF9C27B0, "Pembe" to 0xFFE91E63,
        "Kırmızı" to 0xFFF44336, "Turuncu" to 0xFFFF9800, "Sarı" to 0xFFFFEB3B,
        "Siyah" to 0xFF000000
    ).map { it.first to it.second.toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        overlaySwitch.isChecked = prefs.getBoolean(Prefs.OVERLAY, false) && Settings.canDrawOverlays(this)
    }

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun put(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    private fun put(key: String, v: Int) = prefs.edit().putInt(key, v).apply()

    private fun buildUi(): View {
        val (scroll, root) = Ui.page(this)
        val ctx = this
        root.addView(Ui.topBar(this, "Ayarlar"))

        // ----- Kullanım sayacı -----
        root.addView(Ui.card(ctx, "Kullanım sayacı", "⏱").apply {
            addView(Ui.label(ctx, "Neyi saysın?"))
            addView(Ui.choiceRow(ctx, listOf("Tümü" to false, "Sadece mobil veri" to true), prefs.getBoolean(Prefs.MOBILE_ONLY, false)) {
                put(Prefs.MOBILE_ONLY, it)
            })
            addView(Ui.label(ctx, "Kaç saatte bir sıfırlansın?"))
            addView(Ui.choiceRow(ctx, Prefs.HOUR_OPTIONS.map { "$it sa" to it }, prefs.getInt(Prefs.HOURS, 1)) {
                put(Prefs.HOURS, it)
            })
            addView(Ui.button(ctx, "Sayacı şimdi sıfırla") {
                startForegroundService(Intent(ctx, SpeedService::class.java).setAction(SpeedService.ACTION_RESET))
                toast("Sayaç sıfırlandı")
            })
        })

        // ----- Veri sınırı -----
        root.addView(Ui.card(ctx, "Veri sınırı uyarısı", "🚨").apply {
            addView(Ui.hint(ctx, "Dönem içinde bu sınır aşılınca bildirim gelir. \"Sadece mobil veri\" seçiliyse yalnızca mobil veri sayılır."))
            addView(Ui.choiceRow(
                ctx,
                listOf("Kapalı" to 0, "250 MB" to 250, "500 MB" to 500, "1 GB" to 1024, "2 GB" to 2048, "5 GB" to 5120, "10 GB" to 10240),
                prefs.getInt(Prefs.QUOTA_MB, 0)
            ) {
                prefs.edit().putInt(Prefs.QUOTA_MB, it).putLong(Prefs.QUOTA_WARNED, 0L).apply()
            })
        })

        // ----- Görünüm -----
        root.addView(Ui.card(ctx, "Görünüm", "🎨").apply {
            if (Build.VERSION.SDK_INT >= 31) {
                addView(Ui.switch(ctx, "Material You (duvar kâğıdı renkleri)", prefs.getBoolean(Prefs.DYNAMIC_COLOR, true)) {
                    put(Prefs.DYNAMIC_COLOR, it)
                    Ui.init(ctx)
                    recreate()
                })
            }
            addView(Ui.label(ctx, "Hız birimi"))
            addView(Ui.choiceRow(ctx, listOf("MB/s" to false, "Mbps" to true), prefs.getBoolean(Prefs.UNIT_BITS, false)) {
                put(Prefs.UNIT_BITS, it)
                updatePreview()
            })
            addView(Ui.label(ctx, "Yazı rengi · bildirim, yüzen pencere, widget"))
            preview = TextView(ctx).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat() }
            }
            addView(preview, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4); bottomMargin = dp(6) })

            colors.chunked(5).forEach { row ->
                val line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                row.forEach { (name, col) ->
                    val swatch = View(ctx).apply {
                        contentDescription = name
                        setOnClickListener { setColor(col) }
                    }
                    line.addView(swatch, LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(4), dp(4), dp(8), dp(4)) })
                    swatches += swatch to col
                }
                addView(line)
            }

            val hexRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val hexInput = EditText(ctx).apply {
                hint = "Özel renk, ör. #FF9800"
                isSingleLine = true
                textSize = 15f
                setTextColor(Ui.primary(ctx))
                setHintTextColor(Ui.secondary(ctx))
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = Ui.rounded(Color.TRANSPARENT, dp(50).toFloat(), dp(1), Ui.outline(ctx))
            }
            hexRow.addView(hexInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
                topMargin = dp(10)
            })
            hexRow.addView(Ui.button(ctx, "Uygula") {
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
        root.addView(Ui.card(ctx, "Durum çubuğu", "📶").apply {
            addView(Ui.hint(ctx, "Oradaki rengi Android belirler; içeriği ve boyutu buradan ayarlanır."))
            sbPreview = ImageView(ctx).apply {
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = Ui.rounded(0xFF000000.toInt(), dp(14).toFloat())
            }
            addView(sbPreview, LinearLayout.LayoutParams(dp(68), dp(68)).apply {
                topMargin = dp(8)
                bottomMargin = dp(2)
            })
            addView(Ui.label(ctx, "Ne gösterilsin?"))
            addView(Ui.choiceRow(
                ctx,
                listOf("İndirme" to StatusIcon.MODE_DOWN, "Yükleme" to StatusIcon.MODE_UP,
                    "Toplam" to StatusIcon.MODE_TOTAL, "İkisi alt alta" to StatusIcon.MODE_BOTH),
                prefs.getInt(Prefs.SB_MODE, StatusIcon.MODE_DOWN)
            ) {
                put(Prefs.SB_MODE, it)
                updateSbPreview()
            })
            addView(Ui.label(ctx, "Boyut"))
            addView(Ui.choiceRow(ctx, listOf("Normal" to 0, "Büyük" to 1, "En büyük" to 2), prefs.getInt(Prefs.SB_SIZE, 1)) {
                put(Prefs.SB_SIZE, it)
                updateSbPreview()
            })
            addView(Ui.label(ctx, "Birim"))
            addView(Ui.choiceRow(
                ctx,
                listOf("Tam (KB/s)" to StatusIcon.UNIT_FULL, "Kısa (4,2M)" to StatusIcon.UNIT_SHORT, "Gizli" to StatusIcon.UNIT_HIDDEN),
                prefs.getInt(Prefs.SB_UNIT, StatusIcon.UNIT_FULL)
            ) {
                put(Prefs.SB_UNIT, it)
                updateSbPreview()
            })
            addView(Ui.switch(ctx, "Kalın yazı", prefs.getBoolean(Prefs.SB_BOLD, true)) {
                put(Prefs.SB_BOLD, it)
                updateSbPreview()
            })
            addView(Ui.switch(ctx, "Boştayken gizle", prefs.getBoolean(Prefs.SB_HIDE_IDLE, false)) { put(Prefs.SB_HIDE_IDLE, it) })
            addView(Ui.hint(ctx, "\"İkisi alt alta\"da üstteki indirme, alttaki yükleme. \"Boştayken gizle\" açıkken veri akmıyorsa simge boş kalır."))
        })

        // ----- Kayan son X saat -----
        root.addView(Ui.card(ctx, "Son X saat (kayan sayaç)", "🕐").apply {
            addView(Ui.hint(ctx, "Ne zaman baksan tam son X saati gösterir, hiç sıfırlanmaz. Dönem sayacından farkı bu."))
            addView(Ui.label(ctx, "Süre"))
            addView(Ui.choiceRow(ctx, Prefs.ROLL_OPTIONS.map { "Son $it sa" to it }, prefs.getInt(Prefs.ROLL_HOURS, 1)) { put(Prefs.ROLL_HOURS, it) })
            addView(Ui.label(ctx, "Neyi saysın?"))
            addView(Ui.choiceRow(ctx, listOf("Tümü" to 0, "Sadece mobil" to 1, "Sadece Wi-Fi" to 2), prefs.getInt(Prefs.ROLL_SCOPE, 0)) { put(Prefs.ROLL_SCOPE, it) })
            addView(Ui.label(ctx, "Bildirimde nerede?"))
            addView(Ui.choiceRow(ctx, listOf("Ayrı satırda" to 0, "Hızın yanında" to 1, "Gösterme" to 2), prefs.getInt(Prefs.ROLL_POS, 0)) { put(Prefs.ROLL_POS, it) })
            addView(Ui.switch(ctx, "Mobil / Wi-Fi ayrıntısını göster", prefs.getBoolean(Prefs.ROLL_DETAIL, true)) { put(Prefs.ROLL_DETAIL, it) })
            addView(Ui.switch(ctx, "Yüzen pencerede göster", prefs.getBoolean(Prefs.ROLL_OVERLAY, true)) { put(Prefs.ROLL_OVERLAY, it) })
            addView(Ui.switch(ctx, "Widget'ta göster", prefs.getBoolean(Prefs.ROLL_WIDGET, true)) { put(Prefs.ROLL_WIDGET, it) })
            addView(Ui.hint(ctx, "\"Hızın yanında\" kısa biçimde gösterir (ör. ↓ 4,2 MB/s ↑ 120 KB/s · 2sa 340 MB). Ayrıntı sadece ayrı satırda görünür."))
        })

        // ----- Bildirim paneli -----
        root.addView(Ui.card(ctx, "Bildirim paneli", "🔔").apply {
            addView(Ui.hint(ctx, "Kapalıyken ilk iki satır, aşağı çekince seçtiğin tüm satırlar görünür. Değişiklikler bir saniye içinde uygulanır."))
            addView(Ui.label(ctx, "Görünecek satırlar"))
            listOf(
                Triple("Anlık hız", Prefs.N_LINE_SPEED, true),
                Triple("Dönem kullanımı", Prefs.N_LINE_PERIOD, true),
                Triple("Bugün: mobil / Wi-Fi", Prefs.N_LINE_TODAY, true),
                Triple("Bugünün rekor hızı", Prefs.N_LINE_PEAK, false),
                Triple("Bu ayın toplamı", Prefs.N_LINE_MONTH, false)
            ).forEach { (label, key, def) ->
                addView(Ui.switch(ctx, label, prefs.getBoolean(key, def)) { put(key, it) })
            }
            addView(Ui.switch(ctx, "Bağlantı türünü göster (5G, Wi-Fi…)", prefs.getBoolean(Prefs.N_SHOW_CONN, true)) {
                put(Prefs.N_SHOW_CONN, it)
            })
            addView(Ui.label(ctx, "Yazı boyutu"))
            addView(Ui.choiceRow(ctx, listOf("Küçük" to 0, "Normal" to 1, "Büyük" to 2), prefs.getInt(Prefs.N_SIZE, 1)) { put(Prefs.N_SIZE, it) })
            addView(Ui.label(ctx, "Ok stili"))
            addView(Ui.choiceRow(ctx, listOf("↓ ↑" to 0, "▼ ▲" to 1, "İnd. / Yük." to 2), prefs.getInt(Prefs.N_ARROWS, 0)) { put(Prefs.N_ARROWS, it) })
            addView(Ui.switch(ctx, "Sabit genişlikli yazı (rakamlar kaymasın)", prefs.getBoolean(Prefs.N_MONO, false)) { put(Prefs.N_MONO, it) })
            addView(Ui.label(ctx, "Arka plan"))
            addView(Ui.choiceRow(
                ctx,
                listOf(
                    "Sistem" to 0, "Siyah" to 0xFF000000.toInt(), "Lacivert" to 0xFF1A237E.toInt(),
                    "Vurgu rengi" to Ui.ACCENT, "Mor" to 0xFF4A148C.toInt(), "Yeşil" to 0xFF1B5E20.toInt(),
                    "Kırmızı" to 0xFFB71C1C.toInt()
                ),
                prefs.getInt(Prefs.N_BG, 0)
            ) { put(Prefs.N_BG, it) })
            addView(Ui.hint(ctx, "Renkli arka planı her telefon desteklemeyebilir. Koyu arka planda açık bir yazı rengi seç."))
            addView(Ui.label(ctx, "Düğmeler ve gizlilik"))
            addView(Ui.switch(ctx, "\"Pencereyi göster/gizle\" düğmesi", prefs.getBoolean(Prefs.N_BTN_OVERLAY, true)) { put(Prefs.N_BTN_OVERLAY, it) })
            addView(Ui.switch(ctx, "\"Sayacı sıfırla\" düğmesi", prefs.getBoolean(Prefs.N_BTN_RESET, false)) { put(Prefs.N_BTN_RESET, it) })
            addView(Ui.switch(ctx, "Kilit ekranında ayrıntıları göster", prefs.getBoolean(Prefs.N_LOCKSCREEN, true)) { put(Prefs.N_LOCKSCREEN, it) })
        })

        // ----- Yüzen pencere -----
        root.addView(Ui.card(ctx, "Yüzen pencere", "🪟").apply {
            overlaySwitch = Ui.switch(ctx, "Ekranda yüzen hız penceresi", prefs.getBoolean(Prefs.OVERLAY, false)) { checked ->
                put(Prefs.OVERLAY, checked)
                if (checked && !Settings.canDrawOverlays(ctx)) {
                    Toast.makeText(ctx, "Hız Göstergesi'ne \"Diğer uygulamaların üzerinde göster\" izni ver, sonra geri dön", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
            addView(overlaySwitch)
            addView(Ui.seekRow(ctx, "Yazı boyutu", 9, 24, prefs.getInt(Prefs.OVERLAY_SIZE, 12), "") {
                put(Prefs.OVERLAY_SIZE, it)
                updatePreview()
            })
            addView(Ui.seekRow(ctx, "Arka plan koyuluğu", 0, 100, prefs.getInt(Prefs.OVERLAY_ALPHA, 60), "%") {
                put(Prefs.OVERLAY_ALPHA, it)
                updatePreview()
            })
            addView(Ui.hint(ctx, "Parmağınla sürükleyip taşı. Çift dokununca gizlenir; bildirimdeki \"Pencereyi göster\" ile geri açılır."))
        })

        // ----- Pil -----
        root.addView(Ui.card(ctx, "Pil tasarrufu", "🔋").apply {
            addView(Ui.label(ctx, "Güncelleme aralığı"))
            addView(Ui.choiceRow(ctx, listOf("1 sn" to 1, "2 sn" to 2, "5 sn" to 5), prefs.getInt(Prefs.INTERVAL_SEC, 1)) { put(Prefs.INTERVAL_SEC, it) })
            addView(Ui.switch(ctx, "Ekran kapalıyken yavaşlat (önerilen)", prefs.getBoolean(Prefs.SLOW_WHEN_OFF, true)) { put(Prefs.SLOW_WHEN_OFF, it) })
            addView(Ui.hint(ctx, "Aralık uzadıkça pil daha az harcanır; kullanım toplamları her aralıkta doğru kalır. Ekran kapalıyken sadece göstergeler çizilmez."))
        })

        // ----- Bildirimler -----
        root.addView(Ui.card(ctx, "Otomatik bildirimler", "📬").apply {
            addView(Ui.switch(ctx, "Haftalık özet (pazartesi sabahı)", prefs.getBoolean(Prefs.WEEKLY_ON, true)) { put(Prefs.WEEKLY_ON, it) })
            addView(Ui.switch(ctx, "Olağandışı kullanım uyarısı", prefs.getBoolean(Prefs.ANOMALY_ON, true)) { put(Prefs.ANOMALY_ON, it) })
            addView(Ui.hint(ctx, "Uygulamaya özel günlük sınırları, uygulamanın ayrıntı ekranından koyabilirsin."))
        })

        // ----- Diğer -----
        root.addView(Ui.card(ctx, "Diğer", "⚙️").apply {
            addView(Ui.hint(ctx, "Ana ekran widget'ı için: boş bir yere basılı tut → Widget'lar → Hız Göstergesi."))
            addView(Ui.button(ctx, "Kurulum sihirbazını tekrar aç") {
                startActivity(Intent(ctx, OnboardingActivity::class.java))
            })
            val version = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (e: Exception) {
                ""
            }
            addView(Ui.hint(ctx, "Sürüm $version · İnternet izni yok, tüm veriler bu telefonda kalır.").apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            })
        })

        setColor(prefs.getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR), save = false)
        updatePreview()
        return scroll
    }

    private fun updateSbPreview() {
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        sbPreview.setImageBitmap(StatusIcon.draw(prefs, 4_404_019, 122_880, bits, ignoreIdle = true))
    }

    private fun updatePreview() {
        updateSbPreview()
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        val alpha = prefs.getInt(Prefs.OVERLAY_ALPHA, 60)
        preview.text = "↓ ${Fmt.speed(4_404_019, bits)}  ↑ ${Fmt.speed(122_880, bits)}  ·  5G"
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
                setStroke(if (selected) dp(4) else dp(1), if (selected) Ui.ACCENT else Ui.outline(this@SettingsActivity))
            }
        }
    }
}
