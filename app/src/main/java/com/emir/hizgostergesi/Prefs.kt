package com.emir.hizgostergesi

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

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

    // Analiz bildirimleri
    const val WEEKLY_ON = "haftalik_ozet"
    const val WEEKLY_SENT = "haftalik_gonderilen"
    const val ANOMALY_ON = "anormal_uyari"
    const val ANOMALY_SENT = "anormal_gonderilen"

    // Pil
    const val INTERVAL_SEC = "aralik_sn"
    const val SLOW_WHEN_OFF = "ekran_kapali_yavas"

    const val DEFAULT_COLOR = Color.WHITE
    val HOUR_OPTIONS = intArrayOf(1, 2, 3, 6, 12, 24)

    fun get(c: Context): SharedPreferences = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}

/** Sayı biçimlendirme yardımcıları. */
object Fmt {
    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
        b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
        b >= 1L shl 10 -> "${b / 1024} KB"
        else -> "$b B"
    }

    fun mb(mb: Int): String = when {
        mb >= 1024 && mb % 1024 == 0 -> "${mb / 1024} GB"
        mb >= 1024 -> "%.1f GB".format(mb / 1024.0)
        else -> "$mb MB"
    }

    /** Hızı "sayı" + "birim" olarak ayırır. bits=true ise Mbps/Kbps kullanır. */
    fun speedParts(bytesPerSec: Long, bits: Boolean): Pair<String, String> {
        if (bits) {
            val b = bytesPerSec * 8
            return if (b >= 1_000_000) {
                val m = b / 1_000_000.0
                (if (m >= 10) "%.0f".format(m) else "%.1f".format(m)) to "Mbps"
            } else {
                (b / 1000).toString() to "Kbps"
            }
        }
        return if (bytesPerSec >= 1024L * 1024L) {
            val m = bytesPerSec / 1048576.0
            (if (m >= 10) "%.0f".format(m) else "%.1f".format(m)) to "MB/s"
        } else {
            (bytesPerSec / 1024).toString() to "KB/s"
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
