package com.emir.hizgostergesi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import android.widget.RemoteViews

/** 1×1 widget: sadece anlık indirme hızı. */
class SmallWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.updateAll(c, true)
}

/** 4×2 widget: hız, son dakikaların grafiği, bugün ve paket. */
class LargeWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.updateAll(c, true)
}

object Widgets {
    private fun open(c: Context) =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    /** Küçük ve büyük widget'ları günceller (servis birkaç saniyede bir çağırır). */
    fun updateAll(c: Context, force: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(c)
        val small = mgr.getAppWidgetIds(ComponentName(c, SmallWidget::class.java))
        val large = mgr.getAppWidgetIds(ComponentName(c, LargeWidget::class.java))
        if (small.isEmpty() && large.isEmpty()) return
        if (small.isNotEmpty()) mgr.updateAppWidget(small, smallViews(c))
        if (large.isNotEmpty()) mgr.updateAppWidget(large, largeViews(c))
    }

    private fun color(c: Context) = Prefs.onDark(Prefs.get(c).getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR))

    fun smallViews(c: Context): RemoteViews {
        val bits = Prefs.get(c).getBoolean(Prefs.UNIT_BITS, false)
        val color = color(c)
        val today = StatsStore.today(c)
        val (v, u) = Fmt.speedParts(SpeedService.lastDown, bits)
        return RemoteViews(c.packageName, R.layout.widget_small).apply {
                setTextViewText(R.id.ws_value, v)
                setTextViewText(R.id.ws_unit, "↓ $u")
                setTextViewText(R.id.ws_today, Fmt.bytes(today.mobile + today.wifi))
                setTextColor(R.id.ws_value, color)
                setTextColor(R.id.ws_unit, color)
                setTextColor(R.id.ws_today, color)
                setOnClickPendingIntent(R.id.ws_root, open(c))
            }
    }

    fun largeViews(c: Context): RemoteViews {
        val bits = Prefs.get(c).getBoolean(Prefs.UNIT_BITS, false)
        val color = color(c)
        val today = StatsStore.today(c)
        val down = SpeedService.lastDown
        val up = SpeedService.lastUp
        val plan = PlanState.last
        return RemoteViews(c.packageName, R.layout.widget_large).apply {
                setTextViewText(R.id.wl_speed, "↓ ${Fmt.speed(down, bits)}    ↑ ${Fmt.speed(up, bits)}")
                setTextViewText(R.id.wl_today, "Bugün  Mobil ${Fmt.bytes(today.mobile)} • Wi-Fi ${Fmt.bytes(today.wifi)}")
                if (plan != null) {
                    val days = PlanMath.daysLeft(System.currentTimeMillis(), plan.end)
                    setTextViewText(R.id.wl_extra, "Paket  kalan ${Fmt.bytes(plan.remaining)} / ${Fmt.bytes(plan.limit)} · $days gün")
                    setViewVisibility(R.id.wl_bar, View.VISIBLE)
                    setProgressBar(R.id.wl_bar, 1000, (plan.ratio * 1000).toInt().coerceIn(0, 1000), false)
                    if (android.os.Build.VERSION.SDK_INT >= 31) setColorStateList(R.id.wl_bar, "setProgressTintList", android.content.res.ColorStateList.valueOf(
                        if (plan.ratio >= 1.0) 0xFFE5484D.toInt() else if (plan.ratio >= 0.8) 0xFFF5A524.toInt() else color))
                } else {
                    val label = SpeedService.rollLabel
                    setTextViewText(R.id.wl_extra, if (label.isNotEmpty()) "$label  ${Fmt.bytes(SpeedService.rollValue)}" else "")
                    setViewVisibility(R.id.wl_bar, View.GONE)
                }
                setTextColor(R.id.wl_speed, color)
                setTextColor(R.id.wl_today, color)
                setTextColor(R.id.wl_extra, color)
                setImageViewBitmap(R.id.wl_graph, graph(SpeedService.recent(), color))
                setOnClickPendingIntent(R.id.wl_root, open(c))
            }
    }

    /** Son ölçümlerin dolgulu çizgi grafiği. */
    fun graph(samples: LongArray, color: Int, w: Int = 480, h: Int = 120): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val max = maxOf(samples.maxOrNull() ?: 0L, 1L).toFloat()
        val n = samples.size
        if (n < 2) return bmp
        val pad = 4f
        val line = Path()
        for (i in 0 until n) {
            val x = i * (w - 1f) / (n - 1)
            val y = h - pad - samples[i] / max * (h - 2 * pad)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path(line).apply {
            lineTo(w.toFloat(), h.toFloat())
            lineTo(0f, h.toFloat())
            close()
        }
        val base = color and 0x00FFFFFF
        cv.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), base or 0x66000000, base, Shader.TileMode.CLAMP)
        })
        cv.drawPath(line, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeJoin = Paint.Join.ROUND
            this.color = color
        })
        // Taban çizgisi
        cv.drawLine(0f, h - 1f, w.toFloat(), h - 1f, Paint().apply { this.color = Color.argb(60, 255, 255, 255) })
        return bmp
    }
}
