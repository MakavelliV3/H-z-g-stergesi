package com.emir.hizgostergesi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Telefon yeniden başlayınca göstergeyi otomatik açar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.startForegroundService(Intent(context, SpeedService::class.java))
        }
    }
}
