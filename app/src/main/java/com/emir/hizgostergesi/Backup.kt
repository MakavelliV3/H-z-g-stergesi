package com.emir.hizgostergesi

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONObject
import java.util.Locale
import java.util.TreeMap

/** Ayar ve istatistik yedeği (JSON) ile CSV dışa aktarma. */
object Backup {
    const val FORMAT = 1

    /** Geri yüklenmemesi gereken anlık durum anahtarları (uyarı işaretleri vb.). */
    private val SKIP = setOf(Prefs.PLAN_WARNED, Prefs.QUOTA_WARNED, Prefs.APP_LIMIT_SENT)

    /** SharedPreferences içeriğini tür bilgisiyle JSON'a çevirir. */
    fun prefsToJson(all: Map<String, *>): JSONObject {
        val o = JSONObject()
        all.forEach { (k, v) ->
            if (k in SKIP || v == null) return@forEach
            val t = when (v) {
                is Boolean -> "b"
                is Int -> "i"
                is Long -> "l"
                is Float -> "f"
                is String -> "s"
                is Set<*> -> "ss"
                else -> return@forEach
            }
            val value: Any = if (v is Set<*>) org.json.JSONArray(v.map { it.toString() }) else v
            o.put(k, JSONObject().put("t", t).put("v", value))
        }
        return o
    }

    /** JSON'daki ayarları düzenleyiciye yazar; tanınmayan girdileri atlar. Yazılan anahtar sayısını döner. */
    fun applyPrefs(o: JSONObject, e: SharedPreferences.Editor): Int {
        var n = 0
        o.keys().forEach { k ->
            if (k in SKIP) return@forEach
            val item = o.optJSONObject(k) ?: return@forEach
            try {
                when (item.getString("t")) {
                    "b" -> e.putBoolean(k, item.getBoolean("v"))
                    "i" -> e.putInt(k, item.getInt("v"))
                    "l" -> e.putLong(k, item.getLong("v"))
                    "f" -> e.putFloat(k, item.getDouble("v").toFloat())
                    "s" -> e.putString(k, item.getString("v"))
                    "ss" -> {
                        val a = item.getJSONArray("v")
                        e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                    else -> return@forEach
                }
                n++
            } catch (ex: Exception) { }
        }
        return n
    }

    /** Günlük istatistikleri Excel'in Türkçe ayarlarıyla açılacak CSV'ye çevirir (; ayraç, virgüllü ondalık). */
    fun csv(days: TreeMap<String, DayStats>, months: TreeMap<String, DayStats> = TreeMap()): String {
        val tr = Locale("tr", "TR")
        fun mb(b: Long) = String.format(tr, "%.2f", b / 1_000_000.0)
        val sb = StringBuilder("﻿")
        sb.append("Tarih;Mobil (MB);Wi-Fi (MB);Toplam (MB);Ekran kapalı (MB);Mobil tepe (MB/s);Wi-Fi tepe (MB/s);Mobil ort. (MB/s);Wi-Fi ort. (MB/s)\n")
        fun row(k: String, d: DayStats) {
            sb.append(k).append(';')
                .append(mb(d.mobile)).append(';')
                .append(mb(d.wifi)).append(';')
                .append(mb(d.mobile + d.wifi)).append(';')
                .append(mb(d.screenOff)).append(';')
                .append(mb(d.peakMobile)).append(';')
                .append(mb(d.peakWifi)).append(';')
                .append(mb(d.avgMobile)).append(';')
                .append(mb(d.avgWifi)).append('\n')
        }
        months.forEach { (k, d) -> row("$k (ay)", d) }
        days.forEach { (k, d) -> row(k, d) }
        return sb.toString()
    }

    fun export(c: Context): String {
        StatsStore.save(c, force = true)
        return JSONObject()
            .put("app", "hiz-gostergesi")
            .put("format", FORMAT)
            .put("created", System.currentTimeMillis())
            .put("prefs", prefsToJson(Prefs.get(c).all))
            .put("stats", StatsStore.exportJson(c))
            .toString()
    }

    /** Yedeği geri yükler. Başarılıysa (ayar sayısı, gün sayısı), değilse hata mesajı fırlatır. */
    fun restore(c: Context, text: String): Pair<Int, Int> {
        val o = JSONObject(text)
        require(o.optString("app") == "hiz-gostergesi") { "Bu dosya bir Hız Göstergesi yedeği değil" }
        require(o.optInt("format", 0) in 1..FORMAT) { "Yedek, uygulamanın daha yeni bir sürümünden" }
        val e = Prefs.get(c).edit()
        val n = o.optJSONObject("prefs")?.let { applyPrefs(it, e) } ?: 0
        e.apply()
        val days = o.optJSONObject("stats")?.let { StatsStore.importJson(c, it) } ?: 0
        return n to days
    }

    fun write(c: Context, uri: Uri, text: String) {
        c.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    }

    fun read(c: Context, uri: Uri): String =
        c.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
}
