package com.emir.hizgostergesi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.RemoteViews
import android.widget.TextView
import android.widget.Toast

/**
 * Telefonun bayt sayaçlarını okuyup:
 *  - anlık hızı durum çubuğuna, bildirime, yüzen pencereye ve widget'a yazar,
 *  - seçilen dönemdeki kullanımı tutar, süre dolunca sıfırlar, sınır aşılınca uyarır,
 *  - günlük mobil / Wi-Fi kullanımını, rekor ve ortalama hızı kaydeder.
 * İnternete bağlanmaz, hiçbir veri göndermez.
 */
class SpeedService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var nm: NotificationManager
    private lateinit var wm: WindowManager
    private lateinit var power: PowerManager
    private lateinit var prefs: SharedPreferences

    private var lastTotRx = 0L
    private var lastTotTx = 0L
    private var lastMobRx = 0L
    private var lastMobTx = 0L
    private var lastTickAt = 0L
    private var lastWidgetAt = 0L

    private var usedMobile = 0L
    private var usedWifi = 0L
    private var windowStart = 0L

    private var lastNotification: Notification? = null
    private var overlay: TextView? = null
    private var overlayStyle = ""

    private val tick = Runnable { update() }

    /** Ekran açılınca beklemeden güncelle. */
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        wm = getSystemService(WindowManager::class.java)
        power = getSystemService(PowerManager::class.java)
        prefs = Prefs.get(this)

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ağ hızı", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(WARN_CHANNEL_ID, "Veri sınırı uyarısı", NotificationManager.IMPORTANCE_HIGH)
        )

        lastTotRx = pos(TrafficStats.getTotalRxBytes())
        lastTotTx = pos(TrafficStats.getTotalTxBytes())
        lastMobRx = pos(TrafficStats.getMobileRxBytes())
        lastMobTx = pos(TrafficStats.getMobileTxBytes())
        lastTickAt = SystemClock.elapsedRealtime()

        windowStart = prefs.getLong(Prefs.WINDOW_START, 0L).takeIf { it > 0 } ?: System.currentTimeMillis()
        usedMobile = prefs.getLong(Prefs.USED_MOBILE, 0L)
        usedWifi = prefs.getLong(Prefs.USED_WIFI, 0L)

        val color = prefs.getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR)
        enterForeground(build(0, 0, prefs.getBoolean(Prefs.UNIT_BITS, false), "Başlatılıyor…", "", "", color))

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOn, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOn, filter)
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastNotification?.let { enterForeground(it) }
        when (intent?.action) {
            ACTION_RESET -> resetWindow(System.currentTimeMillis())
            ACTION_TOGGLE_OVERLAY -> {
                prefs.edit().putBoolean(Prefs.OVERLAY, !prefs.getBoolean(Prefs.OVERLAY, false)).apply()
                if (!Settings.canDrawOverlays(this)) {
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        if (intent?.action != null) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try {
            unregisterReceiver(screenOn)
        } catch (e: Exception) {
            // zaten kayıtlı değil
        }
        removeOverlay()
        saveCounters()
        StatsStore.save(this, force = true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun enterForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun pos(v: Long) = if (v < 0) 0L else v

    private fun resetWindow(now: Long) {
        windowStart = now
        usedMobile = 0
        usedWifi = 0
        saveCounters()
    }

    private fun saveCounters() {
        prefs.edit()
            .putLong(Prefs.WINDOW_START, windowStart)
            .putLong(Prefs.USED_MOBILE, usedMobile)
            .putLong(Prefs.USED_WIFI, usedWifi)
            .apply()
    }

    private fun update() {
        val nowEl = SystemClock.elapsedRealtime()
        val dt = (nowEl - lastTickAt).coerceAtLeast(250) / 1000.0
        lastTickAt = nowEl

        // Sayaçları oku; Wi-Fi = toplam - mobil
        val totRx = pos(TrafficStats.getTotalRxBytes())
        val totTx = pos(TrafficStats.getTotalTxBytes())
        val mobRx = pos(TrafficStats.getMobileRxBytes())
        val mobTx = pos(TrafficStats.getMobileTxBytes())
        val dTotRx = (totRx - lastTotRx).coerceAtLeast(0)
        val dTotTx = (totTx - lastTotTx).coerceAtLeast(0)
        val dMobRx = (mobRx - lastMobRx).coerceAtLeast(0)
        val dMobTx = (mobTx - lastMobTx).coerceAtLeast(0)
        val dWifiRx = (dTotRx - dMobRx).coerceAtLeast(0)
        val dWifiTx = (dTotTx - dMobTx).coerceAtLeast(0)
        lastTotRx = totRx
        lastTotTx = totTx
        lastMobRx = mobRx
        lastMobTx = mobTx

        val down = (dTotRx / dt).toLong()
        val up = (dTotTx / dt).toLong()
        lastDown = down
        lastUp = up

        // Günlük istatistik
        val today = StatsStore.today(this)
        today.add(true, dMobRx, dMobTx, dt)
        today.add(false, dWifiRx, dWifiTx, dt)
        StatsStore.save(this)

        // Dönem sayacı
        val hours = prefs.getInt(Prefs.HOURS, 1)
        val periodMs = hours * 3_600_000L
        val now = System.currentTimeMillis()
        if (now - windowStart >= periodMs || now < windowStart) resetWindow(now)
        usedMobile += dMobRx + dMobTx
        usedWifi += dWifiRx + dWifiTx
        saveCounters()

        val mobileOnly = prefs.getBoolean(Prefs.MOBILE_ONLY, false)
        val periodUsed = if (mobileOnly) usedMobile else usedMobile + usedWifi
        checkQuota(periodUsed, mobileOnly, hours)

        // Ekran kapalıyken (istenirse) sadece sayaç tut, göstergeleri çizme
        val slow = prefs.getBoolean(Prefs.SLOW_WHEN_OFF, true) && !power.isInteractive
        if (!slow) {
            val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
            val color = prefs.getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR)
            val scope = if (mobileOnly) "mobil" else "toplam"
            val speedText = "↓ ${Fmt.speed(down, bits)}    ↑ ${Fmt.speed(up, bits)}"
            val periodText = "Son $hours sa ($scope): ${Fmt.bytes(periodUsed)} • " +
                "${Fmt.remaining(windowStart + periodMs - now)} sonra sıfırlanır"
            val todayText = "Bugün  Mobil ${Fmt.bytes(today.mobile)} • Wi-Fi ${Fmt.bytes(today.wifi)}"

            val n = build(down, up, bits, speedText, periodText, todayText, color)
            lastNotification = n
            nm.notify(NOTIF_ID, n)
            updateOverlay(down, up, bits, "$hours sa: ${Fmt.bytes(periodUsed)}", color)
            updateWidgets(nowEl, speedText, todayText, "Son $hours sa ($scope): ${Fmt.bytes(periodUsed)}", color)
        }

        val interval = prefs.getInt(Prefs.INTERVAL_SEC, 1).coerceIn(1, 10) * 1000L
        handler.postDelayed(tick, if (slow) 15_000L else interval)
    }

    // ---------- Veri sınırı ----------

    private fun checkQuota(used: Long, mobileOnly: Boolean, hours: Int) {
        val mb = prefs.getInt(Prefs.QUOTA_MB, 0)
        if (mb <= 0 || used < mb * 1048576L) return
        if (prefs.getLong(Prefs.QUOTA_WARNED, 0L) == windowStart) return
        prefs.edit().putLong(Prefs.QUOTA_WARNED, windowStart).apply()

        val what = if (mobileOnly) "mobil veri" else "toplam"
        val n = Notification.Builder(this, WARN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Veri sınırı aşıldı: ${Fmt.mb(mb)}")
            .setContentText("Son $hours saatte $what kullanım ${Fmt.bytes(used)} oldu.")
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        nm.notify(WARN_ID, n)
    }

    // ---------- Bildirim ----------

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    private fun build(
        down: Long, up: Long, bits: Boolean, speedText: String, periodText: String, todayText: String, color: Int
    ): Notification {
        val small = RemoteViews(packageName, R.layout.notif).apply {
            setTextViewText(R.id.speed, speedText)
            setTextViewText(R.id.usage, periodText)
            setTextColor(R.id.speed, color)
            setTextColor(R.id.usage, color)
        }
        val big = RemoteViews(packageName, R.layout.notif_big).apply {
            setTextViewText(R.id.speed, speedText)
            setTextViewText(R.id.usage, periodText)
            setTextViewText(R.id.today, todayText)
            setTextColor(R.id.speed, color)
            setTextColor(R.id.usage, color)
            setTextColor(R.id.today, color)
        }

        val overlayOn = prefs.getBoolean(Prefs.OVERLAY, false)
        val toggle = PendingIntent.getForegroundService(
            this, 1,
            Intent(this, SpeedService::class.java).setAction(ACTION_TOGGLE_OVERLAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_view),
            if (overlayOn) "Pencereyi gizle" else "Pencereyi göster",
            toggle
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(Icon.createWithBitmap(StatusIcon.draw(prefs, down, up, bits)))
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .addAction(action)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    // ---------- Widget ----------

    private fun updateWidgets(nowEl: Long, speed: String, today: String, period: String, color: Int) {
        if (nowEl - lastWidgetAt < 3000) return
        lastWidgetAt = nowEl
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, SpeedWidget::class.java))
        if (ids.isEmpty()) return
        mgr.updateAppWidget(ids, SpeedWidget.views(this, speed, today, period, color))
    }

    // ---------- Yüzen pencere ----------

    private fun updateOverlay(down: Long, up: Long, bits: Boolean, usage: String, color: Int) {
        val wanted = prefs.getBoolean(Prefs.OVERLAY, false) && Settings.canDrawOverlays(this)
        if (!wanted) {
            removeOverlay()
            return
        }
        val view = overlay ?: createOverlay() ?: return

        val size = prefs.getInt(Prefs.OVERLAY_SIZE, 12)
        val alpha = prefs.getInt(Prefs.OVERLAY_ALPHA, 60)
        val style = "$size|$alpha"
        if (style != overlayStyle) {
            overlayStyle = style
            view.textSize = size.toFloat()
            (view.background as? GradientDrawable)?.setColor(Color.argb(alpha * 255 / 100, 0, 0, 0))
        }
        view.setTextColor(color)
        view.text = "↓ ${Fmt.speed(down, bits)}  ↑ ${Fmt.speed(up, bits)}\n$usage"
    }

    private fun createOverlay(): TextView? {
        val d = resources.displayMetrics.density
        val tv = TextView(this).apply {
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * d).toInt(), (5 * d).toInt(), (10 * d).toInt(), (5 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 12 * d }
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
            overlayStyle = ""
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

    /** Sürükleyince taşır, bırakınca konumu kaydeder, çift dokununca gizler. */
    private inner class Drag(private val p: WindowManager.LayoutParams) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        private val detector = GestureDetector(this@SpeedService, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                prefs.edit().putBoolean(Prefs.OVERLAY, false).apply()
                removeOverlay()
                Toast.makeText(
                    this@SpeedService,
                    "Pencere gizlendi. Bildirimdeki \"Pencereyi göster\" ile geri açabilirsin.",
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }
        })

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            detector.onTouchEvent(e)
            if (overlay == null) return true
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
                    try {
                        wm.updateViewLayout(v, p)
                    } catch (ex: Exception) {
                        // pencere kaldırılmış olabilir
                    }
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
        const val WARN_CHANNEL_ID = "uyari"
        const val NOTIF_ID = 1
        const val WARN_ID = 2
        const val ACTION_RESET = "com.emir.hizgostergesi.RESET"
        const val ACTION_TOGGLE_OVERLAY = "com.emir.hizgostergesi.TOGGLE_OVERLAY"

        /** Ana ekrandaki canlı hız kartı için son ölçülen hızlar. */
        @Volatile
        var lastDown = 0L

        @Volatile
        var lastUp = 0L
    }
}
