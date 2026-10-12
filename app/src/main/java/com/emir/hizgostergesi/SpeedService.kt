package com.emir.hizgostergesi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.RemoteViews
import android.widget.TextView

/**
 * Her saniye telefonun toplam indirilen/yüklenen bayt sayacını okur:
 *  - anlık hızı durum çubuğu simgesine yazar,
 *  - seçilen süre boyunca harcanan veriyi toplar, süre dolunca sıfırlar,
 *  - isteğe bağlı olarak ekranda yüzen bir pencerede gösterir.
 * İnternete bağlanmaz, hiçbir veri göndermez.
 */
class SpeedService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager
    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences

    private var lastRx = 0L
    private var lastTx = 0L
    private var usedRx = 0L
    private var usedTx = 0L
    private var windowStart = 0L

    private var overlay: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        wm = getSystemService(WindowManager::class.java)
        prefs = Prefs.get(this)

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ağ hızı", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )

        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        windowStart = prefs.getLong(Prefs.WINDOW_START, 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
        usedRx = prefs.getLong(Prefs.USED_RX, 0L)
        usedTx = prefs.getLong(Prefs.USED_TX, 0L)

        val first = build(0, 0, "", prefs.getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR))
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, first)
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESET) resetWindow(System.currentTimeMillis())
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        removeOverlay()
        save()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resetWindow(now: Long) {
        windowStart = now
        usedRx = 0
        usedTx = 0
        save()
    }

    private fun save() {
        prefs.edit()
            .putLong(Prefs.WINDOW_START, windowStart)
            .putLong(Prefs.USED_RX, usedRx)
            .putLong(Prefs.USED_TX, usedTx)
            .apply()
    }

    private fun update() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val down = (rx - lastRx).coerceAtLeast(0)
        val up = (tx - lastTx).coerceAtLeast(0)
        lastRx = rx
        lastTx = tx

        val hours = prefs.getInt(Prefs.HOURS, 1)
        val periodMs = hours * 3_600_000L
        val now = System.currentTimeMillis()
        if (now - windowStart >= periodMs || now < windowStart) resetWindow(now)

        usedRx += down
        usedTx += up
        save()

        val used = Fmt.bytes(usedRx + usedTx)
        val left = Fmt.remaining(windowStart + periodMs - now)
        val color = prefs.getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR)

        nm.notify(NOTIF_ID, build(down, up, "Son $hours saat: $used  •  $left sonra sıfırlanır", color))
        updateOverlay(down, up, "$hours sa: $used", color)
    }

    // ---------- Bildirim ----------

    private fun build(down: Long, up: Long, usage: String, color: Int): Notification {
        val (num, unit) = Fmt.speedParts(down)
        val views = RemoteViews(packageName, R.layout.notif).apply {
            setTextViewText(R.id.speed, "↓ ${Fmt.speed(down)}    ↑ ${Fmt.speed(up)}")
            setTextViewText(R.id.usage, usage)
            setTextColor(R.id.speed, color)
            setTextColor(R.id.usage, color)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(textIcon(num, unit))
            .setCustomContentView(views)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    /** Sayıyı üstte, birimi altta olacak şekilde durum çubuğu simgesi çizer. */
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

    // ---------- Yüzen pencere ----------

    private fun updateOverlay(down: Long, up: Long, usage: String, color: Int) {
        val wanted = prefs.getBoolean(Prefs.OVERLAY, false) && Settings.canDrawOverlays(this)
        if (!wanted) {
            removeOverlay()
            return
        }
        val view = overlay ?: createOverlay() ?: return
        view.setTextColor(color)
        view.text = "↓ ${Fmt.speed(down)}  ↑ ${Fmt.speed(up)}\n$usage"
    }

    private fun createOverlay(): TextView? {
        val d = resources.displayMetrics.density
        val tv = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (5 * d).toInt(), (10 * d).toInt(), (5 * d).toInt())
            background = GradientDrawable().apply {
                setColor(0x99000000.toInt())
                cornerRadius = 12 * d
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(Prefs.OVERLAY_X, (16 * d).toInt())
            y = prefs.getInt(Prefs.OVERLAY_Y, (120 * d).toInt())
        }
        tv.setOnTouchListener(Drag(params))
        return try {
            wm.addView(tv, params)
            overlay = tv
            tv
        } catch (e: Exception) {
            null
        }
    }

    private fun removeOverlay() {
        overlay?.let { v ->
            try {
                wm.removeView(v)
            } catch (e: Exception) {
                // pencere zaten kaldırılmış
            }
        }
        overlay = null
    }

    /** Pencereyi parmakla sürükleme; bırakınca konumu kaydeder. */
    private inner class Drag(private val p: WindowManager.LayoutParams) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x
                    startY = p.y
                    touchX = e.rawX
                    touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = startX + (e.rawX - touchX).toInt()
                    p.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(v, p)
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt(Prefs.OVERLAY_X, p.x).putInt(Prefs.OVERLAY_Y, p.y).apply()
                }
            }
            return true
        }
    }

    companion object {
        const val CHANNEL_ID = "speed"
        const val NOTIF_ID = 1
        const val ACTION_RESET = "com.emir.hizgostergesi.RESET"
    }
}
