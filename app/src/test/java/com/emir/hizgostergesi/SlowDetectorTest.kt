package com.emir.hizgostergesi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlowDetectorTest {

    /** 1 saniyelik ölçümlerle [sec] saniye besler; herhangi birinde uyarı çıktıysa true. */
    private fun run(d: SlowDetector, startEl: Long, sec: Int, bps: Long, cell: Boolean = true): Boolean {
        var warned = false
        for (i in 0..sec) if (d.feed(startEl + i * 1000L, bps, bps, cell)) warned = true
        return warned
    }

    @Test
    fun slowActiveUse_warns() {
        val d = SlowDetector(125_000)
        assertTrue(run(d, 0, 121, 50_000))       // 2 dk boyunca 50 KB/s ≈ 6 MB, eşik altı
    }

    @Test
    fun idlePhone_doesNotWarn() {
        val d = SlowDetector(125_000)
        assertFalse(run(d, 0, 121, 2_000))       // 2 KB/s: toplam 240 KB, kullanım yok sayılır
    }

    @Test
    fun fastConnection_doesNotWarn() {
        val d = SlowDetector(125_000)
        assertFalse(run(d, 0, 121, 2_000_000))
    }

    @Test
    fun wifi_neverWarns() {
        val d = SlowDetector(125_000)
        assertFalse(run(d, 0, 300, 50_000, cell = false))
    }

    @Test
    fun cooldown_onePerHour() {
        val d = SlowDetector(125_000)
        assertTrue(run(d, 0, 121, 50_000))
        assertFalse(run(d, 200_000, 121, 50_000))           // yarım saat dolmadan
        assertTrue(run(d, 3_800_000, 121, 50_000))          // bir saat sonra tekrar
    }
}
