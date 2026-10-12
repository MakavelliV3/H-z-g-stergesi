package com.emir.hizgostergesi

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefsTest {
    @Test
    fun parseLimits_skipsBrokenEntries() {
        val m = Prefs.parseLimits("10123=500;bozuk;10200=0; 10300 = 1024;=5;abc=7")
        assertEquals(mapOf(10123 to 500, 10300 to 1024), m)
    }

    @Test
    fun parseLimits_emptyOrNull() {
        assertEquals(emptyMap<Int, Int>(), Prefs.parseLimits(""))
        assertEquals(emptyMap<Int, Int>(), Prefs.parseLimits(null))
    }
}
