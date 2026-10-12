package com.emir.hizgostergesi

/** Telefonun kümülatif bayt sayaçlarının bir anlık görüntüsü. */
data class Counters(val totRx: Long, val totTx: Long, val mobRx: Long, val mobTx: Long)

/** İki okuma arasında akan veri, mobil ve Wi-Fi olarak ayrılmış. */
data class Delta(val mobRx: Long, val mobTx: Long, val wifiRx: Long, val wifiTx: Long) {
    val rx get() = mobRx + wifiRx
    val tx get() = mobTx + wifiTx
    val mobile get() = mobRx + mobTx
    val wifi get() = wifiRx + wifiTx
    val total get() = mobile + wifi

    companion object {
        val ZERO = Delta(0, 0, 0, 0)
    }
}

/**
 * Sayaç farklarından gerçek kullanımı hesaplar. Android'e bağımlı değildir, birim testleriyle sınanır.
 *
 * Ele alınan durumlar:
 *  - Sayaç geriye gider (yeniden başlatma, arayüz kapanması): o tur 0 sayılır, sonraki tur normal devam eder.
 *  - Anlamsız sıçrama (bir saniyede gigabaytlar): fiziksel olarak imkânsız, 0 sayılır.
 *  - VPN: Android trafiği hem VPN tünelinde hem asıl hatta sayar.
 *      · Mobilde: mobil sayaç doğrudur, "toplam - mobil" tünel kopyasıdır → Wi-Fi 0 sayılır.
 *      · Wi-Fi'da: "toplam - mobil" hem tüneli hem Wi-Fi'ı içerir → yarısı alınır (yaklaşık).
 */
object TrafficMath {
    /** Kabul edilen en yüksek hız: 2,5 Gbit/s ≈ 312 MB/s. Üstü sayaç hatası sayılır. */
    const val MAX_RATE = 312_500_000L

    fun read(totRx: Long, totTx: Long, mobRx: Long, mobTx: Long) =
        Counters(pos(totRx), pos(totTx), pos(mobRx), pos(mobTx))

    /** Android "desteklenmiyor" için -1 döndürür; bunu 0 kabul ederiz. */
    fun pos(v: Long) = if (v < 0) 0L else v

    /** Tek bir sayaç farkı: geri gidişte ve imkânsız sıçramada 0. */
    fun diff(prev: Long, cur: Long, dtSec: Double): Long {
        val d = cur - prev
        if (d <= 0) return 0
        val limit = (MAX_RATE * maxOf(dtSec, 1.0)).toLong()
        return if (d > limit) 0 else d
    }

    fun delta(prev: Counters, cur: Counters, dtSec: Double, vpn: Boolean, onCellular: Boolean): Delta {
        val dTotRx = diff(prev.totRx, cur.totRx, dtSec)
        val dTotTx = diff(prev.totTx, cur.totTx, dtSec)
        val dMobRx = minOf(diff(prev.mobRx, cur.mobRx, dtSec), if (dTotRx > 0) dTotRx else Long.MAX_VALUE)
        val dMobTx = minOf(diff(prev.mobTx, cur.mobTx, dtSec), if (dTotTx > 0) dTotTx else Long.MAX_VALUE)
        var wRx = (dTotRx - dMobRx).coerceAtLeast(0)
        var wTx = (dTotTx - dMobTx).coerceAtLeast(0)
        if (vpn) {
            if (onCellular) {
                wRx = 0
                wTx = 0
            } else {
                wRx /= 2
                wTx /= 2
            }
        }
        return Delta(dMobRx, dMobTx, wRx, wTx)
    }

    /** Bayt/saniye hızı. */
    fun rate(bytes: Long, dtSec: Double): Long = if (dtSec <= 0) 0 else (bytes / dtSec).toLong()
}

/**
 * Kayan "son X saat" sayacı: dakika dakika halka şeklinde kovalar (varsayılan 24 saat).
 * Android'e bağımlı değildir.
 */
class RollingCounter(val size: Int = 1440) {
    val total = LongArray(size)
    val mobile = LongArray(size)

    /** En son yazılan kovanın dakikası (epoch dakikası). 0: boş. */
    var minute = 0L
        private set

    private fun idx(m: Long) = Math.floorMod(m, size.toLong()).toInt()

    /** Kovaları verilen dakikaya kadar ilerletir, aradaki dakikaları boşaltır. */
    fun advanceTo(m: Long) {
        if (minute == 0L || m - minute >= size || m < minute) {
            total.fill(0)
            mobile.fill(0)
            minute = m
            return
        }
        while (minute < m) {
            minute++
            val i = idx(minute)
            total[i] = 0
            mobile[i] = 0
        }
    }

    fun add(nowMs: Long, totalBytes: Long, mobileBytes: Long) {
        advanceTo(nowMs / 60_000L)
        val i = idx(minute)
        total[i] += totalBytes
        mobile[i] += mobileBytes
    }

    /** Şu ana kadar son n dakikanın (toplam, mobil) değeri. */
    fun sum(nowMs: Long, minutes: Int): Pair<Long, Long> {
        advanceTo(nowMs / 60_000L)
        var t = 0L
        var m = 0L
        for (k in 0 until minutes.coerceIn(1, size)) {
            val i = idx(minute - k)
            t += total[i]
            m += mobile[i]
        }
        return t to m
    }

    fun reset(nowMs: Long) {
        total.fill(0)
        mobile.fill(0)
        minute = nowMs / 60_000L
    }

    fun serialize(): String = "$minute|${total.joinToString(",")}|${mobile.joinToString(",")}"

    /** Kaydedilmiş hâli geri yükler; biçim uymazsa false döner ve sayaç boş kalır. */
    fun restore(s: String?, nowMs: Long): Boolean {
        try {
            val parts = (s ?: "").split("|")
            if (parts.size != 3) return false
            val t = parts[1].split(",")
            val mo = parts[2].split(",")
            if (t.size != size || mo.size != size) return false
            val m = parts[0].toLong()
            val tv = LongArray(size) { t[it].toLong() }
            val mv = LongArray(size) { mo[it].toLong() }
            tv.copyInto(total)
            mv.copyInto(mobile)
            minute = m
            advanceTo(nowMs / 60_000L)
            return true
        } catch (e: Exception) {
            reset(nowMs)
            minute = 0L
            return false
        }
    }
}
