package com.emir.hizgostergesi

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock

/**
 * Ön plandaki uygulamayı "Kullanım erişimi" kayıtlarından bulur (oyun modu için).
 * En fazla 2 saniyede bir sorgular; arada son bilinen sonucu döner.
 */
object ForegroundApp {
    private var lastQueryEl = 0L
    private var lastQueryWall = 0L
    private var current: String? = null

    fun get(c: Context): String? {
        val el = SystemClock.elapsedRealtime()
        if (el - lastQueryEl < 2000 && lastQueryEl != 0L) return current
        lastQueryEl = el
        val now = System.currentTimeMillis()
        // İlk sorguda son 10 dakikaya bak, sonrasında yalnızca yeni olaylara
        val from = if (lastQueryWall == 0L || now - lastQueryWall > 600_000) now - 600_000 else lastQueryWall - 1000
        lastQueryWall = now
        try {
            val usm = c.getSystemService(UsageStatsManager::class.java) ?: return current
            val events = usm.queryEvents(from, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) current = e.packageName
            }
        } catch (e: Exception) { }
        return current
    }

    fun parse(s: String?): Set<String> = s?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
}
