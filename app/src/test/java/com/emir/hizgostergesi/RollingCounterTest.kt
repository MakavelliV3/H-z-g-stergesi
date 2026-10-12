package com.emir.hizgostergesi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingCounterTest {

    private val min = 60_000L
    private val t0 = 1_700_000_000_000L // sabit bir başlangıç anı

    @Test
    fun addsWithinSameMinute() {
        val r = RollingCounter()
        r.add(t0, 100, 40)
        r.add(t0 + 10_000, 50, 10)
        assertEquals(150L to 50L, r.sum(t0 + 20_000, 60))
    }

    @Test
    fun oldDataFallsOutOfWindow() {
        val r = RollingCounter()
        r.add(t0, 1000, 0)                 // 0. dakika
        r.add(t0 + 30 * min, 200, 0)       // 30. dakika
        assertEquals(1200L, r.sum(t0 + 59 * min, 60).first)
        assertEquals(200L, r.sum(t0 + 61 * min, 60).first)  // ilk veri artık 60 dakikadan eski
        assertEquals(1200L, r.sum(t0 + 61 * min, 120).first) // 2 saatlik pencerede hâlâ var
        assertEquals(0L, r.sum(t0 + 91 * min, 60).first)
    }

    @Test
    fun gapLongerThanBuffer_clearsEverything() {
        val r = RollingCounter()
        r.add(t0, 500, 500)
        assertEquals(0L to 0L, r.sum(t0 + 25 * 60 * min, 1440))
    }

    @Test
    fun clockGoingBackwards_clears() {
        val r = RollingCounter()
        r.add(t0, 500, 0)
        r.add(t0 - 10 * min, 7, 0)
        assertEquals(7L, r.sum(t0 - 10 * min, 60).first)
    }

    @Test
    fun windowLargerThanBuffer_isClamped() {
        val r = RollingCounter(size = 10)
        repeat(20) { r.add(t0 + it * min, 1, 0) }
        assertEquals(10L, r.sum(t0 + 19 * min, 999).first)
    }

    @Test
    fun serializeAndRestore_roundTrip() {
        val r = RollingCounter()
        r.add(t0, 123, 45)
        r.add(t0 + 5 * min, 77, 7)
        val copy = RollingCounter()
        assertTrue(copy.restore(r.serialize(), t0 + 6 * min))
        assertEquals(200L to 52L, copy.sum(t0 + 6 * min, 60))
    }

    @Test
    fun restore_dropsMinutesThatPassedWhileStopped() {
        val r = RollingCounter()
        r.add(t0, 123, 0)
        val copy = RollingCounter()
        assertTrue(copy.restore(r.serialize(), t0 + 70 * min))
        assertEquals(0L, copy.sum(t0 + 70 * min, 60).first)
        assertEquals(123L, copy.sum(t0 + 70 * min, 120).first)
    }

    @Test
    fun restore_rejectsBadData() {
        val r = RollingCounter()
        assertFalse(r.restore("", t0))
        assertFalse(r.restore("abc|1,2|3,4", t0))
        assertFalse(r.restore(null, t0))
        assertEquals(0L, r.sum(t0, 60).first)
    }

    @Test
    fun reset_clearsTotals() {
        val r = RollingCounter()
        r.add(t0, 999, 99)
        r.reset(t0 + min)
        assertEquals(0L to 0L, r.sum(t0 + min, 1440))
        r.add(t0 + 2 * min, 5, 1)
        assertEquals(5L to 1L, r.sum(t0 + 2 * min, 60))
    }
}
