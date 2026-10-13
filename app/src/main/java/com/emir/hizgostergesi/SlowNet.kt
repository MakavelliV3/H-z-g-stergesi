package com.emir.hizgostergesi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Yavaş mobil internet tespiti. Android'e bağımlı değildir (birim testli).
 *
 * 2 dakikalık pencerelerde mobil veri izlenir: o sürede gerçekten veri çekilmişse
 * (en az [minBytes]) ama hız hiç [thresholdBps]'yi geçmediyse bağlantı yavaş sayılır.
 * Boşta duran telefon (az veri) yavaş sayılmaz. Uyarılar arası en az [cooldownMs].
 */
class SlowDetector(
    var thresholdBps: Long,
    private val windowMs: Long = 120_000L,
    private val minBytes: Long = 1_000_000L,
    private val cooldownMs: Long = 3_600_000L
) {
    private var windowStart = -1L
    private var bytes = 0L
    private var peak = 0L
    private var lastWarn = -1L
    private var lastFeed = -1L
    var lastPeak = 0L
        private set

    /** Her ölçümde çağrılır. Uyarı gerekiyorsa true döner. */
    fun feed(nowEl: Long, downBps: Long, mobileBytes: Long, onCellular: Boolean): Boolean {
        // Ölçüm kesintisi (ekran kapalı, Wi-Fi'ye geçiş) pencereyi sıfırlar
        val gap = lastFeed >= 0 && nowEl - lastFeed > 10_000L
        lastFeed = nowEl
        if (!onCellular) {
            windowStart = -1
            return false
        }
        if (windowStart < 0 || gap) {
            windowStart = nowEl
            bytes = 0
            peak = 0
        }
        bytes += mobileBytes
        if (downBps > peak) peak = downBps
        if (nowEl - windowStart < windowMs) return false
        val slow = bytes >= minBytes && peak < thresholdBps
        lastPeak = peak
        windowStart = -1
        if (!slow) return false
        if (lastWarn >= 0 && nowEl - lastWarn < cooldownMs) return false
        lastWarn = nowEl
        return true
    }
}

object SlowNet {
    private const val CHANNEL = "yavas"
    private const val ID = 9

    fun notify(c: Context, peakBps: Long, kind: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Yavaş internet uyarısı", NotificationManager.IMPORTANCE_DEFAULT))
        val bits = Prefs.get(c).getBoolean(Prefs.UNIT_BITS, false)
        val text = "Son 2 dakikada mobil veri kullanılırken hız ${Fmt.speed(peakBps, bits)}'ı geçmedi ($kind). " +
            "Kapsama zayıf olabilir ya da paketin hız sınırına (kota) takılmış olabilirsin."
        val pi = PendingIntent.getActivity(
            c, ID, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nm.notify(ID, Notification.Builder(c, Quiet.channel(c, CHANNEL))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Mobil internet yavaş")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build())
    }
}
