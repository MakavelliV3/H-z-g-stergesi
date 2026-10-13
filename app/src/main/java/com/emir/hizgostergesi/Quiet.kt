package com.emir.hizgostergesi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import java.util.Calendar

/**
 * Sessiz saatler: bu saatlerde uyarılar sessiz kanaldan gelir (ses, titreşim ve açılır pencere yok),
 * sabah bildirim panelinde bekler.
 */
object Quiet {
    const val CHANNEL = "sessiz_uyarilar"

    fun active(c: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = Prefs.get(c)
        if (!p.getBoolean(Prefs.QUIET_ON, false)) return false
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return PlanMath.isNight(h, p.getInt(Prefs.QUIET_START, 23), p.getInt(Prefs.QUIET_END, 8))
    }

    /** Sessiz saatteyse sessiz kanalı, değilse verilen kanalı döner. */
    fun channel(c: Context, normal: String): String {
        if (!active(c)) return normal
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Sessiz saatlerdeki uyarılar", NotificationManager.IMPORTANCE_LOW)
        )
        return CHANNEL
    }
}
