package com.emir.hizgostergesi

import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import java.util.Locale

/** Ayarlar sekmesi: kategoriler (her biri kendi sayfasında), anlık özetleri ve ayar arama. */
class SettingsScreen(a: MainActivity) : Screen(a) {

    private lateinit var categories: LinearLayout
    private lateinit var results: LinearLayout
    private val tr = Locale("tr", "TR")

    /** Aranabilir ayarlar: (görünen ad, kategori). */
    private val index = listOf(
        "Durum çubuğu simgesi" to SettingsActivity.CAT_STATUS,
        "İndirme / yükleme / toplam gösterimi" to SettingsActivity.CAT_STATUS,
        "Simge boyutu" to SettingsActivity.CAT_STATUS,
        "Kalın yazı" to SettingsActivity.CAT_STATUS,
        "Boştayken gizle" to SettingsActivity.CAT_STATUS,
        "Bildirim satırları" to SettingsActivity.CAT_NOTIF,
        "Bağlantı türünü göster (5G, Wi-Fi)" to SettingsActivity.CAT_NOTIF,
        "Bildirim yazı boyutu" to SettingsActivity.CAT_NOTIF,
        "Ok stili" to SettingsActivity.CAT_NOTIF,
        "Sabit genişlikli yazı" to SettingsActivity.CAT_NOTIF,
        "Bildirim arka plan rengi" to SettingsActivity.CAT_NOTIF,
        "Bildirim düğmeleri" to SettingsActivity.CAT_NOTIF,
        "Kilit ekranında gizle" to SettingsActivity.CAT_NOTIF,
        "Paket takibi (tarife, kota)" to SettingsActivity.CAT_PLAN,
        "Paket yenilenme günü" to SettingsActivity.CAT_PLAN,
        "Gece paketi" to SettingsActivity.CAT_PLAN,
        "Paket uyarısı (%80, %100)" to SettingsActivity.CAT_PLAN,
        "Son X saat (kayan sayaç)" to SettingsActivity.CAT_COUNTERS,
        "Kayan sayacı sıfırla" to SettingsActivity.CAT_COUNTERS,
        "Dönem sayacı" to SettingsActivity.CAT_COUNTERS,
        "Dönem sayacını sıfırla" to SettingsActivity.CAT_COUNTERS,
        "Sadece mobil veriyi say" to SettingsActivity.CAT_COUNTERS,
        "Veri sınırı uyarısı (kota)" to SettingsActivity.CAT_ALERTS,
        "Haftalık özet" to SettingsActivity.CAT_ALERTS,
        "Olağandışı kullanım uyarısı" to SettingsActivity.CAT_ALERTS,
        "Yavaş internet uyarısı" to SettingsActivity.CAT_ALERTS,
        "Yüzen pencere" to SettingsActivity.CAT_OVERLAY,
        "Oyun modu (seçili uygulamalarda göster)" to SettingsActivity.CAT_OVERLAY,
        "Yüzen pencere yazı boyutu" to SettingsActivity.CAT_OVERLAY,
        "Yüzen pencere koyuluğu" to SettingsActivity.CAT_OVERLAY,
        "Material You renkleri" to SettingsActivity.CAT_LOOK,
        "Hız birimi (MB/s, Mbps)" to SettingsActivity.CAT_LOOK,
        "Birim tabanı (1000 / 1024)" to SettingsActivity.CAT_LOOK,
        "Yazı rengi" to SettingsActivity.CAT_LOOK,
        "Güncelleme aralığı" to SettingsActivity.CAT_BATTERY,
        "Ekran kapalıyken yavaşlat" to SettingsActivity.CAT_BATTERY,
        "Widget güncelleme sıklığı" to SettingsActivity.CAT_BATTERY,
        "Kurulum sihirbazı ve izinler" to SettingsActivity.CAT_ABOUT,
        "Yedek al / geri yükle" to SettingsActivity.CAT_ABOUT,
        "CSV olarak dışa aktar (Excel)" to SettingsActivity.CAT_ABOUT,
        "Sürüm ve hakkında" to SettingsActivity.CAT_ABOUT
    )

    private val icons = mapOf(
        SettingsActivity.CAT_STATUS to "📶", SettingsActivity.CAT_NOTIF to "🔔", SettingsActivity.CAT_PLAN to "📦", SettingsActivity.CAT_COUNTERS to "⏱",
        SettingsActivity.CAT_ALERTS to "🚨", SettingsActivity.CAT_OVERLAY to "🪟", SettingsActivity.CAT_LOOK to "🎨",
        SettingsActivity.CAT_BATTERY to "🔋", SettingsActivity.CAT_ABOUT to "🧭"
    )

    override fun build() {
        root.addView(Ui.screenTitle(a, "Ayarlar"))
        root.addView(Ui.searchField(a, "Ayar ara") { search(it) })
        results = Ui.card(a).apply { visibility = android.view.View.GONE }
        root.addView(results)
        categories = Ui.card(a)
        root.addView(categories)
        root.addView(Ui.hint(a, "🔒 İnternet izni yok · Tüm veriler sadece bu telefonda kalır").apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        })
    }

    override fun onShow() {
        renderCategories()
    }

    private fun open(cat: String) {
        a.startActivity(Intent(a, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, cat))
    }

    /** Kategorinin şu anki ayarlarından kısa özet. */
    private fun summary(cat: String): String {
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        return when (cat) {
            SettingsActivity.CAT_STATUS -> listOf(
                when (prefs.getInt(Prefs.SB_MODE, 0)) { 1 -> "Yükleme"; 2 -> "Toplam"; 3 -> "İkisi alt alta"; else -> "İndirme" },
                when (prefs.getInt(Prefs.SB_SIZE, 1)) { 0 -> "normal"; 2 -> "en büyük"; else -> "büyük" },
                when (prefs.getInt(Prefs.SB_UNIT, 0)) { 1 -> "kısa birim"; 2 -> "birimsiz"; else -> "tam birim" }
            ).joinToString(" · ")
            SettingsActivity.CAT_NOTIF -> {
                val lines = listOf(Prefs.N_LINE_SPEED to true, Prefs.N_LINE_PERIOD to false, Prefs.N_LINE_TODAY to true,
                    Prefs.N_LINE_PEAK to false, Prefs.N_LINE_MONTH to false).count { prefs.getBoolean(it.first, it.second) }
                "$lines satır · ${when (prefs.getInt(Prefs.N_SIZE, 1)) { 0 -> "küçük"; 2 -> "büyük"; else -> "normal" }} yazı"
            }
            SettingsActivity.CAT_PLAN -> if (prefs.getBoolean(Prefs.PLAN_ON, false))
                "${Fmt.mb(prefs.getInt(Prefs.PLAN_MB, 0))} · her ayın ${prefs.getInt(Prefs.PLAN_DAY, 1)}. günü yenilenir" else "Kapalı · kalan GB ve günlük payını gör"
            SettingsActivity.CAT_COUNTERS -> "Son ${prefs.getInt(Prefs.ROLL_HOURS, 1)} saat (kayan) · dönem ${prefs.getInt(Prefs.HOURS, 1)} saat"
            SettingsActivity.CAT_ALERTS -> {
                val q = prefs.getInt(Prefs.QUOTA_MB, 0)
                (if (q > 0) "Sınır ${Fmt.mb(q)}" else "Sınır kapalı") +
                    (if (prefs.getBoolean(Prefs.WEEKLY_ON, true)) " · haftalık özet" else "") +
                    (if (prefs.getBoolean(Prefs.ANOMALY_ON, true)) " · olağandışı uyarı" else "")
            }
            SettingsActivity.CAT_OVERLAY -> if (prefs.getBoolean(Prefs.OVERLAY, false)) "Açık · yazı ${prefs.getInt(Prefs.OVERLAY_SIZE, 12)}" else "Kapalı"
            SettingsActivity.CAT_LOOK -> "${if (prefs.getBoolean(Prefs.DYNAMIC_COLOR, true)) "Material You" else "Klasik mavi"} · ${if (bits) "Mbps" else "MB/s"} · ${prefs.getInt(Prefs.UNIT_BASE, 1000)} tabanı"
            SettingsActivity.CAT_BATTERY -> "${prefs.getInt(Prefs.INTERVAL_SEC, 1)} sn aralık · widget ${prefs.getInt(Prefs.WIDGET_SEC, 15)} sn"
            else -> "İzinler, yedek, CSV, sürüm"
        }
    }

    private fun renderCategories() {
        categories.removeAllViews()
        SettingsActivity.TITLES.keys.forEach { cat ->
            categories.addView(Ui.navRow(a, icons[cat] ?: "⚙", SettingsActivity.TITLES[cat] ?: cat, summary(cat)) { open(cat) })
        }
    }

    private fun search(q: String) {
        results.removeAllViews()
        val query = q.trim().lowercase(tr)
        if (query.isEmpty()) {
            results.visibility = android.view.View.GONE
            categories.visibility = android.view.View.VISIBLE
            return
        }
        val hits = index.filter { it.first.lowercase(tr).contains(query) || (SettingsActivity.TITLES[it.second] ?: "").lowercase(tr).contains(query) }
        results.visibility = android.view.View.VISIBLE
        categories.visibility = android.view.View.GONE
        if (hits.isEmpty()) {
            results.addView(Ui.emptyState(a, "🔍", "Sonuç yok", "\"$q\" ile eşleşen bir ayar bulunamadı."))
            return
        }
        hits.forEach { (name, cat) ->
            results.addView(Ui.navRow(a, icons[cat] ?: "⚙", name, SettingsActivity.TITLES[cat] ?: "") { open(cat) })
        }
    }
}
