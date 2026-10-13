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
import java.util.TreeMap

/** Bir ayar kategorisinin sayfası. Hangi kategori olduğu [EXTRA_CATEGORY] ile gelir. */
class SettingsActivity : Activity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(Themes.wrap(newBase))
    }

    companion object {
        const val EXTRA_CATEGORY = "kategori"
        const val CAT_STATUS = "durum"
        const val CAT_NOTIF = "bildirim"
        const val CAT_COUNTERS = "sayac"
        const val CAT_PLAN = "paket"
        private const val REQ_BACKUP = 41
        private const val REQ_RESTORE = 42
        private const val REQ_CSV = 43
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
        root.addView(Ui.card(ctx, "Bildirim türü", "⚡").apply {
            addView(Ui.choiceRow(ctx, listOf("Klasik" to false, "Canlı (sol üstte hap)" to true), prefs.getBoolean(Prefs.N_LIVE, false)) {
                put(Prefs.N_LIVE, it)
                if (it && LiveUpdate.supported && !LiveUpdate.allowed(ctx)) {
                    toast("Hız Göstergesi için \"Canlı bildirimler\"i aç")
                    try { startActivity(LiveUpdate.settingsIntent(ctx)) } catch (e: Exception) { }
                }
                recreate()
            })
            addView(Ui.hint(ctx, when {
                !LiveUpdate.supported -> "Canlı bildirim Android 16 (One UI 8) gerektirir; bu telefonda klasik bildirim kullanılır."
                prefs.getBoolean(Prefs.N_LIVE, false) -> "Hız, durum çubuğunun solunda hap olarak ve kilit ekranındaki Now Bar'da görünür. " +
                    "Android'in standart tasarımı kullanıldığı için mini grafik ve renkli arka plan bu modda yoktur."
                else -> "Klasik: kendi tasarımımız (mini grafik, satır seçimi). Canlı: kronometre gibi durum çubuğunun solunda hap ve Now Bar."
            }))
            if (prefs.getBoolean(Prefs.N_LIVE, false)) {
                val status = Ui.text(ctx, LiveUpdate.statusText(ctx), 14f, topDp = 6, color = Ui.accentText(ctx))
                addView(status)
                // Bildirim bir iki saniye içinde yeniden kurulur; durumu sonra tekrar oku
                status.postDelayed({ status.text = LiveUpdate.statusText(ctx) }, 2500)
                addView(Ui.button(ctx, "Geliştirici seçeneklerini aç") {
                    try {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    } catch (e: Exception) {
                        toast("Önce Ayarlar → Telefon hakkında → Yazılım bilgileri → Yapım numarasına 7 kez dokun")
                    }
                })
            }
            if (prefs.getBoolean(Prefs.N_LIVE, false) && LiveUpdate.supported && !LiveUpdate.allowed(ctx)) {
                addView(Ui.button(ctx, "Canlı bildirim iznini aç", filled = true) {
                    try { startActivity(LiveUpdate.settingsIntent(ctx)) } catch (e: Exception) {
                        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                    }
                })
            }
        })
        root.addView(Ui.card(ctx, "Kapalı görünüm", "📶").apply {
            addView(Ui.hint(ctx, "Bildirim küçükken (yukarı kaydırılmış hâli) hızın altında ne görünsün?"))
            addView(Ui.choiceRow(ctx, listOf("Paketten kalan" to 0, "Bugünkü kullanım" to 1, "Son X saat" to 2, "Sadece hız" to 3),
                prefs.getInt(Prefs.N_SMALL, 0)) { put(Prefs.N_SMALL, it) })
            addView(Ui.switch(ctx, "Mini hız grafiği", prefs.getBoolean(Prefs.N_GRAPH, true)) { put(Prefs.N_GRAPH, it) })
            addView(Ui.switch(ctx, "İnce paket çubuğu (paket takibi açıksa)", prefs.getBoolean(Prefs.N_BAR, true)) { put(Prefs.N_BAR, it) })
            addView(Ui.hint(ctx, "\"Paketten kalan\" seçiliyken paket takibi kapalıysa bugünkü kullanım gösterilir."))
        })
        root.addView(Ui.card(ctx, "Açık görünüm satırları", "📝").apply {
            addView(Ui.hint(ctx, "Bildirimi aşağı çekince görünen satırlar. Değişiklikler bir saniye içinde uygulanır."))
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

    private fun parseGb(s: String): Double? = s.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it <= 10_000 }

    private fun gbField(hintText: String) = EditText(this).apply {
        hint = hintText
        isSingleLine = true
        textSize = 15f
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        setTextColor(Ui.primary(context))
        setHintTextColor(Ui.secondary(context))
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = Ui.rounded(Color.TRANSPARENT, dp(50).toFloat(), dp(1), Ui.outline(context))
    }

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
        root.addView(Ui.card(ctx, "Operatörle eşitle", "🔄").apply {
            addView(Ui.hint(ctx, "Operatör SMS'le ya da uygulamasında \"kalan 3,2 GB\" diyorsa o rakamı yaz: uygulama kendini ona göre düzeltir ve oradan saymaya devam eder. Düzeltme sadece bu dönem için geçerli."))
            val input = gbField("Operatörün söylediği kalan (GB)")
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8); topMargin = dp(8) })
            row.addView(Ui.button(ctx, "Eşitle") {
                val gb = parseGb(input.text.toString())
                if (gb == null || gb < 0) {
                    toast("Geçerli bir GB değeri yaz")
                } else {
                    val app = applicationContext
                    Thread {
                        PlanState.calibrate(app, (gb * Fmt.base * Fmt.base * Fmt.base).toLong())
                        runOnUiThread { toast("Eşitlendi: kalan ${Fmt.bytes((gb * Fmt.base * Fmt.base * Fmt.base).toLong())}") }
                    }.start()
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(row)
            if (PlanState.last?.calibrated == true) {
                addView(Ui.button(ctx, "Eşitlemeyi kaldır") {
                    prefs.edit().putLong(Prefs.PLAN_OFFSET, 0L).apply()
                    refreshPlan()
                    toast("Eşitleme kaldırıldı")
                    recreate()
                })
            }
        })
        root.addView(Ui.card(ctx, "Ek paket", "➕").apply {
            addView(Ui.hint(ctx, "Dönem ortasında ek GB aldıysan buradan ekle; sadece bu döneme eklenir, yenilenince sıfırlanır."))
            val extra = PlanState.last?.extra ?: 0L
            if (extra > 0) addView(Ui.text(ctx, "Bu dönem eklenen: ${Fmt.bytes(extra)}", 14f, color = Ui.accentText(ctx)))
            addView(Ui.choiceRow(ctx, listOf("+1 GB" to 1, "+2 GB" to 2, "+3 GB" to 3, "+5 GB" to 5, "+10 GB" to 10, "+20 GB" to 20), -1) { gb ->
                val app = applicationContext
                Thread {
                    PlanState.addExtra(app, Fmt.mbToBytes(gb * 1024))
                    runOnUiThread {
                        toast("$gb GB eklendi")
                        recreate()
                    }
                }.start()
            })
            if (extra > 0) {
                addView(Ui.button(ctx, "Ek paketi sıfırla") {
                    prefs.edit().putLong(Prefs.PLAN_EXTRA, 0L).apply()
                    refreshPlan()
                    recreate()
                })
            }
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
            addView(Ui.switch(ctx, "Dönem bitince özet bildirimi", prefs.getBoolean(Prefs.PLAN_SUMMARY_ON, true)) { put(Prefs.PLAN_SUMMARY_ON, it) })
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
                startForegroundService(Intent(ctx, SpeedService::class.java).setAction(SpeedService.ACTION_RESET_ROLL)
                    .putExtra(SpeedService.EXTRA_FROM_APP, true))
                resetText.text = "Az önce sıfırlandı."
                Ui.undoBar(this@SettingsActivity, "Kayan sayaç sıfırlandı") {
                    startForegroundService(Intent(ctx, SpeedService::class.java).setAction(SpeedService.ACTION_UNDO_ROLL))
                    resetText.postDelayed({ update() }, 500)
                }
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
                startForegroundService(Intent(ctx, SpeedService::class.java).setAction(SpeedService.ACTION_RESET)
                    .putExtra(SpeedService.EXTRA_FROM_APP, true))
                Ui.undoBar(this@SettingsActivity, "Dönem sayacı sıfırlandı") {
                    startForegroundService(Intent(ctx, SpeedService::class.java).setAction(SpeedService.ACTION_UNDO_RESET))
                }
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
        root.addView(Ui.card(ctx, "Sessiz saatler", "🌙").apply {
            addView(Ui.hint(ctx, "Bu saatlerde uyarılar sessiz gelir: ses, titreşim ve açılır pencere olmaz, sabah bildirim panelinde bekler. Hız bildirimi etkilenmez."))
            addView(Ui.switch(ctx, "Sessiz saatleri kullan", prefs.getBoolean(Prefs.QUIET_ON, false)) { put(Prefs.QUIET_ON, it) })
            addView(Ui.seekRow(ctx, "Başlangıç", 0, 23, prefs.getInt(Prefs.QUIET_START, 23), ":00") { put(Prefs.QUIET_START, it) })
            addView(Ui.seekRow(ctx, "Bitiş", 0, 23, prefs.getInt(Prefs.QUIET_END, 8), ":00") { put(Prefs.QUIET_END, it) })
        })
        root.addView(Ui.card(ctx, "Otomatik bildirimler", "📬").apply {
            addView(Ui.switch(ctx, "Haftalık özet (pazartesi sabahı)", prefs.getBoolean(Prefs.WEEKLY_ON, true)) { put(Prefs.WEEKLY_ON, it) })
            addView(Ui.switch(ctx, "Olağandışı kullanım uyarısı", prefs.getBoolean(Prefs.ANOMALY_ON, true)) { put(Prefs.ANOMALY_ON, it) })
            addView(Ui.switch(ctx, "Roaming (yurt dışı) uyarısı", prefs.getBoolean(Prefs.ROAM_ON, true)) { put(Prefs.ROAM_ON, it) })
            addView(Ui.switch(ctx, "Yavaş mobil internet uyarısı", prefs.getBoolean(Prefs.SLOW_ON, false)) { put(Prefs.SLOW_ON, it) })
            addView(Ui.choiceRow(ctx, listOf("0,5 Mbps" to 500, "1 Mbps" to 1000, "2 Mbps" to 2000, "5 Mbps" to 5000),
                prefs.getInt(Prefs.SLOW_KBPS, 1000)) { put(Prefs.SLOW_KBPS, it) })
            addView(Ui.hint(ctx, "Ekran açıkken mobil veri kullanılırken 2 dakika boyunca hız bu değerin altında kalırsa uyarır (en fazla saatte bir). Boşta beklerken uyarmaz."))
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
            addView(Ui.switch(ctx, "Bırakınca kenara yapışsın", prefs.getBoolean(Prefs.OV_SNAP, true)) { put(Prefs.OV_SNAP, it) })
            addView(Ui.switch(ctx, "Yerini kilitle", prefs.getBoolean(Prefs.OV_LOCK, false)) { put(Prefs.OV_LOCK, it) })
            addView(Ui.switch(ctx, "Tam ekran video ve oyunlarda gizle", prefs.getBoolean(Prefs.OV_FULLSCREEN_HIDE, true)) { put(Prefs.OV_FULLSCREEN_HIDE, it) })
            addView(Ui.hint(ctx, "Sürükleyip taşı. Tek dokunuş: bugünkü kullanım ve paketi gösteren küçük panel. Çift dokunuş: gizler; bildirimdeki \"Pencereyi göster\" ile geri açılır. Yazı rengi Görünüm sayfasından seçilir."))
        })
        root.addView(Ui.card(ctx, "Oyun modu", "🎮").apply {
            addView(Ui.hint(ctx, "Yüzen pencere sadece seçtiğin uygulamalar (oyunlar, video, hız testi…) açıkken görünür, diğer zamanlarda kendiliğinden gizlenir."))
            addView(Ui.switch(ctx, "Sadece seçili uygulamalarda göster", prefs.getBoolean(Prefs.GAME_ON, false)) {
                put(Prefs.GAME_ON, it)
                if (it && !NetStats.hasAccess(ctx)) {
                    toast("Bunun için \"Kullanım erişimi\" izni gerekiyor")
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            })
            val count = ForegroundApp.parse(prefs.getString(Prefs.GAME_APPS, "")).size
            gameButton = Ui.button(ctx, if (count > 0) "Uygulamalar ($count seçili)" else "Uygulama seç") { pickGameApps() }
            addView(gameButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        })
    }

    private var gameButton: TextView? = null

    private fun pickGameApps() {
        val ctx = this
        toast("Uygulamalar yükleniyor…")
        Thread {
            val pm = packageManager
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launch, 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != packageName }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase(Locale("tr", "TR")) }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val selected = ForegroundApp.parse(prefs.getString(Prefs.GAME_APPS, "")).toMutableSet()
                val checked = BooleanArray(apps.size) { apps[it].first in selected }
                android.app.AlertDialog.Builder(ctx)
                    .setTitle("Oyun modu uygulamaları")
                    .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, on ->
                        if (on) selected += apps[i].first else selected -= apps[i].first
                    }
                    .setPositiveButton("Kaydet") { _, _ ->
                        prefs.edit().putString(Prefs.GAME_APPS, selected.joinToString(",")).apply()
                        gameButton?.text = if (selected.isNotEmpty()) "Uygulamalar (${selected.size} seçili)" else "Uygulama seç"
                    }
                    .setNegativeButton("Vazgeç", null)
                    .show()
            }
        }.start()
    }

    // ---------- Görünüm ----------

    private fun look(root: LinearLayout) {
        val ctx = this
        root.addView(Ui.card(ctx, "Tema", "🌗").apply {
            val current = Themes.current(ctx)
            val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            (0 until 4).chunked(2).forEach { pair ->
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEach { t ->
                    row.addView(themeTile(t, t == current), LinearLayout.LayoutParams(0, dp(112), 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    })
                }
                grid.addView(row)
            }
            addView(grid)
            if (current != Themes.SYSTEM) {
                addView(Ui.switch(ctx, "Telefon açık temadayken de karanlık kalsın", prefs.getBoolean(Prefs.FORCE_DARK, true)) {
                    put(Prefs.FORCE_DARK, it)
                    applyTheme()
                })
                addView(Ui.hint(ctx, "Kapalıysa telefonun açık temasında bu temanın açık hâli kullanılır."))
            }
        })
        root.addView(Ui.card(ctx, "Renkler ve birimler", "🎨").apply {
            if (Build.VERSION.SDK_INT >= 31 && Themes.current(ctx) == Themes.SYSTEM) {
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
            addView(Ui.label(ctx, "Widget zemini"))
            addView(Ui.choiceRow(ctx, listOf("Koyu" to 0, "Yarı saydam" to 1, "Saydam" to 2), prefs.getInt(Prefs.WIDGET_BG, 1)) {
                put(Prefs.WIDGET_BG, it)
                try { Widgets.updateAll(ctx) } catch (e: Exception) { }
            })
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
        root.addView(Ui.card(ctx, "Yedek ve dışa aktarma", "💾").apply {
            addView(Ui.hint(ctx, "Tüm ayarlarını ve günlük istatistiklerini tek bir dosyaya kaydet; telefon değiştirince ya da uygulamayı yeniden kurunca geri yükle. " +
                "CSV dosyası Excel / Google E-Tablolar'da açılır."))
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            row.addView(Ui.button(ctx, "Yedek al", filled = true) {
                pick(Intent.ACTION_CREATE_DOCUMENT, "application/json", "hiz-gostergesi-yedek-${StatsStore.key()}.json", REQ_BACKUP)
            }, btnParams())
            row.addView(Ui.button(ctx, "Yedekten geri yükle") {
                pick(Intent.ACTION_OPEN_DOCUMENT, "*/*", null, REQ_RESTORE)
            }, btnParams())
            row.addView(Ui.button(ctx, "Günlük kullanımı CSV olarak dışa aktar") {
                pick(Intent.ACTION_CREATE_DOCUMENT, "text/csv", "hiz-gostergesi-${StatsStore.key()}.csv", REQ_CSV)
            }, btnParams())
            addView(row)
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

    private fun btnParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(8) }

    private fun pick(action: String, type: String, name: String?, req: Int) {
        val i = Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType(type)
        if (name != null) i.putExtra(Intent.EXTRA_TITLE, name)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, req)
        } catch (e: Exception) {
            toast("Dosya seçici açılamadı")
        }
    }

    @Deprecated("Platform Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        val ctx = applicationContext
        Thread {
            val msg = try {
                when (requestCode) {
                    REQ_BACKUP -> {
                        Backup.write(ctx, uri, Backup.export(ctx))
                        "Yedek kaydedildi"
                    }
                    REQ_CSV -> {
                        StatsStore.save(ctx, force = true)
                        val text = synchronized(StatsStore) { Backup.csv(TreeMap(StatsStore.all(ctx)), TreeMap(StatsStore.archivedMonths(ctx))) }
                        Backup.write(ctx, uri, text)
                        "CSV kaydedildi"
                    }
                    REQ_RESTORE -> {
                        val (n, d) = Backup.restore(ctx, Backup.read(ctx, uri))
                        Ui.init(ctx)
                        "Geri yüklendi: $n ayar, $d gün"
                    }
                    else -> return@Thread
                }
            } catch (e: Exception) {
                "Olmadı: " + (e.message ?: "dosya okunamadı")
            }
            runOnUiThread {
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                if (requestCode == REQ_RESTORE && !isFinishing) recreate()
            }
        }.start()
    }

    private fun applyTheme() {
        Ui.init(this)
        recreate()
    }

    /** Temanın küçük önizlemesi: zemin, kart, vurgu çizgisi ve adı. */
    private fun themeTile(t: Int, selected: Boolean): View {
        val p = Themes.preview(t) ?: Palette(
            0xFF0E1014.toInt(), 0xFF1A1D24.toInt(), 0xFFF1F3F7.toInt(), 0xFF9AA1AD.toInt(), 0xFF2C313B.toInt(),
            Ui.ACCENT, Ui.ACCENT_2, 0xFF9DBBFF.toInt(), Color.WHITE
        )
        val r = dp(18).toFloat()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(10))
            background = Ui.ripple(Ui.rounded(p.bg, r, if (selected) dp(2) else dp(1), if (selected) Ui.ACCENT else Ui.outline(this@SettingsActivity)))
            isClickable = true
            contentDescription = Themes.NAMES[t]
            setOnClickListener {
                if (selected) return@setOnClickListener
                it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                put(Prefs.THEME, t)
                applyTheme()
            }
            // mini kart: gösterge yayı ve iki satır
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = Ui.rounded(p.surface, dp(10).toFloat())
                addView(View(context).apply { background = Ui.rounded(p.accent, dp(3).toFloat()) },
                    LinearLayout.LayoutParams(dp(46), dp(6)))
                addView(View(context).apply { background = Ui.rounded(p.secondary and 0x66FFFFFF, dp(3).toFloat()) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(5)).apply { topMargin = dp(7) })
                addView(View(context).apply { background = Ui.rounded(p.secondary and 0x66FFFFFF, dp(3).toFloat()) },
                    LinearLayout.LayoutParams(dp(60), dp(5)).apply { topMargin = dp(5) })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(TextView(context).apply {
                text = (if (selected) "✓ " else "") + Themes.NAMES[t]
                textSize = 13f
                typeface = Ui.MEDIUM
                setTextColor(p.primary)
                setPadding(dp(2), dp(8), 0, 0)
            })
        }
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
