package com.emir.hizgostergesi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayStatsTest {

    @Test
    fun add_tracksTotalsPeakAndActiveTime() {
        val d = DayStats()
        d.add(true, 1_000_000, 100_000, 1.0, 21)   // 1 MB/s, aktif
        d.add(true, 1_000, 0, 1.0, 21)            // 1 KB/s, boşta (ortalamaya girmez)
        assertEquals(1_101_000L, d.mobile)
        assertEquals(1_000_000L, d.peakMobile)
        assertEquals(1_000_000L, d.avgMobile)
        assertEquals(1_101_000L, d.hourUse[21])
        assertEquals(0L, d.wifi)
    }

    @Test
    fun add_ignoresEmptyAndClampsHour() {
        val d = DayStats()
        d.add(false, 0, 0, 1.0, 5)
        d.add(false, 10_000, 0, 1.0, 99)
        assertEquals(10_000L, d.wifi)
        assertEquals(10_000L, d.hourUse[23])
    }

    @Test
    fun merge_sumsAndKeepsMaxPeak() {
        val a = DayStats().apply {
            add(true, 2_000_000, 0, 1.0, 1)
            addConn("5G", 2_000_000, 0, 1.0)
            screenOff = 10
        }
        val b = DayStats().apply {
            add(true, 500_000, 0, 1.0, 1)
            addConn("5G", 500_000, 0, 1.0)
            addConn("Wi-Fi", 7, 0, 1.0)
            screenOff = 5
        }
        a.merge(b)
        assertEquals(2_500_000L, a.mobile)
        assertEquals(2_000_000L, a.peakMobile)
        assertEquals(15L, a.screenOff)
        assertEquals(2_500_000L, a.byConn["5G"]!!.bytes)
        assertEquals(7L, a.byConn["Wi-Fi"]!!.bytes)
    }

    @Test
    fun json_roundTrip() {
        val d = DayStats().apply {
            add(true, 3_000_000, 50_000, 1.0, 8)
            add(false, 9_000_000, 10_000, 2.0, 22)
            addConn("4.5G", 3_000_000, 50_000, 1.0)
            screenOff = 1234
        }
        val back = DayStats.fromJson(JSONObject(d.toJson().toString()))
        assertEquals(d.mobile, back.mobile)
        assertEquals(d.wifi, back.wifi)
        assertEquals(d.peakWifi, back.peakWifi)
        assertEquals(d.avgWifi, back.avgWifi)
        assertEquals(1234L, back.screenOff)
        assertEquals(d.hourUse[22], back.hourUse[22])
        assertEquals(3_050_000L, back.byConn["4.5G"]!!.bytes)
    }

    @Test
    fun json_oldFormatWithoutNewFields_loads() {
        val back = DayStats.fromJson(JSONObject("""{"m":5,"w":6,"pm":1,"pw":2}"""))
        assertEquals(5L, back.mobile)
        assertEquals(6L, back.wifi)
        assertEquals(0L, back.screenOff)
        assertTrue(back.byConn.isEmpty())
    }

    @Test
    fun dropHourly_removesHourlyArraysFromJson() {
        val d = DayStats().apply { add(true, 5_000_000, 0, 1.0, 3) }
        d.dropHourly()
        val json = d.toJson()
        assertFalse(json.has("hu"))
        assertEquals(5_000_000L, DayStats.fromJson(json).mobile)
    }
}
