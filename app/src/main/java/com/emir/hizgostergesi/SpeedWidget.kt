package com.emir.hizgostergesi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Ana ekran widget'ı. İçeriği her birkaç saniyede servis günceller. */
class SpeedWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        val today = StatsStore.today(c)
        val color = Prefs.onDark(Prefs.get(c).getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR))
        mgr.updateAppWidget(
            ids,
            views(
                c, "↓ —    ↑ —",
                "Bugün  Mobil ${Fmt.bytes(today.mobile)} • Wi-Fi ${Fmt.bytes(today.wifi)}",
                "Güncellemek için uygulamayı bir kez aç",
                color
            )
        )
    }

    companion object {
        fun views(c: Context, speed: String, today: String, period: String, color: Int) =
            RemoteViews(c.packageName, R.layout.widget).apply {
                setTextViewText(R.id.w_speed, speed)
                setTextViewText(R.id.w_today, today)
                setTextViewText(R.id.w_period, period)
                setTextColor(R.id.w_speed, color)
                setTextColor(R.id.w_today, color)
                setTextColor(R.id.w_period, color)
                setOnClickPendingIntent(
                    R.id.w_root,
                    PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                )
            }
    }
}
