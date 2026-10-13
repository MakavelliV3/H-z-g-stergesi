package com.emir.hizgostergesi

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** Uygulama simgesine basılı tutunca çıkan kısayolları işler; ekranda bir şey göstermeden kapanır. */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.get(this)
        when (intent?.action) {
            ACTION_OVERLAY -> {
                val on = prefs.getBoolean(Prefs.OVERLAY, false)
                if (!on && !Settings.canDrawOverlays(this)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    prefs.edit().putBoolean(Prefs.OVERLAY, !on).apply()
                    startForegroundService(Intent(this, SpeedService::class.java))
                    Toast.makeText(this, if (on) "Yüzen pencere kapatıldı" else "Yüzen pencere açıldı", Toast.LENGTH_SHORT).show()
                }
            }
            ACTION_RESET -> {
                startForegroundService(Intent(this, SpeedService::class.java).setAction(SpeedService.ACTION_RESET_ROLL))
                Toast.makeText(this, "Son ${prefs.getInt(Prefs.ROLL_HOURS, 1)} saat sayacı sıfırlandı", Toast.LENGTH_SHORT).show()
            }
            ACTION_PLAN -> startActivity(
                (if (PlanState.enabled(this)) Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_HOME)
                else Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, SettingsActivity.CAT_PLAN))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ACTION_APPS -> startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_APPS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    companion object {
        const val ACTION_OVERLAY = "com.emir.hizgostergesi.SC_OVERLAY"
        const val ACTION_PLAN = "com.emir.hizgostergesi.SC_PLAN"
        const val ACTION_RESET = "com.emir.hizgostergesi.SC_RESET"
        const val ACTION_APPS = "com.emir.hizgostergesi.SC_APPS"
    }
}
