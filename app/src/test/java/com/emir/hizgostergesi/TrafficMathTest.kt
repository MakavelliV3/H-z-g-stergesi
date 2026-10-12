package com.emir.hizgostergesi

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficMathTest {

    private val zero = Counters(0, 0, 0, 0)

    @Test
    fun normalTraffic_isSplitIntoMobileAndWifi() {
        val d = TrafficMath.delta(zero, Counters(1000, 500, 300, 100), 1.0, vpn = false, onCellular = false)
        assertEquals(300L, d.mobRx)
        assertEquals(100L, d.mobTx)
        assertEquals(700L, d.wifiRx)
        assertEquals(400L, d.wifiTx)
        assertEquals(1500L, d.total)
    }

    @Test
    fun counterGoingBackwards_countsAsZero() {
        val prev = Counters(10_000, 10_000, 5_000, 5_000)
        val d = TrafficMath.delta(prev, Counters(100, 100, 50, 50), 1.0, false, false)
        assertEquals(Delta.ZERO, d)
    }

    @Test
    fun counterResetThenGrowth_resumesNormally() {
        // Yeniden başlatma: sayaç küçüldü, bir sonraki tur sadece yeni artışı saymalı
        val afterReset = Counters(100, 100, 0, 0)
        val d = TrafficMath.delta(afterReset, Counters(1100, 600, 0, 0), 1.0, false, false)
        assertEquals(1000L, d.wifiRx)
        assertEquals(500L, d.wifiTx)
    }

    @Test
    fun impossibleSpike_isIgnored() {
        val huge = TrafficMath.MAX_RATE * 10
        val d = TrafficMath.delta(zero, Counters(huge, 0, 0, 0), 1.0, false, false)
        assertEquals(0L, d.rx)
    }

    @Test
    fun spikeLimit_scalesWithInterval() {
        // 15 saniyelik aralıkta 2 GB makul (≈133 MB/s), sayılmalı
        val twoGb = 2_000_000_000L
        val d = TrafficMath.delta(zero, Counters(twoGb, 0, 0, 0), 15.0, false, false)
        assertEquals(twoGb, d.wifiRx)
    }

    @Test
    fun vpnOnCellular_doesNotInventWifiTraffic() {
        // Tünel kopyası "toplam - mobil" farkında görünür; Wi-Fi sayılmamalı
        val d = TrafficMath.delta(zero, Counters(2000, 400, 1000, 200), 1.0, vpn = true, onCellular = true)
        assertEquals(1000L, d.mobRx)
        assertEquals(200L, d.mobTx)
        assertEquals(0L, d.wifi)
    }

    @Test
    fun vpnOnWifi_halvesDoubleCountedTraffic() {
        val d = TrafficMath.delta(zero, Counters(2000, 400, 0, 0), 1.0, vpn = true, onCellular = false)
        assertEquals(1000L, d.wifiRx)
        assertEquals(200L, d.wifiTx)
    }

    @Test
    fun mobileNeverExceedsTotal() {
        val d = TrafficMath.delta(zero, Counters(500, 500, 900, 900), 1.0, false, false)
        assertEquals(500L, d.mobRx)
        assertEquals(500L, d.mobTx)
        assertEquals(0L, d.wifi)
    }

    @Test
    fun unsupportedCounters_readAsZero() {
        assertEquals(Counters(0, 0, 0, 0), TrafficMath.read(-1, -1, -1, -1))
    }

    @Test
    fun rate_dividesByInterval() {
        assertEquals(500L, TrafficMath.rate(1000, 2.0))
        assertEquals(0L, TrafficMath.rate(1000, 0.0))
    }
}
