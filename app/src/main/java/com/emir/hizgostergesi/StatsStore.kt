package com.emir.hizgostergesi

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
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

    /** Başka bir günü/ayı bunun üstüne ekler (aylık ve yıllık toplamlar için). */
    fun merge(o: DayStats) {
        mobile += o.mobile
        wifi += o.wifi
        peakMobile = maxOf(peakMobile, o.peakMobile)
        peakWifi = maxOf(peakWifi, o.peakWifi)
        activeMobileSec += o.activeMobileSec
        activeMobileBytes += o.activeMobileBytes
        activeWifiSec += o.activeWifiSec
        activeWifiBytes += o.activeWifiBytes
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

/**
 * Günlük istatistikleri bellekte tutar, ara ara dosyaya yazar.
 * Son 400 gün gün gün saklanır; daha eskileri aylık özete katlanır, böylece yıllık rapor kaybolmaz.
 */
object StatsStore {
    private const val FILE = "gunluk.json"
    private const val KEEP_DAYS = 400

    private var cache: TreeMap<String, DayStats>? = null
    private var monthCache: TreeMap<String, DayStats>? = null
    private var lastSave = 0L
    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Synchronized
    fun key(time: Long = System.currentTimeMillis()): String = keyFormat.format(Date(time))

    @Synchronized
    fun all(c: Context): TreeMap<String, DayStats> {
        cache?.let { return it }
        val days = TreeMap<String, DayStats>()
        val months = TreeMap<String, DayStats>()
        try {
            val o = JSONObject(String(AtomicFile(File(c.filesDir, FILE)).readFully()))
            if (o.has("days")) {
                val d = o.getJSONObject("days")
                d.keys().forEach { k -> days[k] = DayStats.fromJson(d.getJSONObject(k)) }
                val m = o.optJSONObject("months")
                m?.keys()?.forEach { k -> months[k] = DayStats.fromJson(m.getJSONObject(k)) }
            } else {
                // eski biçim: sadece günler
                o.keys().forEach { k -> days[k] = DayStats.fromJson(o.getJSONObject(k)) }
            }
        } catch (e: Exception) {
            // ilk açılış ya da bozuk dosya: boş başla
        }
        cache = days
        monthCache = months
        return days
    }

    /** Gün gün tutulmayan eski aylar (yyyy-MM). */
    @Synchronized
    fun archivedMonths(c: Context): TreeMap<String, DayStats> {
        all(c)
        return monthCache!!
    }

    /** Anahtarı bu önekle başlayan her şeyin toplamı: "2026-10" bir ay, "2026" bir yıl. */
    @Synchronized
    fun aggregate(c: Context, prefix: String): DayStats {
        val r = DayStats()
        all(c).forEach { (k, v) -> if (k.startsWith(prefix)) r.merge(v) }
        archivedMonths(c).forEach { (k, v) -> if (k.startsWith(prefix)) r.merge(v) }
        return r
    }

    /** Kaydı olan en eski yıl. */
    @Synchronized
    fun firstYear(c: Context): Int {
        val a = all(c).firstEntry()?.key?.take(4)?.toIntOrNull()
        val b = archivedMonths(c).keys.firstOrNull()?.take(4)?.toIntOrNull()
        return listOfNotNull(a, b).minOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
    }

    @Synchronized
    fun today(c: Context): DayStats = all(c).getOrPut(key()) { DayStats() }

    @Synchronized
    fun save(c: Context, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSave < 15_000) return
        lastSave = now
        val map = all(c)
        val months = archivedMonths(c)
        while (map.size > KEEP_DAYS) {
            val old = map.pollFirstEntry() ?: break
            months.getOrPut(old.key.take(7)) { DayStats() }.merge(old.value)
        }
        val d = JSONObject()
        map.forEach { (k, v) -> d.put(k, v.toJson()) }
        val m = JSONObject()
        months.forEach { (k, v) -> m.put(k, v.toJson()) }
        val o = JSONObject().put("days", d).put("months", m)
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
