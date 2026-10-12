package com.emir.hizgostergesi

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/** Ayar anahtarları: ayar ekranı yazar, servis her saniye okur. */
object Prefs {
    private const val NAME = "ayarlar"

    const val HOURS = "periyot_saat"
    const val COLOR = "renk"
    const val OVERLAY = "yuzen_pencere"
    const val OVERLAY_X = "pencere_x"
    const val OVERLAY_Y = "pencere_y"
    const val WINDOW_START = "donem_baslangic"
    const val USED_RX = "kullanilan_indirme"
    const val USED_TX = "kullanilan_yukleme"

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

    /** Hızı "sayı" + "birim" olarak ayırır (durum çubuğu simgesi için). */
    fun speedParts(bytesPerSec: Long): Pair<String, String> =
        if (bytesPerSec >= 1024L * 1024L) {
            val mb = bytesPerSec / 1048576.0
            (if (mb >= 10) "%.0f".format(mb) else "%.1f".format(mb)) to "MB/s"
        } else {
            (bytesPerSec / 1024).toString() to "KB/s"
        }

    fun speed(b: Long) = speedParts(b).let { "${it.first} ${it.second}" }

    fun remaining(ms: Long): String {
        val totalMin = (ms / 60000).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "$h sa $m dk" else "$m dk"
    }
}
