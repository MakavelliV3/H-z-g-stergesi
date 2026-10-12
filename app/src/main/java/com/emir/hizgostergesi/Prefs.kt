package com.emir.hizgostergesi

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import java.util.Locale

/** Ayar anahtarları: ayar ekranı yazar, servis her turda okur. */
object Prefs {
    private const val NAME = "ayarlar"

    // Sayaç
    const val HOURS = "periyot_saat"
    const val MOBILE_ONLY = "sadece_mobil"
    const val WINDOW_START = "donem_baslangic"
    const val USED_MOBILE = "donem_mobil"
    const val USED_WIFI = "donem_wifi"

    // Veri sınırı
    const val QUOTA_MB = "sinir_mb"
    const val QUOTA_WARNED = "sinir_uyarilan_donem"

    // Görünüm
    const val COLOR = "renk"
    const val UNIT_BITS = "birim_mbps"

    // Yüzen pencere
    const val OVERLAY = "yuzen_pencere"
    const val OVERLAY_X = "pencere_x"
    const val OVERLAY_Y = "pencere_y"
    const val OVERLAY_SIZE = "pencere_boyut"
    const val OVERLAY_ALPHA = "pencere_koyuluk"

    // Durum çubuğu simgesi
    const val SB_MODE = "cubuk_icerik"
    const val SB_SIZE = "cubuk_boyut"
    const val SB_UNIT = "cubuk_birim"
    const val SB_BOLD = "cubuk_kalin"
    const val SB_HIDE_IDLE = "cubuk_bosta_gizle"

    // Bildirim paneli
    const val N_LINE_SPEED = "bildirim_hiz"
    const val HOUR_BUCKETS = "kayan_dakikalar"

    // Kayan "son X saat" sayacı
    const val ROLL_HOURS = "kayan_saat"
    const val ROLL_SCOPE = "kayan_kapsam"       // 0: tümü, 1: mobil, 2: Wi-Fi
    const val ROLL_POS = "kayan_konum"          // 0: ayrı satır, 1: hız satırında, 2: gösterme
    const val ROLL_DETAIL = "kayan_ayrinti"
    const val ROLL_OVERLAY = "kayan_pencere"
    const val ROLL_WIDGET = "kayan_widget"
    const val ROLL_RESET_AT = "kayan_sifirlama"
    const val N_BTN_ROLL_RESET = "bildirim_kayan_sifirla_dugmesi"
    val ROLL_OPTIONS = intArrayOf(1, 2, 3, 6, 12, 24)
    const val N_LINE_PERIOD = "bildirim_donem"
    const val N_LINE_TODAY = "bildirim_bugun"
    const val N_LINE_PEAK = "bildirim_rekor"
    const val N_LINE_MONTH = "bildirim_ay"
    const val N_SIZE = "bildirim_boyut"
    const val N_ARROWS = "bildirim_ok"
    const val N_MONO = "bildirim_sabit_genislik"
    const val N_BG = "bildirim_arka_plan"
    const val N_BTN_OVERLAY = "bildirim_pencere_dugmesi"
    const val N_BTN_RESET = "bildirim_sifirla_dugmesi"
    const val N_LOCKSCREEN = "bildirim_kilit_ekrani"

    const val N_SHOW_CONN = "baglanti_turu_goster"

    // Uygulama bazlı sayım ve sınırlar
    const val APPS_RESET_AT = "uygulama_sifirlama"
    const val APP_LIMITS = "uygulama_sinirlari"
    const val APP_LIMIT_SENT = "uygulama_sinir_gonderilen"

    // Genel
    const val DYNAMIC_COLOR = "material_you"
    const val UNIT_BASE = "birim_tabani"   // 1000 ya da 1024
    const val WIDGET_SEC = "widget_aralik_sn"
    const val ONBOARDED = "kurulum_tamam"
    const val HEALTH_BATTERY_HIDDEN = "pil_uyarisi_gizli"

    // Analiz bildirimleri
    const val WEEKLY_ON = "haftalik_ozet"
    const val WEEKLY_SENT = "haftalik_gonderilen"
    const val ANOMALY_ON = "anormal_uyari"
    const val ANOMALY_SENT = "anormal_gonderilen"

    // Pil
    const val INTERVAL_SEC = "aralik_sn"
    const val SLOW_WHEN_OFF = "ekran_kapali_yavas"

    /** 0 = otomatik: bildirimde sistemin yazı rengi, koyu zeminli pencere ve widget'ta beyaz. */
    const val COLOR_AUTO = 0
    const val DEFAULT_COLOR = COLOR_AUTO

    /** Koyu zemin üstünde kullanılacak yazı rengi (otomatikse beyaz). */
    fun onDark(color: Int) = if (color == COLOR_AUTO) Color.WHITE else color
    val HOUR_OPTIONS = intArrayOf(1, 2, 3, 6, 12, 24)

    fun get(c: Context): SharedPreferences = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Uygulama başına günlük sınırlar (uid -> MB). "uid=mb;uid=mb" olarak saklanır. */
    fun appLimits(c: Context): Map<Int, Int> = parseLimits(get(c).getString(APP_LIMITS, ""))

    /** "uid=mb;uid=mb" biçimini çözer; bozuk parçaları atlar. */
    fun parseLimits(s: String?): Map<Int, Int> =
        (s ?: "").split(";").mapNotNull {
            val parts = it.split("=")
            val uid = parts.getOrNull(0)?.trim()?.toIntOrNull()
            val mb = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (uid != null && mb != null && mb > 0) uid to mb else null
        }.toMap()

    fun setAppLimit(c: Context, uid: Int, mb: Int) {
        val map = appLimits(c).toMutableMap()
        if (mb > 0) map[uid] = mb else map.remove(uid)
        get(c).edit().putString(APP_LIMITS, map.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }
}

/** Sayı biçimlendirme yardımcıları. */
object Fmt {
    private val TR = Locale("tr", "TR")

    /**
     * Birim tabanı: 1000 (Android'in kendi veri ekranı ve operatörler gibi) ya da 1024.
     * Uygulama açılışında ve ayar değişince Prefs'ten güncellenir.
     */
    @Volatile
    var base = 1000L

    private fun f(pattern: String, v: Double) = String.format(TR, pattern, v)

    /** Kota/sınır seçenekleri MB cinsinden saklanır; bayta çevirir. */
    fun mbToBytes(mb: Int): Long =
        if (mb >= 1024 && mb % 1024 == 0) (mb / 1024) * base * base * base else mb * base * base

    fun bytes(b: Long): String {
        val k = base.toDouble()
        return when {
            b >= base * base * base -> {
                val g = b / (k * k * k)
                f(if (g >= 10) "%.1f GB" else "%.2f GB", g)
            }
            b >= base * base -> {
                val m = b / (k * k)
                f(if (m >= 100) "%.0f MB" else "%.1f MB", m)
            }
            b >= base -> "${b / base} KB"
            else -> "${b.coerceAtLeast(0)} B"
        }
    }

    fun mb(mb: Int): String = when {
        mb >= 1000 && mb % 1024 == 0 -> "${mb / 1024} GB"
        mb >= 1000 && mb % 1000 == 0 -> "${mb / 1000} GB"
        mb >= 1000 -> f("%.1f GB", mb / 1024.0)
        else -> "$mb MB"
    }

    /** Hızı "sayı" + "birim" olarak ayırır. bits=true ise Mbps/Kbps kullanır (bit hızı hep 1000 tabanlıdır). */
    fun speedParts(bytesPerSec: Long, bits: Boolean): Pair<String, String> {
        val v = bytesPerSec.coerceAtLeast(0)
        if (bits) {
            val b = v * 8
            return when {
                b >= 1_000_000_000 -> f(if (b >= 10_000_000_000) "%.0f" else "%.1f", b / 1e9) to "Gbps"
                b >= 1_000_000 -> f(if (b >= 10_000_000) "%.0f" else "%.1f", b / 1e6) to "Mbps"
                else -> (b / 1000).toString() to "Kbps"
            }
        }
        val k = base.toDouble()
        return if (v >= base * base) {
            val m = v / (k * k)
            f(if (m >= 10) "%.0f" else "%.1f", m) to "MB/s"
        } else {
            (v / base).toString() to "KB/s"
        }
    }

    fun speed(b: Long, bits: Boolean) = speedParts(b, bits).let { "${it.first} ${it.second}" }

    /** Kısa biçim: "4,2M", "350K". */
    fun compact(b: Long, bits: Boolean) = speedParts(b, bits).let { it.first + it.second.first() }

    fun remaining(ms: Long): String {
        val totalMin = (ms / 60000).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "$h sa $m dk" else "$m dk"
    }
}
