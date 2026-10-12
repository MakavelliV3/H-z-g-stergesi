package com.emir.hizgostergesi

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.util.TreeMap

class BackupTest {

    private class MapEditor(val m: MutableMap<String, Any?>) : SharedPreferences.Editor {
        override fun putString(k: String, v: String?) = apply { m[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { m[k] = v }
        override fun putInt(k: String, v: Int) = apply { m[k] = v }
        override fun putLong(k: String, v: Long) = apply { m[k] = v }
        override fun putFloat(k: String, v: Float) = apply { m[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { m[k] = v }
        override fun remove(k: String) = apply { m.remove(k) }
        override fun clear() = apply { m.clear() }
        override fun commit() = true
        override fun apply() {}
    }

    @Test
    fun prefs_roundTripKeepsTypes() {
        val src = mapOf<String, Any?>(
            Prefs.PLAN_ON to true, Prefs.PLAN_MB to 20480, Prefs.WINDOW_START to 1_700_000_000_000L,
            "f" to 1.5f, Prefs.GAME_APPS to "a,b", "set" to setOf("x", "y"),
            Prefs.PLAN_WARNED to "123|80"
        )
        // JSON'a çevirip metin üzerinden geri oku (dosyaya yazılmış gibi)
        val json = JSONObject(Backup.prefsToJson(src).toString())
        val out = HashMap<String, Any?>()
        val n = Backup.applyPrefs(json, MapEditor(out))
        assertEquals(6, n)
        assertEquals(true, out[Prefs.PLAN_ON])
        assertEquals(20480, out[Prefs.PLAN_MB])
        assertEquals(1_700_000_000_000L, out[Prefs.WINDOW_START])
        assertEquals(1.5f, out["f"])
        assertEquals("a,b", out[Prefs.GAME_APPS])
        assertEquals(setOf("x", "y"), out["set"])
        assertFalse(out.containsKey(Prefs.PLAN_WARNED))   // uyarı işaretleri geri yüklenmez
    }

    @Test
    fun applyPrefs_skipsBrokenEntries() {
        val json = JSONObject("""{"a":{"t":"i","v":"yazı"},"b":{"t":"?","v":1},"c":5,"d":{"t":"b","v":true}}""")
        val out = HashMap<String, Any?>()
        assertEquals(1, Backup.applyPrefs(json, MapEditor(out)))
        assertEquals(true, out["d"])
    }

    @Test
    fun csv_turkishExcelFormat() {
        val days = TreeMap<String, DayStats>()
        days["2026-10-11"] = DayStats().apply { mobile = 1_500_000; wifi = 250_000 }
        val text = Backup.csv(days)
        assertTrue(text.startsWith("﻿Tarih;"))
        val lines = text.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[1], lines[1].startsWith("2026-10-11;1,50;0,25;1,75;"))
    }
}
