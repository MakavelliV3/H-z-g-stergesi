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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bir ayar kategorisinin sayfası. Hangi kategori olduğu [EXTRA_CATEGORY] ile gelir. */
class SettingsActivity : Activity() {

    companion object {
        const val EXTRA_CATEGORY = "kategori"
        const val CAT_STATUS = "durum"
        const val CAT_NOTIF = "bildirim"
        const val CAT_COUNTERS = "sayac"
        const val CAT_PLAN = "paket"
        const val CAT_ALERTS = "uyari"
        const val CAT_OVERLAY = "pencere"
        const val CAT_LOOK = "gorunum"
        const val CAT_BATTERY = "pil"
        const val CAT_ABOUT = "hakkinda"

        /** Bildirim arka planı için "vurgu rengini kullan" işareti (renk değil). */
        const val BG_ACCENT = 1

        val TITLES = mapOf(
            CAT_STATUS to "Durum çubuğu", CAT_NOTIF to "Bildirim paneli", CAT_PLAN to "Paketim", CAT_COUNTERS to "Sayaçlar",
            CAT_ALERTS to "Uyarılar", CAT_OVERLAY to "Yüzen pencere", CAT_LOOK to "Görünüm",
            CAT_BATTERY to "Pil ve widget", CAT_ABOUT to "Kurulum ve hakkında"
        )
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var overlaySwitch: Switch
    private var preview: TextView? = null
    private var sbPreview: ImageView? = null
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
        val cat = intent.getStringExtra(EXTRA_CATEGORY) ?: CAT_STATUS
        val (scroll, root) = Ui.page(this)
        root.addView(Ui.topBar(this, TITLES[cat] ?: "Ayarlar"))
        when (cat) {
            CAT_STATUS -> statusBar(root)
            CAT_NOTIF -> notification(root)
            CAT_COUNTERS -> counters(root)
            CAT_PLAN -> plan(root)
            CAT_ALERTS -> alerts(root)
            CAT_OVERLAY -> overlay(root)
            CAT_LOOK -> look(root)
            CAT_BATTERY -> battery(root)
            else -> about(root)
        }
        setContentView(scroll)
        updatePreview()
    }

    override fun onResume() {
        super.onResume()
        if (::overlaySwitch.isInitialized) {
            overlaySwitch.isChecked = prefs.getBoolean(Prefs.OVERLAY, false) && Settings.canDrawOverlays(this)
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun put(key: String, v: Boolean) = prefs.edit().putBoolean(key, v).apply()
    private fun put(key: String, v: Int) = prefs.edit().putInt(key, v).apply()
    private val stamp = SimpleDateFormat("d MMM HH:mm", Locale("tr"))

    // ---------- Durum çubuğu ----------

    private fun statusBar(root: LinearLayout) {
        val ctx = this
        root.addView(Ui.card(ctx, "Önizleme", "👁").apply {
            addView(Ui.hint(ctx, "Durum çubuğundaki simgenin rengini Android belirler; içeriği ve boyutu buradan ayarlanır."))
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(4))
            }
            sbPreview = ImageView(ctx).apply {
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = Ui.rounded(0xFF000000.toInt(), dp(16).toFloat())
                contentDescription = "Durum çubuğu simgesi önizlemesi"
            }
            row.addView(sbPreview, LinearLayout.LayoutParams(dp(72), dp(72)).apply { rightMargin = dp(14) })
            row.addView(Ui.hint(ctx, "Örnek: 4,4 MB/s indirme, 120 KB/s yükleme"), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(row)
        })
        root.addView(Ui.card(ctx, "İçerik", "📶").apply {
            addView(Ui.label(ctx, "Ne gösterilsin?"))
            addView(Ui.choiceRow(
                ctx,
                listOf("İndirme" to StatusIcon.MODE_DOWN, "Yükleme" to StatusIcon.MODE_UP,
                    "Toplam" to StatusIcon.MODE_TOTAL, "İkisi alt alta" to StatusIcon.MODE_BOTH),
                prefs.getInt(Prefs.SB_MODE, StatusIcon.MODE_DOWN)
            ) {
                put(Prefs.SB_MODE, it)
                updatePreview()
            })
            addView(Ui.label(ctx, "Boyut"))
            addView(Ui.choiceRow(ctx, listOf("Normal" to 0, "Büyük" to 1, "En büyük" to 2), prefs.getInt(Prefs.SB_SIZE, 1)) {
                put(Prefs.SB_SIZE, it)
                updatePreview()
            })
            addView(Ui.label(ctx, "Birim"))
            addView(Ui.choiceRow(
                ctx,
                listOf("Tam (KB/s)" to StatusIcon.UNIT_FULL, "Kısa (4,4M)" to StatusIcon.UNIT_SHORT, "Gizli" to StatusIcon.UNIT_HIDDEN),
                prefs.getInt(Prefs.SB_UNIT, StatusIcon.UNIT_FULL)
            ) {
                put(Prefs.SB_UNIT, it)
                updatePreview()
            })
            addView(Ui.switch(ctx, "Kalın yazı", prefs.getBoolean(Prefs.SB_BOLD, true)) {
                put(Prefs.SB_BOLD, it)
                updatePreview()
            })
            addView(Ui.switch(ctx, "Boştayken gizle", prefs.getBoolean(Prefs.SB_HIDE_IDLE, false)) { put(Prefs.SB_HIDE_IDLE, it) })
            addView(Ui.hint(ctx, "\"İkisi alt alta\"da üstteki indirme, alttaki yükleme. \"Boştayken gizle\" açıkken veri akmıyorsa simge boş kalır."))
        })
    }

    // ---------- Bildirim paneli ----------

    private fun notification(root: LinearLayout) {
        val ctx = this
        root.addView(Ui.card(ctx, "Satırlar", "📝").apply {
            addView(Ui.hint(ctx, "Bildirim kapalıyken ilk iki satır, aşağı çekince seçtiğin tüm satırlar görünür. Değişiklikler bir saniye içinde uygulanır."))
            listOf(
                Triple("Anlık hız", Prefs.N_LINE_SPEED, true),
                Triple("Paket durumu (paket takibi açıksa)", Prefs.N_LINE_PLAN, true),
                Triple("Dönem sayacı (sabit pencere)", Prefs.N_LINE_PERIOD, false),
                Triple("Bugün: mobil / Wi-Fi", Prefs.N_LINE_TODAY, true),
                Triple("Bugünün rekor hızı", Prefs.N_LINE_PEAK, false),
                Triple("Bu ayın toplamı", Prefs.N_LINE_MONTH, false)
            ).forEach { (label, key, def) ->
                addView(Ui.switch(ctx, label, prefs.getBoolean(key, def)) { put(key, it) })
            }
            addView(Ui.switch(ctx, "Bağlantı türünü göster (5G, Wi-Fi…)", prefs.getBoolean(Prefs.N_SHOW_CONN, true)) { put(Prefs.N_SHOW_CONN, it) })
            addView(Ui.hint(ctx, "Kayan \"son X saat\" satırının yeri Sayaçlar sayfasından seçilir."))
        })
        root.addView(Ui.card(ctx, "Görünüş", "🎛").apply {
            addView(Ui.label(ctx, "Yazı boyutu"))
            addView(Ui.choiceRow(ctx, listOf("Küçük" to 0, "Normal" to 1, "Büyük" to 2), prefs.getInt(Prefs.N_SIZE, 1)) { put(Prefs.N_SIZE, it) })
            addView(Ui.label(ctx, "Ok stili"))
            addView(Ui.choiceRow(ctx, listOf("↓ ↑" to 0, "▼ ▲" to 1, "İnd. / Yük." to 2), prefs.getInt(Prefs.N_ARROWS, 0)) { put(Prefs.N_ARROWS, it) })
            addView(Ui.switch(ctx, "Sabit genişlikli yazı (rakamlar kaymasın)", prefs.getBoolean(Prefs.N_MONO, false)) { put(Prefs.N_MONO, it) })
            addView(Ui.label(ctx, "Arka plan"))
            addView(Ui.choiceRow(
                ctx,
                listOf(
                    "Sistem" to 0, "Vurgu rengi" to BG_ACCENT, "Siyah" to 0xFF000000.toInt(),
                    "Lacivert" to 0xFF1A237E.toInt(), "Mor" to 0xFF4A148C.toInt(), "Yeşil" to 0xFF1B5E20.toInt(),
                    "Kırmızı" to 0xFFB71C1C.toInt()
                ),
                prefs.getInt(Prefs.N_BG, 0)
            ) { put(Prefs.N_BG, it) })
            addView(Ui.hint(ctx, "Renkli arka planı her telefon desteklemeyebilir. Koyu arka planda açık bir yazı rengi seç (Görünüm sayfası)."))
        })
        root.addView(Ui.card(ctx, "Düğmeler ve gizlilik", "🔘").apply {
            addView(Ui.switch(ctx, "\"Pencereyi göster/gizle\" düğmesi", prefs.getBoolean(Prefs.N_BTN_OVERLAY, true)) { put(Prefs.N_BTN_OVERLAY, it) })
            addView(Ui.switch(ctx, "\"Sayacı sıfırla\" düğmesi", prefs.getBoolean(Prefs.N_BTN_RESET, false)) { put(Prefs.N_BTN_RESET, it) })
            addView(Ui.switch(ctx, "\"Son X saati sıfırla\" düğmesi", prefs.getBoolean(Prefs.N_BTN_ROLL_RESET, false)) { put(Prefs.N_BTN_ROLL_RESET, it) })
            addView(Ui.switch(ctx, "Kilit ekranında ayrıntıları göster", prefs.getBoolean(Prefs.N_LOCKSCREEN, true)) { put(Prefs.N_LOCKSCREEN, it) })
        })
    }

    // ---------- Paket ----------

    private fun refreshPlan() {
        val ctx = applicationContext
        Thread { try { PlanState.compute(ctx) } catch (e: Exception) { } }.start()
    }

    private fun plan(root: LinearLayout) {
        val ctx = this
        root.addView(Ui.card(ctx, "Paket takibi", "📦").apply {
            addView(Ui.hint(ctx, "Operatör paketindeki mobil veriyi takip eder: kalan GB, kalan gün, günlük pay ve paketin yetip yetmeyeceği. Sadece mobil veri sayılır."))
            addView(Ui.switch(ctx, "Paket takibini aç", prefs.getBoolean(Prefs.PLAN_ON, false)) {
                put(Prefs.PLAN_ON, it)
                if (it && prefs.getInt(Prefs.PLAN_MB, 0) <= 0) put(Prefs.PLAN_MB, 20 * 1024)
                refreshPlan()
            })
            addView(Ui.label(ctx, "Paket büyüklüğü"))
            val sizes = listOf(1, 2, 3, 5, 8, 10, 15, 20, 25, 30, 40, 50, 100).map { "$it GB" to it * 1024 }
            addView(Ui.choiceRow(ctx, sizes, prefs.getInt(Prefs.PLAN_MB, 20 * 1024)) {
                put(Prefs.PLAN_MB, it)
                refreshPlan()
            })
            val customRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val gbInput = EditText(ctx).apply {
                hint = "Başka bir miktar (GB), ör. 12,5"
                isSingleLine = true
                textSize = 15f
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setTextColor(Ui.primary(ctx))
                setHintTextColor(Ui.secondary(ctx))
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = Ui.rounded(Color.TRANSPARENT, dp(50).toFloat(), dp(1), Ui.outline(ctx))
            }
            customRow.addView(gbInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = dp(8)
                topMargin = dp(8)
            })
            customRow.addView(Ui.button(ctx, "Kaydet") {
                val gb = gbInput.text.toString().replace(',', '.').trim().toDoubleOrNull()
                if (gb == null || gb <= 0 || gb > 10_000) {
                    toast("Geçerli bir GB değeri yaz")
                } else {
                    put(Prefs.PLAN_MB, (gb * 1024).toInt())
                    put(Prefs.PLAN_ON, true)
                    toast("Paket ${Fmt.mb((gb * 1024).toInt())} olarak kaydedildi")
                    refreshPlan()
                    recreate()
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(customRow)
            addView(Ui.seekRow(ctx, "Yenilenme günü (ayın)", 1, 31, prefs.getInt(Prefs.PLAN_DAY, 1), ".") {
                put(Prefs.PLAN_DAY, it)
                refreshPlan()
            })
            addView(Ui.hint(ctx, "Paketin her ay hangi gün yenileniyor? 29-31 seçilirse o günü olmayan aylarda ayın son günü sayılır."))
        })
        root.addView(Ui.card(ctx, "Gece paketi", "🌙").apply {
            addView(Ui.hint(ctx, "Operatörün gece saatleri için ayrı bir paketi varsa, bu saatlerdeki mobil kullanım ana paketten düşülmez."))
            addView(Ui.switch(ctx, "Gece saatlerini ayrı say", prefs.getBoolean(Prefs.PLAN_NIGHT, false)) {
                put(Prefs.PLAN_NIGHT, it)
                refreshPlan()
            })
            addView(Ui.seekRow(ctx, "Başlangıç saati", 0, 23, prefs.getInt(Prefs.NIGHT_START, 0), ":00") {
                put(Prefs.NIGHT_START, it)
                refreshPlan()
            })
            addView(Ui.seekRow(ctx, "Bitiş saati", 0, 23, prefs.getInt(Prefs.NIGHT_END, 8), ":00") {
                put(Prefs.NIGHT_END, it)
                refreshPlan()
            })
            addView(Ui.hint(ctx, "Gece kullanımı uygulamanın kendi saatlik ölçümlerinden hesaplanır (kurulumdan sonrası)."))
        })
        root.addView(Ui.card(ctx, "Uyarı ve gösterim", "🔔").apply {
            addView(Ui.switch(ctx, "%80 ve %100'e gelince bildir", prefs.getBoolean(Prefs.PLAN_ALERTS, true)) { put(Prefs.PLAN_ALERTS, it) })
            addView(Ui.switch(ctx, "Bildirim panelinde paket satırı", prefs.getBoolean(Prefs.N_LINE_PLAN, true)) { put(Prefs.N_LINE_PLAN, it) })
            addView(Ui.hint(ctx, "\"Kullanım erişimi\" izni varsa dönem başından bu yana tüm kullanım (kurulumdan öncesi dahil) Android'in kayıtlarından okunur."))
        })
    }

    // ---------- Sayaçlar ----------

    private fun counters(root: LinearLayout) {
        val ctx = this
        root.addView(Ui.card(ctx, "Son X saat · kayan sayaç", "🕐").apply {
            addView(Ui.hint(ctx, "Ne zaman baksan tam son X saati gösterir, kendiliğinden sıfırlanmaz."))
            addView(Ui.label(ctx, "Süre"))
            addView(Ui.choiceRow(ctx, Prefs.ROLL_OPTIONS.map { "Son $it sa" to it }, prefs.getInt(Prefs.ROLL_HOURS, 1)) { put(Prefs.ROLL_HOURS, it) })
            addView(Ui.label(ctx, "Neyi saysın?"))
            addView(Ui.choiceRow(ctx, listOf("Tümü" to 0, "Sadece mobil" to 1, "Sadece Wi-Fi" to 2), prefs.getInt(Prefs.ROLL_SCOPE, 0)) { put(Prefs.ROLL_SCOPE, it) })
            addView(Ui.label(ctx, "Bildirimde nerede?"))
            addView(Ui.choiceRow(ctx, listOf("Ayrı satırda" to 0, "Hızın yanında" to 1, "Gösterme" to 2), prefs.getInt(Prefs.ROLL_POS, 0)) { put(Prefs.ROLL_POS, it) })
            addView(Ui.switch(ctx, "Mobil / Wi-Fi ayrıntısını göster", prefs.getBoolean(Prefs.ROLL_DETAIL, true)) { put(Prefs.ROLL_DETAIL, it) })
            addView(Ui.switch(ctx, "Yüzen pencerede göster", prefs.getBoolean(Prefs.ROLL_OVERLAY, true)) { put(Prefs.ROLL_OVERLAY, it) })
            addView(Ui.switch(ctx, "Widget'ta göster", prefs.getBoolean(Prefs.ROLL_WIDGET, true)) { put(Prefs.ROLL_WIDGET, it) })
            val resetText = Ui.hint(ctx, "")
            fun update() {
                val at = prefs.getLong(Prefs.ROLL_RESET_AT, 0L)
                resetText.text = if (at == 0L) "Hiç sıfırlanmadı." else
                    "Son sıfırlama: ${stamp.format(Date(at))}. Seçtiğin süre dolana kadar sıfırlamadan bu yana olan gösterilir."
            }
            update()
            addView(Ui.button(ctx, "Kayan sayacı şimdi sıfırla") {
                startForegroundService(Intent(ctx, SpeedService::class.java).setAction(SpeedService.ACTION_RESET_ROLL))
                prefs.edit().putLong(Prefs.ROLL_RESET_AT, System.currentTimeMillis()).apply()
                update()
                toast("Kayan sayaç sıfırlandı")
            })
            addView(resetText)
        })
        root.addView(Ui.card(ctx, "Dönem sayacı", "⏱").apply {
            addView(Ui.hint(ctx, "Sabit pencere: seçtiğin süre dolunca kendiliğinden sıfırlanır. Veri sınırı uyarısı bu sayaca göre çalışır."))
            addView(Ui.label(ctx, "Neyi saysın?"))
            addView(Ui.choiceRow(ctx, listOf("Tümü" to false, "Sadece mobil veri" to true), prefs.getBoolean(Prefs.MOBILE_ONLY, false)) { put(Prefs.MOBILE_ONLY, it) })
            addView(Ui.label(ctx, "Kaç saatte bir sıfırlansın?"))
            addView(Ui.choiceRow(ctx, Prefs.HOUR_OPTIONS.map { "$it sa" to it }, prefs.getInt(Prefs.HOURS, 1)) { put(Prefs.HOURS, it) })
            addView(Ui.button(ctx, "Dönem sayacını şimdi sıfırla") {
                startForegroundService(Intent(ctx, SpeedService::class.java).setAction(SpeedService.ACTION_RESET))
                toast("Dönem sayacı sıfırlandı")
            })
        })
    }

    // ---------- Uyarılar ----------

    private fun alerts(root: LinearLayout) {
        val ctx = this
        root.addView(Ui.card(ctx, "Veri sınırı", "🚨").apply {
            addView(Ui.hint(ctx, "Dönem sayacı bu sınırı aşınca bir kez bildirim gelir. \"Sadece mobil veri\" seçiliyse yalnızca mobil veri sayılır."))
            addView(Ui.choiceRow(
                ctx,
                listOf("Kapalı" to 0, "250 MB" to 250, "500 MB" to 500, "1 GB" to 1024, "2 GB" to 2048, "5 GB" to 5120, "10 GB" to 10240),
                prefs.getInt(Prefs.QUOTA_MB, 0)
            ) {
                prefs.edit().putInt(Prefs.QUOTA_MB, it).putLong(Prefs.QUOTA_WARNED, 0L).apply()
            })
        })
        root.addView(Ui.card(ctx, "Otomatik bildirimler", "📬").apply {
            addView(Ui.switch(ctx, "Haftalık özet (pazartesi sabahı)", prefs.getBoolean(Prefs.WEEKLY_ON, true)) { put(Prefs.WEEKLY_ON, it) })
            addView(Ui.switch(ctx, "Olağandışı kullanım uyarısı", prefs.getBoolean(Prefs.ANOMALY_ON, true)) { put(Prefs.ANOMALY_ON, it) })
            addView(Ui.hint(ctx, "Bir uygulama bugün 100 MB'ı geçip son 7 günlük ortalamasının 3 katından fazla veri çekerse uyarır. " +
                "Uygulamaya özel günlük sınırları, uygulamanın ayrıntı sayfasından koyabilirsin."))
        })
    }

    // ---------- Yüzen pencere ----------

    private fun overlay(root: LinearLayout) {
        val ctx = this
        root.addView(Ui.card(ctx, "Yüzen pencere", "🪟").apply {
            overlaySwitch = Ui.switch(ctx, "Ekranda yüzen hız penceresi", prefs.getBoolean(Prefs.OVERLAY, false)) { checked ->
                put(Prefs.OVERLAY, checked)
                if (checked && !Settings.canDrawOverlays(ctx)) {
                    Toast.makeText(ctx, "Hız Göstergesi'ne \"Diğer uygulamaların üzerinde göster\" izni ver, sonra geri dön", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
            }
            addView(overlaySwitch)
            addPreview(this)
            addView(Ui.seekRow(ctx, "Yazı boyutu", 9, 24, prefs.getInt(Prefs.OVERLAY_SIZE, 12), "") {
                put(Prefs.OVERLAY_SIZE, it)
                updatePreview()
            })
            addView(Ui.seekRow(ctx, "Arka plan koyuluğu", 0, 100, prefs.getInt(Prefs.OVERLAY_ALPHA, 60), "%") {
                put(Prefs.OVERLAY_ALPHA, it)
                updatePreview()
            })
            addView(Ui.hint(ctx, "Parmağınla sürükleyip taşı. Çift dokununca gizlenir; bildirimdeki \"Pencereyi göster\" ile geri açılır. Yazı rengi Görünüm sayfasından seçilir."))
        })
    }

    // ---------- Görünüm ----------

    private fun look(root: LinearLayout) {
        val ctx = this
        root.addView(Ui.card(ctx, "Tema ve birimler", "🎨").apply {
            if (Build.VERSION.SDK_INT >= 31) {
                addView(Ui.switch(ctx, "Material You (duvar kâğıdı renkleri)", prefs.getBoolean(Prefs.DYNAMIC_COLOR, true)) {
                    put(Prefs.DYNAMIC_COLOR, it)
                    Ui.init(ctx)
                    toast("Renkler güncellendi; uygulamayı yeniden açınca her yerde geçerli olur")
                    recreate()
                })
            }
            addView(Ui.label(ctx, "Hız birimi"))
            addView(Ui.choiceRow(ctx, listOf("MB/s" to false, "Mbps" to true), prefs.getBoolean(Prefs.UNIT_BITS, false)) {
                put(Prefs.UNIT_BITS, it)
                updatePreview()
            })
            addView(Ui.label(ctx, "Birim tabanı"))
            addView(Ui.choiceRow(ctx, listOf("1000 (Android gibi)" to 1000, "1024" to 1024), prefs.getInt(Prefs.UNIT_BASE, 1000)) {
                put(Prefs.UNIT_BASE, it)
                Fmt.base = it.toLong()
                updatePreview()
            })
            addView(Ui.hint(ctx, "Android'in kendi veri kullanımı ekranı ve operatörler 1000 tabanını kullanır (1 GB = 1000 MB). Rakamların onlarla tutması için 1000 önerilir."))
        })
        root.addView(Ui.card(ctx, "Yazı rengi", "🖍").apply {
            addView(Ui.hint(ctx, "Bildirim paneli, yüzen pencere ve widget'taki yazılarda kullanılır. " +
                "\"Otomatik\": bildirimde telefonun açık/koyu temasına uyan renk, koyu zeminlerde beyaz."))
            addPreview(this)
            val auto = TextView(ctx).apply {
                text = "Otomatik"
                textSize = 14f
                typeface = Ui.MEDIUM
                gravity = Gravity.CENTER
                setTextColor(Ui.primary(ctx))
                setPadding(dp(16), 0, dp(16), 0)
                contentDescription = "Otomatik renk"
                isClickable = true
                setOnClickListener { setColor(Prefs.COLOR_AUTO) }
            }
            addView(auto, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)).apply { setMargins(dp(4), dp(4), 0, dp(4)) })
            swatches += auto to Prefs.COLOR_AUTO
            colors.chunked(5).forEach { row ->
                val line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                row.forEach { (name, col) ->
                    val swatch = View(ctx).apply {
                        contentDescription = name
                        isClickable = true
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
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
            addView(hexRow)
        })
        setColor(prefs.getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR), save = false)
    }

    // ---------- Pil ve widget ----------

    private fun battery(root: LinearLayout) {
        val ctx = this
        root.addView(Ui.card(ctx, "Pil tasarrufu", "🔋").apply {
            addView(Ui.label(ctx, "Güncelleme aralığı"))
            addView(Ui.choiceRow(ctx, listOf("1 sn" to 1, "2 sn" to 2, "5 sn" to 5), prefs.getInt(Prefs.INTERVAL_SEC, 1)) { put(Prefs.INTERVAL_SEC, it) })
            addView(Ui.switch(ctx, "Ekran kapalıyken yavaşlat (önerilen)", prefs.getBoolean(Prefs.SLOW_WHEN_OFF, true)) { put(Prefs.SLOW_WHEN_OFF, it) })
            addView(Ui.hint(ctx, "Aralık uzadıkça pil daha az harcanır; kullanım toplamları her aralıkta doğru kalır. Bildirim, içeriği değişmedikçe yeniden çizilmez."))
        })
        root.addView(Ui.card(ctx, "Ana ekran widget'ı", "🧩").apply {
            addView(Ui.label(ctx, "Widget ne sıklıkla güncellensin?"))
            addView(Ui.choiceRow(ctx, listOf("5 sn" to 5, "15 sn" to 15, "30 sn" to 30, "1 dk" to 60), prefs.getInt(Prefs.WIDGET_SEC, 15)) { put(Prefs.WIDGET_SEC, it) })
            addView(Ui.hint(ctx, "Eklemek için: ana ekranda boş bir yere basılı tut → Widget'lar → Hız Göstergesi."))
        })
    }

    // ---------- Kurulum ve hakkında ----------

    private fun about(root: LinearLayout) {
        val ctx = this
        root.addView(Ui.card(ctx, "Kurulum", "🧭").apply {
            addView(Ui.hint(ctx, "İzinleri (bildirim, pil, kullanım erişimi, bağlantı türü, yüzen pencere) adım adım yeniden gözden geçir."))
            addView(Ui.button(ctx, "Kurulum sihirbazını aç", filled = true) { startActivity(Intent(ctx, OnboardingActivity::class.java)) })
        })
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        root.addView(Ui.card(ctx, "Hakkında", "ℹ️").apply {
            addView(Ui.text(ctx, "Hız Göstergesi $version", 16f).apply { typeface = Ui.MEDIUM })
            addView(Ui.hint(ctx, "İnternet izni yoktur: uygulama hiçbir yere bağlanamaz, tüm veriler bu telefonda kalır. " +
                "Hız ve kullanım, Android'in bayt sayaçlarından ve (izin verirsen) kendi ağ kayıtlarından okunur."))
        })
    }

    // ---------- Önizleme ve renk ----------

    private fun addPreview(parent: LinearLayout) {
        if (preview != null) return
        preview = TextView(this).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat() }
        }
        parent.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6); bottomMargin = dp(8) })
    }

    private fun updatePreview() {
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        sbPreview?.setImageBitmap(StatusIcon.draw(prefs, 4_404_019, 122_880, bits, ignoreIdle = true))
        preview?.let { p ->
            val alpha = prefs.getInt(Prefs.OVERLAY_ALPHA, 60)
            p.text = "↓ ${Fmt.speed(4_404_019, bits)}  ↑ ${Fmt.speed(122_880, bits)}  ·  5G"
            p.textSize = prefs.getInt(Prefs.OVERLAY_SIZE, 12).toFloat()
            p.setTextColor(Prefs.onDark(prefs.getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR)))
            (p.background as? GradientDrawable)?.setColor(Color.argb(alpha * 255 / 100, 0, 0, 0))
        }
    }

    private fun setColor(color: Int, save: Boolean = true) {
        if (save) prefs.edit().putInt(Prefs.COLOR, color).apply()
        preview?.setTextColor(Prefs.onDark(color))
        swatches.forEach { (view, col) ->
            val selected = col == color
            view.background = if (col == Prefs.COLOR_AUTO) {
                Ui.rounded(0, dp(50).toFloat(), if (selected) dp(3) else dp(1), if (selected) Ui.ACCENT else Ui.outline(this@SettingsActivity))
            } else GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(col)
                setStroke(if (selected) dp(4) else dp(1), if (selected) Ui.ACCENT else Ui.outline(this@SettingsActivity))
            }
        }
    }
}
