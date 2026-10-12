package com.emir.hizgostergesi

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeMap

/** Bir günün mobil ve Wi-Fi istatistikleri. Hızlar bayt/saniye cinsinden. */
class DayStats {
    var mobile = 0L
    var wifi = 0L
    var peakMobile = 0L
    var peakWifi = 0L
    var activeMobileSec = 0.0
    var activeMobileBytes = 0L
    var activeWifiSec = 0.0
    var activeWifiBytes = 0L

    /** Ortalama: sadece gerçekten veri akan saniyelerdeki ortalama indirme hızı. */
    val avgMobile: Long get() = if (activeMobileSec >= 1) (activeMobileBytes / activeMobileSec).toLong() else 0L
    val avgWifi: Long get() = if (activeWifiSec >= 1) (activeWifiBytes / activeWifiSec).toLong() else 0L

    fun add(isMobile: Boolean, rx: Long, tx: Long, dt: Double) {
        if (rx + tx == 0L) return
        val rate = (rx / dt).toLong()
        if (isMobile) {
            mobile += rx + tx
            if (rate > peakMobile) peakMobile = rate
            if (rate >= ACTIVE_RATE) {
                activeMobileSec += dt
                activeMobileBytes += rx
            }
        } else {
            wifi += rx + tx
            if (rate > peakWifi) peakWifi = rate
            if (rate >= ACTIVE_RATE) {
                activeWifiSec += dt
                activeWifiBytes += rx
            }
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("m", mobile).put("w", wifi)
        .put("pm", peakMobile).put("pw", peakWifi)
        .put("ams", activeMobileSec).put("amb", activeMobileBytes)
        .put("aws", activeWifiSec).put("awb", activeWifiBytes)

    companion object {
        /** Bu hızın altındaki saniyeler "boşta" sayılır, ortalamaya katılmaz. */
        const val ACTIVE_RATE = 5L * 1024

        fun fromJson(o: JSONObject) = DayStats().apply {
            mobile = o.optLong("m")
            wifi = o.optLong("w")
            peakMobile = o.optLong("pm")
            peakWifi = o.optLong("pw")
            activeMobileSec = o.optDouble("ams", 0.0)
            activeMobileBytes = o.optLong("amb")
            activeWifiSec = o.optDouble("aws", 0.0)
            activeWifiBytes = o.optLong("awb")
        }
    }
}

/** Günlük istatistikleri bellekte tutar, ara ara dosyaya yazar. Son 40 gün saklanır. */
object StatsStore {
    private const val FILE = "gunluk.json"
    private const val KEEP_DAYS = 40

    private var cache: TreeMap<String, DayStats>? = null
    private var lastSave = 0L
    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Synchronized
    fun key(time: Long = System.currentTimeMillis()): String = keyFormat.format(Date(time))

    @Synchronized
    fun all(c: Context): TreeMap<String, DayStats> {
        cache?.let { return it }
        val map = TreeMap<String, DayStats>()
        try {
            val txt = String(AtomicFile(File(c.filesDir, FILE)).readFully())
            val o = JSONObject(txt)
            o.keys().forEach { k -> map[k] = DayStats.fromJson(o.getJSONObject(k)) }
        } catch (e: Exception) {
            // ilk açılış ya da bozuk dosya: boş başla
        }
        cache = map
        return map
    }

    @Synchronized
    fun today(c: Context): DayStats = all(c).getOrPut(key()) { DayStats() }

    @Synchronized
    fun save(c: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSave < 15_000) return
        lastSave = now
        val map = all(c)
        while (map.size > KEEP_DAYS) map.pollFirstEntry()
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v.toJson()) }
        val file = AtomicFile(File(c.filesDir, FILE))
        val out = try {
            file.startWrite()
        } catch (e: Exception) {
            return
        }
        try {
            out.write(o.toString().toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
