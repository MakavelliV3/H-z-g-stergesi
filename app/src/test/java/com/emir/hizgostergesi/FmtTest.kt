package com.emir.hizgostergesi

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FmtTest {

    @After
    fun restoreBase() {
        Fmt.base = 1000
    }

    @Test
    fun bytes_base1000() {
        Fmt.base = 1000
        assertEquals("999 B", Fmt.bytes(999))
        assertEquals("1 KB", Fmt.bytes(1500))
        assertEquals("1,5 MB", Fmt.bytes(1_500_000))
        assertEquals("150 MB", Fmt.bytes(150_000_000))
        assertEquals("2,50 GB", Fmt.bytes(2_500_000_000))
        assertEquals("12,3 GB", Fmt.bytes(12_340_000_000))
        assertEquals("0 B", Fmt.bytes(-5))
    }

    @Test
    fun bytes_base1024() {
        Fmt.base = 1024
        assertEquals("1023 B", Fmt.bytes(1023))
        assertEquals("1,0 MB", Fmt.bytes(1_048_576))
        assertEquals("1,00 GB", Fmt.bytes(1_073_741_824))
    }

    @Test
    fun speed_bytesMode() {
        Fmt.base = 1000
        assertEquals("4,4 MB/s", Fmt.speed(4_404_019, false))
        assertEquals("12 MB/s", Fmt.speed(12_300_000, false))
        assertEquals("120 KB/s", Fmt.speed(120_000, false))
        assertEquals("0 KB/s", Fmt.speed(0, false))
        assertEquals("0 KB/s", Fmt.speed(-100, false))
    }

    @Test
    fun speed_bitsMode_isAlwaysDecimal() {
        Fmt.base = 1024
        assertEquals("1,0 Mbps", Fmt.speed(125_000, true))
        assertEquals("10 Mbps", Fmt.speed(1_250_000, true))
        assertEquals("800 Kbps", Fmt.speed(100_000, true))
        assertEquals("1,2 Gbps", Fmt.speed(150_000_000, true))
    }

    @Test
    fun compact() {
        Fmt.base = 1000
        assertEquals("4,4M", Fmt.compact(4_404_019, false))
        assertEquals("350K", Fmt.compact(350_000, false))
        assertEquals("10M", Fmt.compact(1_250_000, true))
    }

    @Test
    fun quotaHelpers() {
        Fmt.base = 1000
        assertEquals(1_000_000_000L, Fmt.mbToBytes(1024))
        assertEquals(250_000_000L, Fmt.mbToBytes(250))
        Fmt.base = 1024
        assertEquals(1_073_741_824L, Fmt.mbToBytes(1024))
        assertEquals("1 GB", Fmt.mb(1024))
        assertEquals("5 GB", Fmt.mb(5120))
        assertEquals("250 MB", Fmt.mb(250))
    }

    @Test
    fun remaining() {
        assertEquals("1 sa 1 dk", Fmt.remaining(3_660_000))
        assertEquals("59 dk", Fmt.remaining(3_599_999))
        assertEquals("0 dk", Fmt.remaining(-1000))
    }
}
