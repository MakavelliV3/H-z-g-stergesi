package com.emir.hizgostergesi

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Process

/** Bir uygulamanın belirli bir aralıktaki kullanımı. */
class AppUsage(val uid: Int) {
    var mobileRx = 0L
    var mobileTx = 0L
    var wifiRx = 0L
    var wifiTx = 0L
    var foreground = 0L
    var background = 0L
    var name = ""
    var category = "Diğer"
    var icon: Drawable? = null

    val mobile get() = mobileRx + mobileTx
    val wifi get() = wifiRx + wifiTx
    val total get() = mobile + wifi
    val rx get() = mobileRx + wifiRx
    val tx get() = mobileTx + wifiTx

    /** Aldığından çok daha fazlasını gönderen uygulama. */
    val heavyUploader get() = tx >= 10L * 1048576 && tx > rx
}

/**
 * Android'in kendi ağ kayıtlarını (NetworkStatsManager) okur.
 * "Kullanım erişimi" izni gerekir; bu kayıtlar uygulama kurulmadan önceki günleri de kapsar.
 */
@Suppress("DEPRECATION")
object NetStats {
    private const val MOBILE = ConnectivityManager.TYPE_MOBILE
    private const val WIFI = ConnectivityManager.TYPE_WIFI

    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    /** Uygulama bazında kullanım, çoktan aza sıralı. */
    fun queryApps(c: Context, start: Long, end: Long, withNames: Boolean = true): List<AppUsage> {
        val nsm = c.getSystemService(NetworkStatsManager::class.java)
        val map = HashMap<Int, AppUsage>()
        for (type in intArrayOf(MOBILE, WIFI)) {
            try {
                val stats = nsm.querySummary(type, null, start, end)
                val b = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    val a = map.getOrPut(b.uid) { AppUsage(b.uid) }
                    if (type == MOBILE) {
                        a.mobileRx += b.rxBytes
                        a.mobileTx += b.txBytes
                    } else {
                        a.wifiRx += b.rxBytes
                        a.wifiTx += b.txBytes
                    }
                    val bytes = b.rxBytes + b.txBytes
                    if (b.state == NetworkStats.Bucket.STATE_FOREGROUND) a.foreground += bytes else a.background += bytes
                }
                stats.close()
            } catch (e: Exception) {
                // bu ağ türü için kayıt yok
            }
        }
        val list = map.values.filter { it.total > 0 }.sortedByDescending { it.total }
        if (withNames) list.forEach { describe(c, it) }
        return list
    }

    /** Hotspot (internet paylaşımı) ile mobil veriden giden toplam. */
    fun tethering(c: Context, start: Long, end: Long): Long {
        val nsm = c.getSystemService(NetworkStatsManager::class.java)
        var sum = 0L
        try {
            val stats = nsm.querySummary(MOBILE, null, start, end)
            val b = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(b)
                if (b.uid == NetworkStats.Bucket.UID_TETHERING) sum += b.rxBytes + b.txBytes
            }
            stats.close()
        } catch (e: Exception) {
        }
        return sum
    }

    /** Cihazın toplam mobil ve Wi-Fi kullanımı. */
    fun deviceTotal(c: Context, start: Long, end: Long): Pair<Long, Long> {
        val nsm = c.getSystemService(NetworkStatsManager::class.java)
        fun one(type: Int): Long = try {
            val b = nsm.querySummaryForDevice(type, null, start, end)
            b.rxBytes + b.txBytes
        } catch (e: Exception) {
            0L
        }
        return one(MOBILE) to one(WIFI)
    }

    /** Tek bir uygulamanın gün gün kullanımı: her gün için [mobil, wifi]. dayStarts gün başlangıçlarıdır. */
    fun appDaily(c: Context, uid: Int, dayStarts: LongArray, end: Long): Array<LongArray> {
        val nsm = c.getSystemService(NetworkStatsManager::class.java)
        val out = Array(dayStarts.size) { LongArray(2) }
        if (dayStarts.isEmpty()) return out
        for ((slot, type) in listOf(0 to MOBILE, 1 to WIFI)) {
            try {
                val stats = nsm.queryDetailsForUid(type, null, dayStarts.first(), end, uid)
                val b = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    val mid = (b.startTimeStamp + b.endTimeStamp) / 2
                    var idx = dayStarts.indexOfLast { it <= mid }
                    if (idx < 0) idx = 0
                    out[idx][slot] += b.rxBytes + b.txBytes
                }
                stats.close()
            } catch (e: Exception) {
                // kayıt yok
            }
        }
        return out
    }

    /** Uygulamanın adını, simgesini ve kategorisini doldurur. */
    fun describe(c: Context, a: AppUsage) {
        when (a.uid) {
            NetworkStats.Bucket.UID_REMOVED -> {
                a.name = "Kaldırılmış uygulamalar"; a.category = "Sistem"; return
            }
            NetworkStats.Bucket.UID_TETHERING -> {
                a.name = "Hotspot / internet paylaşımı"; a.category = "Paylaşım"; return
            }
            Process.SYSTEM_UID -> {
                a.name = "Android sistemi"; a.category = "Sistem"; return
            }
            0 -> {
                a.name = "Sistem çekirdeği"; a.category = "Sistem"; return
            }
        }
        val pm = c.packageManager
        val pkgs = pm.getPackagesForUid(a.uid)
        if (pkgs.isNullOrEmpty()) {
            a.name = "Bilinmeyen (uid ${a.uid})"
            a.category = "Sistem"
            return
        }
        try {
            val ai = pm.getApplicationInfo(pkgs[0], 0)
            val label = ai.loadLabel(pm).toString()
            a.name = if (pkgs.size > 1) "$label +${pkgs.size - 1}" else label
            a.icon = ai.loadIcon(pm)
            a.category = category(pkgs[0], ai)
        } catch (e: Exception) {
            a.name = pkgs[0]
        }
    }

    private val known = listOf(
        "com.instagram" to "Sosyal medya", "com.facebook" to "Sosyal medya", "com.twitter" to "Sosyal medya",
        "com.snapchat" to "Sosyal medya", "com.reddit" to "Sosyal medya", "com.pinterest" to "Sosyal medya",
        "com.linkedin" to "Sosyal medya",
        "com.zhiliaoapp.musically" to "Video", "com.ss.android.ugc" to "Video",
        "com.google.android.youtube" to "Video", "com.netflix" to "Video", "com.amazon.avod" to "Video",
        "com.disney" to "Video", "tv.twitch" to "Video", "com.blutv" to "Video", "com.exxen" to "Video",
        "com.whatsapp" to "Mesajlaşma", "org.telegram" to "Mesajlaşma", "org.thoughtcrime.securesms" to "Mesajlaşma",
        "com.discord" to "Mesajlaşma", "com.google.android.apps.messaging" to "Mesajlaşma",
        "com.samsung.android.messaging" to "Mesajlaşma",
        "com.spotify" to "Müzik", "com.google.android.apps.youtube.music" to "Müzik", "deezer" to "Müzik",
        "com.android.chrome" to "Tarayıcı", "org.mozilla" to "Tarayıcı", "com.sec.android.app.sbrowser" to "Tarayıcı",
        "com.opera" to "Tarayıcı", "com.brave" to "Tarayıcı",
        "com.android.vending" to "Mağaza / güncelleme", "com.sec.android.app.samsungapps" to "Mağaza / güncelleme",
        "com.google.android.apps.maps" to "Harita", "com.waze" to "Harita", "com.yandex" to "Harita",
        "com.google.android.apps.photos" to "Fotoğraf / yedekleme", "com.google.android.apps.docs" to "Fotoğraf / yedekleme",
        "com.microsoft.skydrive" to "Fotoğraf / yedekleme", "com.dropbox" to "Fotoğraf / yedekleme"
    )

    private fun category(pkg: String, ai: ApplicationInfo): String {
        known.firstOrNull { pkg.startsWith(it.first) || pkg.contains(it.first) }?.let { return it.second }
        return when (ai.category) {
            ApplicationInfo.CATEGORY_GAME -> "Oyun"
            ApplicationInfo.CATEGORY_AUDIO -> "Müzik"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_IMAGE -> "Fotoğraf / yedekleme"
            ApplicationInfo.CATEGORY_SOCIAL -> "Sosyal medya"
            ApplicationInfo.CATEGORY_NEWS -> "Haber"
            ApplicationInfo.CATEGORY_MAPS -> "Harita"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Üretkenlik"
            else -> if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) "Sistem" else "Diğer"
        }
    }
}
