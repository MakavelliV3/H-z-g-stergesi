package com.emir.hizgostergesi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Telefon yeniden başlayınca ya da uygulama güncellenince göstergeyi kendiliğinden açar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ok = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        // Kurulum sihirbazı hiç bitmediyse (ilk kurulum) kendiliğinden başlatma
        if (ok && Prefs.get(context).getBoolean(Prefs.ONBOARDED, false)) {
            try {
                context.startForegroundService(Intent(context, SpeedService::class.java))
            } catch (e: Exception) {
                // sistem izin vermezse uygulama açılınca başlar
            }
        }
    }
}
