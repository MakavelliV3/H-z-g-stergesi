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
        val at: Long
    ) {
        val remaining get() = (limit - used).coerceAtLeast(0)
        val ratio get() = if (limit > 0) used.toDouble() / limit else 0.0
    }

    @Volatile
    var last: Snapshot? = null
        private set

    private const val CHANNEL = "paket"
    private const val NOTIF_ID = 5

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
        val system = NetStats.hasAccess(c)
        val mobile = if (system) NetStats.deviceTotal(c, start, now).first else StatsStore.sumRange(c, start, now).first
        val night = if (p.getBoolean(Prefs.PLAN_NIGHT, false)) {
            StatsStore.nightMobile(c, start, now, p.getInt(Prefs.NIGHT_START, 0), p.getInt(Prefs.NIGHT_END, 8))
        } else 0L
        val snap = Snapshot(start, end, (mobile - night).coerceAtLeast(0), night, Fmt.mbToBytes(p.getInt(Prefs.PLAN_MB, 0)), system, now)
        last = snap
        checkAlerts(c, snap)
        return snap
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
