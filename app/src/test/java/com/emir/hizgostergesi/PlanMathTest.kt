package com.emir.hizgostergesi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PlanMathTest {

    private val tz = TimeZone.getTimeZone("Europe/Istanbul")

    private fun t(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(y, m - 1, d, h, min, 0)
        }.timeInMillis

    @Test
    fun cycleStart_afterResetDay_isThisMonth() {
        assertEquals(t(2026, 10, 5), PlanMath.cycleStart(t(2026, 10, 12, 20), 5, tz))
    }

    @Test
    fun cycleStart_beforeResetDay_isPreviousMonth() {
        assertEquals(t(2026, 9, 15), PlanMath.cycleStart(t(2026, 10, 12, 20), 15, tz))
    }

    @Test
    fun cycleStart_exactlyOnResetMidnight_isToday() {
        assertEquals(t(2026, 10, 15), PlanMath.cycleStart(t(2026, 10, 15, 0, 0), 15, tz))
    }

    @Test
    fun cycleStart_acrossYearBoundary() {
        assertEquals(t(2025, 12, 20), PlanMath.cycleStart(t(2026, 1, 3), 20, tz))
    }

    @Test
    fun resetDay31_usesLastDayOfShortMonths() {
        // Şubat 2026'da 31 yok → 28 Şubat
        assertEquals(t(2026, 2, 28), PlanMath.cycleStart(t(2026, 3, 10), 31, tz))
        assertEquals(t(2026, 3, 31), PlanMath.cycleEnd(t(2026, 2, 28), 31, tz))
        assertEquals(t(2026, 4, 30), PlanMath.cycleEnd(t(2026, 3, 31), 31, tz))
    }

    @Test
    fun cycleEnd_isNextResetDay() {
        assertEquals(t(2026, 11, 15), PlanMath.cycleEnd(t(2026, 10, 15), 15, tz))
        assertEquals(t(2027, 1, 1), PlanMath.cycleEnd(t(2026, 12, 1), 1, tz))
    }

    @Test
    fun daysLeft_andAllowance() {
        val now = t(2026, 10, 12, 12)
        val end = t(2026, 10, 15)
        assertEquals(3, PlanMath.daysLeft(now, end))         // 12'nin kalanı, 13, 14
        assertEquals(1, PlanMath.daysLeft(end - 1000, end))
        assertEquals(3_000_000_000L / 3, PlanMath.dailyAllowance(3_000_000_000L, now, end))
        assertEquals(0L, PlanMath.dailyAllowance(-5, now, end))
    }

    @Test
    fun forecast_extrapolatesPace() {
        val start = t(2026, 10, 1)
        val end = t(2026, 10, 31)
        val now = t(2026, 10, 11)      // 10 gün geçti, 30 günlük dönem
        assertEquals(30_000L, PlanMath.forecast(10_000, start, now, end))
    }

    @Test
    fun forecast_ignoresFirstHoursNoise() {
        val start = t(2026, 10, 1)
        val end = t(2026, 10, 31)
        // 1 saatte 1 GB: 12 saat sayılır, aşırı tahmin edilmez
        val f = PlanMath.forecast(1_000_000_000, start, start + 3_600_000, end)
        assertEquals(60_000_000_000L, f)
    }

    @Test
    fun daysEarly() {
        val start = t(2026, 10, 1)
        val end = t(2026, 10, 31)
        val now = t(2026, 10, 11)
        // 10 günde 10 GB, paket 20 GB → 20. günde biter, 10 gün erken
        assertEquals(10, PlanMath.daysEarly(10_000, 20_000, start, now, end))
        // Paket yetiyorsa null
        assertNull(PlanMath.daysEarly(5_000, 20_000, start, now, end))
        assertNull(PlanMath.daysEarly(0, 20_000, start, now, end))
    }

    @Test
    fun nightRanges() {
        assertTrue(PlanMath.isNight(2, 0, 8))
        assertFalse(PlanMath.isNight(8, 0, 8))
        assertTrue(PlanMath.isNight(23, 22, 6))   // gece yarısını aşan
        assertTrue(PlanMath.isNight(5, 22, 6))
        assertFalse(PlanMath.isNight(12, 22, 6))
        assertFalse(PlanMath.isNight(3, 4, 4))    // boş aralık
    }

    @Test
    fun alertLevels() {
        assertEquals(0, PlanMath.alertLevel(79, 100))
        assertEquals(80, PlanMath.alertLevel(80, 100))
        assertEquals(100, PlanMath.alertLevel(100, 100))
        assertEquals(0, PlanMath.alertLevel(500, 0))
    }

    @Test
    fun operatorCalibration() {
        val limit = 10_000L
        // Uygulama 4000 ölçtü, operatör 5000 kaldı diyor → kullanılan 5000 olmalı
        val off = PlanMath.offsetFor(limit, 5_000, 4_000)
        assertEquals(1_000L, off)
        assertEquals(5_000L, PlanMath.adjusted(4_000, off))
        // Sonra 500 daha kullanılınca
        assertEquals(5_500L, PlanMath.adjusted(4_500, off))
        // Operatör daha az kullanım diyorsa düzeltme eksi olabilir, kullanım eksiye düşmez
        val neg = PlanMath.offsetFor(limit, 9_000, 4_000)
        assertEquals(-3_000L, neg)
        assertEquals(0L, PlanMath.adjusted(1_000, neg))
        // Kalan, paketten büyük yazılırsa sınırlanır
        assertEquals(-4_000L, PlanMath.offsetFor(limit, 50_000, 4_000))
    }
}
