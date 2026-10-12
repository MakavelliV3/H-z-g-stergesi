package com.emir.hizgostergesi

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.ceil

/**
 * Paket (tarife) hesapları. Android'e bağımlı değildir, birim testleriyle sınanır.
 *
 * Yenilenme günü 1..31 olabilir; ayda o gün yoksa (ör. Şubat 30) ayın son günü kabul edilir.
 */
object PlanMath {
    const val DAY = 86_400_000L

    private fun at(base: Calendar, year: Int, month: Int, day: Int): Calendar =
        (base.clone() as Calendar).apply {
            clear()
            timeZone = base.timeZone
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.DAY_OF_MONTH, minOf(day, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }

    /** İçinde bulunulan fatura döneminin başlangıcı (yenilenme günü, 00:00). */
    fun cycleStart(now: Long, resetDay: Int, tz: TimeZone = TimeZone.getDefault()): Long {
        val day = resetDay.coerceIn(1, 31)
        val c = Calendar.getInstance(tz).apply { timeInMillis = now }
        val thisMonth = at(c, c.get(Calendar.YEAR), c.get(Calendar.MONTH), day)
        if (thisMonth.timeInMillis <= now) return thisMonth.timeInMillis
        val prev = (c.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        return at(c, prev.get(Calendar.YEAR), prev.get(Calendar.MONTH), day).timeInMillis
    }

    /** Dönemin bitişi = bir sonraki yenilenme anı. */
    fun cycleEnd(start: Long, resetDay: Int, tz: TimeZone = TimeZone.getDefault()): Long {
        val day = resetDay.coerceIn(1, 31)
        val c = Calendar.getInstance(tz).apply {
            timeInMillis = start
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }
        return at(c, c.get(Calendar.YEAR), c.get(Calendar.MONTH), day).timeInMillis
    }

    /** Kalan gün sayısı (bugün dahil, en az 1). */
    fun daysLeft(now: Long, end: Long): Int = maxOf(1, ceil((end - now).toDouble() / DAY).toInt())

    /** Kalan süre boyunca her gün eşit kullanılırsa günlük pay. */
    fun dailyAllowance(remaining: Long, now: Long, end: Long): Long =
        if (remaining <= 0) 0 else remaining / daysLeft(now, end)

    /**
     * Bugüne kadarki tempoyla dönem sonunda toplam ne kadar kullanılmış olur.
     * Dönemin ilk saatlerinde tahmin yanıltıcı olmasın diye en az 12 saat geçmiş sayılır.
     */
    fun forecast(used: Long, start: Long, now: Long, end: Long): Long {
        val elapsed = maxOf(now - start, DAY / 2).toDouble()
        val total = (end - start).toDouble()
        return (used.toDouble() * total / elapsed).toLong()
    }

    /** Paket bu tempoyla bitecekse, bitmesine kaç gün kala biteceği (bitmeyecekse null). */
    fun daysEarly(used: Long, limit: Long, start: Long, now: Long, end: Long): Int? {
        if (limit <= 0) return null
        if (used <= 0) return null
        val elapsed = maxOf(now - start, DAY / 2).toDouble()
        val runOutAt = start + (limit.toDouble() * elapsed / used).toLong()
        if (runOutAt >= end) return null
        return maxOf(0, ((end - runOutAt) / DAY).toInt())
    }

    /** Saat gece paketi aralığında mı? start=end ise aralık yok sayılır. Gece yarısını aşan aralıklar desteklenir. */
    fun isNight(hour: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> hour in start until end
        else -> hour >= start || hour < end
    }

    /** Uyarı seviyesi: 0 (yok), 80, 100. */
    fun alertLevel(used: Long, limit: Long): Int = when {
        limit <= 0 -> 0
        used >= limit -> 100
        used * 100 >= limit * 80 -> 80
        else -> 0
    }
}
