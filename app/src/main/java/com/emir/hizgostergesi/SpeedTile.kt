package com.emir.hizgostergesi

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Hızlı ayarlar kutucuğu: dokununca yüzen pencereyi açar/kapatır,
 * panel açıkken alt yazıda anlık hızı gösterir.
 */
class SpeedTile : TileService() {

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        handler.post(ticker)
    }

    override fun onStopListening() {
        handler.removeCallbacks(ticker)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val prefs = Prefs.get(this)
        val on = prefs.getBoolean(Prefs.OVERLAY, false)
        if (!on && !Settings.canDrawOverlays(this)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 7, i, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(i)
            }
            return
        }
        prefs.edit().putBoolean(Prefs.OVERLAY, !on).apply()
        // Servis çalışmıyorsa başlat (ayar değişikliğini o uygular)
        try {
            startForegroundService(Intent(this, SpeedService::class.java))
        } catch (e: Exception) { }
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        val prefs = Prefs.get(this)
        val on = prefs.getBoolean(Prefs.OVERLAY, false)
        val bits = prefs.getBoolean(Prefs.UNIT_BITS, false)
        t.icon = Icon.createWithResource(this, R.drawable.ic_stat)
        t.label = "Yüzen hız"
        t.subtitle = "↓ ${Fmt.speed(SpeedService.lastDown, bits)}  ↑ ${Fmt.speed(SpeedService.lastUp, bits)}"
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }
}
