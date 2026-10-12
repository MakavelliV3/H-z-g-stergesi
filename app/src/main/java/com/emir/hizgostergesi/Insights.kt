package com.emir.hizgostergesi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Arka planda çalışan kontroller: haftalık özet ve olağandışı kullanım uyarısı. */
object Insights {
    private const val REPORT_CHANNEL = "rapor"
    private const val ANOMALY_CHANNEL = "anormal"
    private const val WEEKLY_ID = 3
    private const val ANOMALY_BASE_ID = 1000

    /** Bir uygulamanın "olağandışı" sayılması için bugün en az bu kadar kullanması gerekir. */
    private const val ANOMALY_MIN = 100L * 1048576
    private const val ANOMALY_FACTOR = 3.0

    fun runChecks(c: Context) {
        try {
            ensureChannels(c)
            weekly(c)
            anomalies(c)
            appLimits(c)
        } catch (e: Exception) {
            // bir sonraki turda tekrar denenir
        }
    }

    private fun ensureChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(REPORT_CHANNEL, "Haftalık özet", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(ANOMALY_CHANNEL, "Olağandışı kullanım", NotificationManager.IMPORTANCE_HIGH))
    }

    fun mondayOf(time: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = time
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, -1)
    }

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Bir aralığın toplamı: izin varsa Android kayıtlarından, yoksa uygulamanın kendi kayıtlarından. */
    fun total(c: Context, start: Long, end: Long): Pair<Long, Long> {
        if (NetStats.hasAccess(c)) return NetStats.deviceTotal(c, start, end)
        return StatsStore.sumRange(c, start, end)
    }

    fun percentChange(now: Long, before: Long): String {
        if (before <= 0) return "önceki dönemde kayıt yok"
        val p = ((now - before) * 100.0 / before).toInt()
        return when {
            p > 0 -> "%$p daha fazla"
            p < 0 -> "%${-p} daha az"
            else -> "aynı"
        }
    }

    private fun weekly(c: Context) {
        val prefs = Prefs.get(c)
        if (!prefs.getBoolean(Prefs.WEEKLY_ON, true)) return
        val now = Calendar.getInstance()
        if (now.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || now.get(Calendar.HOUR_OF_DAY) < 9) return

        val thisMonday = mondayOf(now.timeInMillis).timeInMillis
        val weekKey = StatsStore.key(thisMonday)
        if (prefs.getString(Prefs.WEEKLY_SENT, "") == weekKey) return

        val lastStart = thisMonday - 7 * 86_400_000L
        val prevStart = thisMonday - 14 * 86_400_000L
        val (m, w) = total(c, lastStart, thisMonday)
        val (pm, pw) = total(c, prevStart, lastStart)
        prefs.edit().putString(Prefs.WEEKLY_SENT, weekKey).apply()
        if (m + w == 0L) return

        val top = if (NetStats.hasAccess(c)) {
            NetStats.queryApps(c, lastStart, thisMonday)
                .filter { it.category != "Sistem" }
                .take(3)
                .joinToString(", ") { "${it.name} ${Fmt.bytes(it.total)}" }
        } else ""

        val text = buildString {
            append("Toplam ${Fmt.bytes(m + w)} (mobil ${Fmt.bytes(m)} • Wi-Fi ${Fmt.bytes(w)}). ")
            append("Önceki haftaya göre ${percentChange(m + w, pm + pw)}.")
            if (top.isNotEmpty()) append("\nEn çok: $top")
        }
        notify(
            c, REPORT_CHANNEL, WEEKLY_ID, "Geçen hafta ${Fmt.bytes(m + w)} veri kullandın", text,
            Intent(c, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ANALYSIS)
        )
    }

    private fun anomalies(c: Context) {
        val prefs = Prefs.get(c)
        if (!prefs.getBoolean(Prefs.ANOMALY_ON, true) || !NetStats.hasAccess(c)) return
        val todayStart = startOfToday()
        val now = System.currentTimeMillis()
        // Günün ilk saatlerinde karşılaştırma yanıltıcı olur
        if (now - todayStart < 2 * 3_600_000L) return

        val dayKey = StatsStore.key(todayStart)
        val sent = prefs.getString(Prefs.ANOMALY_SENT, "") ?: ""
        val sentToday = if (sent.startsWith("$dayKey|")) sent.substringAfter("|").split(",").toMutableSet() else mutableSetOf()

        val today = NetStats.queryApps(c, todayStart, now, withNames = false)
        val before = NetStats.queryApps(c, todayStart - 7 * 86_400_000L, todayStart, withNames = false)
            .associateBy { it.uid }

        for (app in today) {
            if (app.total < ANOMALY_MIN) break // liste sıralı
            val avg = (before[app.uid]?.total ?: 0L) / 7.0
            if (app.total < avg * ANOMALY_FACTOR) continue
            if (sentToday.contains(app.uid.toString())) continue
            NetStats.describe(c, app)
            if (app.category == "Sistem") continue
            sentToday += app.uid.toString()
            val normal = if (avg >= 1048576) "normalde günde ~${Fmt.bytes(avg.toLong())}" else "normalde neredeyse hiç kullanmıyor"
            notify(
                c, ANOMALY_CHANNEL, ANOMALY_BASE_ID + (app.uid % 1000),
                "Olağandışı veri kullanımı: ${app.name}",
                "Bugün ${Fmt.bytes(app.total)} kullandı, $normal. (Arka planda: ${Fmt.bytes(app.background)})",
                Intent(c, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_UID, app.uid)
            )
        }
        prefs.edit().putString(Prefs.ANOMALY_SENT, "$dayKey|" + sentToday.joinToString(",")).apply()
    }

    /** Kullanıcının uygulamalara koyduğu günlük sınırlar. */
    private fun appLimits(c: Context) {
        val limits = Prefs.appLimits(c)
        if (limits.isEmpty() || !NetStats.hasAccess(c)) return
        val prefs = Prefs.get(c)
        val todayStart = startOfToday()
        val dayKey = StatsStore.key(todayStart)
        val sent = prefs.getString(Prefs.APP_LIMIT_SENT, "") ?: ""
        val sentToday = if (sent.startsWith("$dayKey|")) sent.substringAfter("|").split(",").toMutableSet() else mutableSetOf()

        val today = NetStats.queryApps(c, todayStart, System.currentTimeMillis() + 60_000L, withNames = false)
            .associateBy { it.uid }
        for ((uid, mb) in limits) {
            val app = today[uid] ?: continue
            if (app.total < Fmt.mbToBytes(mb) || sentToday.contains(uid.toString())) continue
            sentToday += uid.toString()
            NetStats.describe(c, app)
            notify(
                c, ANOMALY_CHANNEL, 2000 + (uid % 1000),
                "${app.name} günlük sınırı aştı",
                "Bugün ${Fmt.bytes(app.total)} kullandı (sınır: ${Fmt.mb(mb)}). Mobil ${Fmt.bytes(app.mobile)} • Wi-Fi ${Fmt.bytes(app.wifi)}",
                Intent(c, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_UID, uid)
            )
        }
        prefs.edit().putString(Prefs.APP_LIMIT_SENT, "$dayKey|" + sentToday.joinToString(",")).apply()
    }

    private fun notify(c: Context, channel: String, id: Int, title: String, text: String, open: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val pi = PendingIntent.getActivity(
            c, id, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}
