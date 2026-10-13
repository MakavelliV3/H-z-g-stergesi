package com.emir.hizgostergesi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Paket (tarife) durumu: içinde bulunulan fatura dönemindeki mobil veri kullanımı.
 * Hesap Android kayıtlarını okuyabildiği için arka planda yapılır; sonuç [last]'ta tutulur.
 */
object PlanState {

    data class Snapshot(
        val start: Long,
        val end: Long,
        val used: Long,          // pakete sayılan (gece hariç) mobil kullanım
        val night: Long,         // gece paketi saatlerindeki mobil kullanım
        val limit: Long,
        val fromSystem: Boolean, // Android kayıtlarından mı (true) yoksa uygulamanın kendi ölçümünden mi
        val at: Long,
        val hotspot: Long = 0,   // bu dönem hotspot ile paylaşılan mobil veri (sistem kaydı varsa)
        val extra: Long = 0,     // dönem içinde eklenen ek paket
        val calibrated: Boolean = false
    ) {
        val remaining get() = (limit - used).coerceAtLeast(0)
        val ratio get() = if (limit > 0) used.toDouble() / limit else 0.0
    }

    @Volatile
    var last: Snapshot? = null
        private set

    private const val CHANNEL = "paket"
    private const val NOTIF_ID = 5
    private const val SUMMARY_CHANNEL = "donem_ozeti"
    private const val SUMMARY_ID = 6

    fun enabled(c: Context): Boolean {
        val p = Prefs.get(c)
        return p.getBoolean(Prefs.PLAN_ON, false) && p.getInt(Prefs.PLAN_MB, 0) > 0
    }

    /** Durumu yeniden hesaplar. Ana thread dışında çağrılmalı (Android kayıtlarını okur). */
    fun compute(c: Context): Snapshot? {
        if (!enabled(c)) {
            last = null
            return null
        }
        val p = Prefs.get(c)
        val now = System.currentTimeMillis()
        val day = p.getInt(Prefs.PLAN_DAY, 1)
        val start = PlanMath.cycleStart(now, day)
        val end = PlanMath.cycleEnd(start, day)
        checkCycleEnd(c, start)
        val system = NetStats.hasAccess(c)
        val night = if (p.getBoolean(Prefs.PLAN_NIGHT, false)) {
            StatsStore.nightMobile(c, start, now, p.getInt(Prefs.NIGHT_START, 0), p.getInt(Prefs.NIGHT_END, 8))
        } else 0L
        val measured = measure(c, start, now, system, night)
        // Operatörle eşitleme ve ek paket sadece girildikleri dönemde geçerli
        val offset = if (p.getLong(Prefs.PLAN_OFFSET_CYCLE, 0L) == start) p.getLong(Prefs.PLAN_OFFSET, 0L) else 0L
        val extra = if (p.getLong(Prefs.PLAN_EXTRA_CYCLE, 0L) == start) p.getLong(Prefs.PLAN_EXTRA, 0L) else 0L
        val hotspot = if (system) NetStats.tethering(c, start, now) else 0L
        val snap = Snapshot(
            start, end, PlanMath.adjusted(measured, offset), night,
            Fmt.mbToBytes(p.getInt(Prefs.PLAN_MB, 0)) + extra, system, now, hotspot, extra, offset != 0L
        )
        last = snap
        checkAlerts(c, snap)
        return snap
    }

    /** Pakete sayılan ham ölçüm (düzeltmesiz): mobil kullanım eksi gece paketi. */
    private fun measure(c: Context, start: Long, now: Long, system: Boolean, night: Long): Long {
        // Android kayıtları genelde daha eksiksizdir; yine de hangisi büyükse o alınır (eksik kayda karşı)
        val own = StatsStore.sumRange(c, start, now).first
        val mobile = if (system) maxOf(NetStats.deviceTotal(c, start, now).first, own) else own
        return (mobile - night).coerceAtLeast(0)
    }

    /** Operatörün söylediği kalan miktara göre eşitler. Ana thread dışında çağrılmalı. */
    fun calibrate(c: Context, operatorRemaining: Long) {
        val p = Prefs.get(c)
        val now = System.currentTimeMillis()
        val day = p.getInt(Prefs.PLAN_DAY, 1)
        val start = PlanMath.cycleStart(now, day)
        val night = if (p.getBoolean(Prefs.PLAN_NIGHT, false)) {
            StatsStore.nightMobile(c, start, now, p.getInt(Prefs.NIGHT_START, 0), p.getInt(Prefs.NIGHT_END, 8))
        } else 0L
        val measured = measure(c, start, now, NetStats.hasAccess(c), night)
        val extra = if (p.getLong(Prefs.PLAN_EXTRA_CYCLE, 0L) == start) p.getLong(Prefs.PLAN_EXTRA, 0L) else 0L
        val limit = Fmt.mbToBytes(p.getInt(Prefs.PLAN_MB, 0)) + extra
        p.edit().putLong(Prefs.PLAN_OFFSET, PlanMath.offsetFor(limit, operatorRemaining, measured))
            .putLong(Prefs.PLAN_OFFSET_CYCLE, start).putString(Prefs.PLAN_WARNED, "").apply()
        compute(c)
    }

    /** Bu döneme ek paket ekler (aynı dönemde birden çok kez eklenebilir). */
    fun addExtra(c: Context, bytes: Long) {
        val p = Prefs.get(c)
        val start = PlanMath.cycleStart(System.currentTimeMillis(), p.getInt(Prefs.PLAN_DAY, 1))
        val cur = if (p.getLong(Prefs.PLAN_EXTRA_CYCLE, 0L) == start) p.getLong(Prefs.PLAN_EXTRA, 0L) else 0L
        p.edit().putLong(Prefs.PLAN_EXTRA, (cur + bytes).coerceAtLeast(0)).putLong(Prefs.PLAN_EXTRA_CYCLE, start)
            .putString(Prefs.PLAN_WARNED, "").apply()
        compute(c)
    }

    /** Yeni döneme geçildiyse biten dönemin özetini bir kez bildirir. */
    private fun checkCycleEnd(c: Context, start: Long) {
        val p = Prefs.get(c)
        val prev = p.getLong(Prefs.PLAN_LAST_CYCLE, 0L)
        if (prev == start) return
        p.edit().putLong(Prefs.PLAN_LAST_CYCLE, start).apply()
        if (prev == 0L || prev > start || !p.getBoolean(Prefs.PLAN_SUMMARY_ON, true)) return
        try {
            val system = NetStats.hasAccess(c)
            val own = StatsStore.sumRange(c, prev, start).first
            val used = if (system) maxOf(NetStats.deviceTotal(c, prev, start).first, own) else own
            val limitMb = p.getInt(Prefs.PLAN_MB, 0)
            val top = if (system) NetStats.queryApps(c, prev, start).filter { it.mobile > 0 }.maxByOrNull { it.mobile } else null
            val text = buildString {
                append("Bu dönem ${Fmt.bytes(used)} mobil veri kullandın")
                if (limitMb > 0) append(" (paket ${Fmt.mb(limitMb)})")
                append(".")
                if (top != null && top.name.isNotEmpty()) append(" En çok ${top.name}: ${Fmt.bytes(top.mobile)}.")
                append(" Yeni dönem başladı.")
            }
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(SUMMARY_CHANNEL, "Dönem sonu özeti", NotificationManager.IMPORTANCE_DEFAULT))
            nm.notify(SUMMARY_ID, Notification.Builder(c, SUMMARY_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Paket dönemi bitti")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(PendingIntent.getActivity(c, SUMMARY_ID,
                    Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
                .setAutoCancel(true)
                .build())
        } catch (e: Exception) {
        }
    }

    /** %80 ve %100'de, her dönem bir kez bildirim. */
    private fun checkAlerts(c: Context, s: Snapshot) {
        val p = Prefs.get(c)
        if (!p.getBoolean(Prefs.PLAN_ALERTS, true)) return
        val level = PlanMath.alertLevel(s.used, s.limit)
        if (level == 0) return
        val key = "${s.start}|$level"
        val warned = p.getString(Prefs.PLAN_WARNED, "") ?: ""
        // Aynı dönemde aynı ya da daha yüksek seviye bildirildiyse tekrar etme
        val parts = warned.split("|")
        if (parts.size == 2 && parts[0] == s.start.toString() && (parts[1].toIntOrNull() ?: 0) >= level) return
        p.edit().putString(Prefs.PLAN_WARNED, key).apply()

        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Paket uyarısı", NotificationManager.IMPORTANCE_HIGH))
        val title = if (level >= 100) "Paketin bitti" else "Paketinin %80'i kullanıldı"
        val days = PlanMath.daysLeft(System.currentTimeMillis(), s.end)
        val text = if (level >= 100) {
            "${Fmt.bytes(s.used)} kullandın (paket ${Fmt.bytes(s.limit)}). Yenilenmeye $days gün var."
        } else {
            "Kalan ${Fmt.bytes(s.remaining)}, yenilenmeye $days gün var. Günde en fazla ~${Fmt.bytes(PlanMath.dailyAllowance(s.remaining, System.currentTimeMillis(), s.end))}."
        }
        val open = PendingIntent.getActivity(
            c, NOTIF_ID, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(
            NOTIF_ID,
            Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    /** Bildirim satırı için kısa metin. */
    fun line(s: Snapshot): String {
        val days = PlanMath.daysLeft(System.currentTimeMillis(), s.end)
        return "Paket: ${Fmt.bytes(s.used)} / ${Fmt.bytes(s.limit)} · kalan ${Fmt.bytes(s.remaining)} · $days gün"
    }
}
