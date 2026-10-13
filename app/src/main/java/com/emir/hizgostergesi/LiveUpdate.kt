package com.emir.hizgostergesi

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Android 16 "Canlı güncellemeler": durum çubuğunun solunda hap, Samsung'da Now Bar.
 * Derleme Android 15 SDK'sıyla yapıldığı için yeni metotlara yansıma (reflection) ile ulaşılır;
 * eski telefonlarda hiçbir şey yapmaz.
 */
object LiveUpdate {
    val supported get() = Build.VERSION.SDK_INT >= 36

    fun enabled(c: Context) = supported && Prefs.get(c).getBoolean(Prefs.N_LIVE, false)

    /** Bildirimi hap olarak öne çıkarılmak üzere işaretler; [chip] haptaki kısa yazı (ör. "4,4M"). */
    fun request(b: Notification.Builder, chip: String) {
        if (!supported) return
        try {
            Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(b, true)
        } catch (e: Exception) {
        }
        try {
            Notification.Builder::class.java.getMethod("setShortCriticalText", String::class.java).invoke(b, chip)
        } catch (e: Exception) {
        }
    }

    /** Kullanıcı bu uygulama için canlı güncellemelere izin vermiş mi (bilinmiyorsa true). */
    fun allowed(c: Context): Boolean {
        if (!supported) return false
        return try {
            NotificationManager::class.java.getMethod("canPostPromotedNotifications")
                .invoke(c.getSystemService(NotificationManager::class.java)) as Boolean
        } catch (e: Exception) {
            true
        }
    }

    /** Android'in "öne çıkarıldı" bayrağı (Notification.FLAG_PROMOTED_ONGOING). */
    private const val FLAG_PROMOTED = 0x00040000

    /** Hız bildirimi şu an gerçekten hap olarak öne çıkarılmış mı. */
    fun isPromoted(c: Context): Boolean = try {
        val n = c.getSystemService(NotificationManager::class.java).activeNotifications
            .firstOrNull { it.id == SpeedService.NOTIF_ID }?.notification
        n != null && (n.flags and FLAG_PROMOTED) != 0
    } catch (e: Exception) {
        false
    }

    /** Ayarlarda gösterilecek durum ve (varsa) ne yapılması gerektiği. */
    fun statusText(c: Context): String = when {
        !supported -> "Bu telefonda Android ${Build.VERSION.RELEASE} var; canlı bildirim Android 16 (One UI 8) ister."
        !allowed(c) -> "Android bu uygulamanın canlı bildirimlerine izin vermiyor. Aşağıdaki düğmeyle aç."
        isPromoted(c) -> "✓ Çalışıyor: hız durum çubuğunun solunda hap olarak görünmeli."
        else -> "Android bildirimi henüz hap yapmadı. Samsung'da: Ayarlar → Geliştirici seçenekleri → " +
            "\"Tüm uygulamalar için canlı bildirimler\" (Live notifications for all apps) seçeneğini açıp telefonu yeniden başlat."
    }

    fun settingsIntent(c: Context): Intent =
        Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
            .putExtra("android.provider.extra.APP_PACKAGE", c.packageName)
            .setData(Uri.parse("package:${c.packageName}"))
}
