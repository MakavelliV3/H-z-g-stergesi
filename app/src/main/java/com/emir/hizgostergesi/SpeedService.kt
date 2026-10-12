package com.emir.hizgostergesi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Her saniye telefonun toplam indirilen/yüklenen bayt sayısını okur,
 * farkı hesaplar ve hızı durum çubuğundaki bildirim simgesine yazar.
 * İnternete bağlanmaz, hiçbir veri göndermez: sadece sayaç okur.
 */
class SpeedService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager
    private var lastRx = 0L
    private var lastTx = 0L

    private val tick = object : Runnable {
        override fun run() {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val down = (rx - lastRx).coerceAtLeast(0)
            val up = (tx - lastTx).coerceAtLeast(0)
            lastRx = rx
            lastTx = tx
            nm.notify(NOTIF_ID, build(down, up))
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ağ hızı", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()

        val first = build(0, 0)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, first)
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun build(down: Long, up: Long): Notification {
        val (num, unit) = split(down)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(textIcon(num, unit))
            .setContentTitle("↓ ${format(down)}    ↑ ${format(up)}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    /** Bayt/saniye değerini "sayı" + "birim" olarak ayırır. */
    private fun split(bytesPerSec: Long): Pair<String, String> =
        if (bytesPerSec >= 1024L * 1024L) {
            val mb = bytesPerSec / 1048576.0
            (if (mb >= 10) "%.0f".format(mb) else "%.1f".format(mb)) to "MB/s"
        } else {
            (bytesPerSec / 1024).toString() to "KB/s"
        }

    private fun format(b: Long) = split(b).let { "${it.first} ${it.second}" }

    /** Sayıyı üstte, birimi altta olacak şekilde simge resmi çizer. */
    private fun textIcon(num: String, unit: String): Icon {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = 60f
        }
        while (paint.measureText(num) > size && paint.textSize > 20f) paint.textSize -= 2f
        canvas.drawText(num, size / 2f, 58f, paint)
        paint.textSize = 34f
        canvas.drawText(unit, size / 2f, 92f, paint)
        return Icon.createWithBitmap(bmp)
    }

    companion object {
        const val CHANNEL_ID = "speed"
        const val NOTIF_ID = 1
    }
}
