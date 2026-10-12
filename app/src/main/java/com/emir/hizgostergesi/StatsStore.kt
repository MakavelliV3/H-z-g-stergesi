package com.emir.hizgostergesi

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONArray
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

    /** Ekran kapalıyken harcanan veri. */
    var screenOff = 0L

    /** Saat saat kullanım (0-23) ve saat saat hız ölçümleri. Eski günlerde boş bırakılır. */
    var hasHourly = true
    val hourUse = LongArray(24)
    val hourMobSec = DoubleArray(24)
    val hourMobBytes = LongArray(24)
    val hourWifiSec = DoubleArray(24)
    val hourWifiBytes = LongArray(24)

    /** Ortalama: sadece gerçekten veri akan saniyelerdeki ortalama indirme hızı. */
    val avgMobile: Long get() = if (activeMobileSec >= 1) (activeMobileBytes / activeMobileSec).toLong() else 0L
    val avgWifi: Long get() = if (activeWifiSec >= 1) (activeWifiBytes / activeWifiSec).toLong() else 0L

    fun add(isMobile: Boolean, rx: Long, tx: Long, dt: Double, hour: Int) {
        if (rx + tx == 0L) return
        val h = hour.coerceIn(0, 23)
        hourUse[h] += rx + tx
        val rate = (rx / dt).toLong()
        val active = rate >= ACTIVE_RATE
        if (isMobile) {
            mobile += rx + tx
            if (rate > peakMobile) peakMobile = rate
            if (active) {
                activeMobileSec += dt
                activeMobileBytes += rx
                hourMobSec[h] += dt
                hourMobBytes[h] += rx
            }
        } else {
            wifi += rx + tx
            if (rate > peakWifi) peakWifi = rate
            if (active) {
                activeWifiSec += dt
                activeWifiBytes += rx
                hourWifiSec[h] += dt
                hourWifiBytes[h] += rx
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
        screenOff += o.screenOff
        for (h in 0 until 24) {
            hourUse[h] += o.hourUse[h]
            hourMobSec[h] += o.hourMobSec[h]
            hourMobBytes[h] += o.hourMobBytes[h]
            hourWifiSec[h] += o.hourWifiSec[h]
            hourWifiBytes[h] += o.hourWifiBytes[h]
        }
    }

    /** Saatlik ayrıntıyı bırakır (eski günlerde dosyayı küçük tutmak için). */
    fun dropHourly() {
        hasHourly = false
        hourUse.fill(0)
        hourMobSec.fill(0.0)
        hourMobBytes.fill(0)
        hourWifiSec.fill(0.0)
        hourWifiBytes.fill(0)
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
            .put("m", mobile).put("w", wifi)
            .put("pm", peakMobile).put("pw", peakWifi)
            .put("ams", activeMobileSec).put("amb", activeMobileBytes)
            .put("aws", activeWifiSec).put("awb", activeWifiBytes)
            .put("so", screenOff)
        if (hasHourly) {
            o.put("hu", JSONArray(hourUse.toList()))
            o.put("hms", JSONArray(hourMobSec.toList()))
            o.put("hmb", JSONArray(hourMobBytes.toList()))
            o.put("hws", JSONArray(hourWifiSec.toList()))
            o.put("hwb", JSONArray(hourWifiBytes.toList()))
        }
        return o
    }

    companion object {
        /** Bu hızın altındaki saniyeler "boşta" sayılır, ortalamaya katılmaz. */
        const val ACTIVE_RATE = 5L * 1024

        private fun readLongs(a: JSONArray?, into: LongArray) {
            if (a == null) return
            for (i in 0 until minOf(24, a.length())) into[i] = a.optLong(i)
        }

        private fun readDoubles(a: JSONArray?, into: DoubleArray) {
            if (a == null) return
            for (i in 0 until minOf(24, a.length())) into[i] = a.optDouble(i, 0.0)
        }

        fun fromJson(o: JSONObject) = DayStats().apply {
            mobile = o.optLong("m")
            wifi = o.optLong("w")
            peakMobile = o.optLong("pm")
            peakWifi = o.optLong("pw")
            activeMobileSec = o.optDouble("ams", 0.0)
            activeMobileBytes = o.optLong("amb")
            activeWifiSec = o.optDouble("aws", 0.0)
            activeWifiBytes = o.optLong("awb")
            screenOff = o.optLong("so")
            readLongs(o.optJSONArray("hu"), hourUse)
            readDoubles(o.optJSONArray("hms"), hourMobSec)
            readLongs(o.optJSONArray("hmb"), hourMobBytes)
            readDoubles(o.optJSONArray("hws"), hourWifiSec)
            readLongs(o.optJSONArray("hwb"), hourWifiBytes)
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
        if (!force && now - lastSave < 30_000) return
        lastSave = now
        val map = all(c)
        val months = archivedMonths(c)
        while (map.size > KEEP_DAYS) {
            val old = map.pollFirstEntry() ?: break
            months.getOrPut(old.key.take(7)) { DayStats().apply { dropHourly() } }.merge(old.value)
        }
        // Saatlik ayrıntı sadece son 60 gün için tutulur
        val hourlyCutoff = key(System.currentTimeMillis() - 60L * 86_400_000L)
        map.forEach { (k, v) -> if (k < hourlyCutoff && v.hasHourly) v.dropHourly() }
        months.values.forEach { if (it.hasHourly) it.dropHourly() }
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
