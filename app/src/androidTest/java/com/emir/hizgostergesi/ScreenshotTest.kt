package com.emir.hizgostergesi

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Tasarımı gözle kontrol etmek için her ekranın ekran görüntüsünü alır.
 * Görüntüler CI'da cihazdan çekilip rapora eklenir.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val pkg = ctx.packageName
    private val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }

    /** Kabuk komutunu çalıştırır ve bitmesini bekler. */
    private fun shell(cmd: String) {
        val pfd = inst.uiAutomation.executeShellCommand(cmd)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }
    private fun pause(ms: Long) = SystemClock.sleep(ms)

    private fun shot(name: String) {
        pause(900)
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
        val f = File(dir, "$name.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Testten sonra uygulama kaldırılınca bu klasör silinir; kabuğun klasörüne kopyala
        shell("mkdir -p /data/local/tmp/shots")
        shell("cp ${f.absolutePath} /data/local/tmp/shots/")
    }

    /** Grafikler dolu görünsün diye son 40 güne gerçekçi örnek veri yazar. */
    private fun seed() {
        val r = Random(42)
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        for (i in 0 until 40) {
            val t = now - i * day
            val s = StatsStore.dayAt(ctx, t)
            if (s.mobile + s.wifi > 50_000_000) continue
            for (h in 0 until 24) {
                val busy = if (h in 19..23) 3.0 else if (h in 8..17) 1.4 else 0.3
                val mob = (r.nextDouble(0.0, 18_000_000.0) * busy).toLong()
                val wifi = (r.nextDouble(0.0, 60_000_000.0) * busy).toLong()
                val secs = 60.0 + r.nextDouble(0.0, 300.0)
                s.add(true, mob, mob / 20, secs, h)
                s.add(false, wifi, wifi / 15, secs, h)
                s.addConn(if (i % 3 == 0) "4.5G" else "5G", mob, mob / 20, secs)
                s.addConn("Wi-Fi", wifi, wifi / 15, secs)
                if (h < 7) s.screenOff += (mob + wifi) / 3
            }
        }
        StatsStore.save(ctx, force = true)
    }

    /** Servisi durdurup canlı hızı sabit örnek değerlere ayarlar (ana pencere servisi yeniden başlatır). */
    private fun fakeLive() {
        ctx.stopService(Intent(ctx, SpeedService::class.java))
        pause(600)
        val r = Random(7)
        repeat(60) { SpeedService.pushSample((2_000_000 + r.nextInt(0, 6_000_000)).toLong()) }
        SpeedService.lastDown = 4_404_019
        SpeedService.lastUp = 122_880
        SpeedService.rollValue = 342_000_000
        SpeedService.rollLabel = "Son 2 saat"
    }

    @Test
    fun screens() {
        shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        shell("appops set $pkg GET_USAGE_STATS allow")
        shell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        shell("cmd uimode night no")
        Prefs.get(ctx).edit().clear().putBoolean(Prefs.ONBOARDED, true).putInt(Prefs.QUOTA_MB, 1024).commit()
        seed()


        for (night in listOf(false, true)) {
            shell("cmd uimode night ${if (night) "yes" else "no"}")
            pause(1000)
            Ui.init(ctx)
            val suffix = if (night) "_koyu" else ""
            ActivityScenario.launch(MainActivity::class.java).use { sc ->
                fakeLive()
                listOf("1_ana_sayfa", "2_istatistik", "3_uygulamalar", "4_analiz", "5_ayarlar").forEachIndexed { tab, name ->
                    sc.onActivity { it.select(tab) }
                    pause(if (tab == 2 || tab == 3) 3500 else 1500)
                    shot(name + suffix)
                    if (!night && (tab == 0 || tab == 3 || tab == 1)) {
                        // aşağı kaydırılmış hâli de
                        shell("input swipe 540 1800 540 500 400")
                        shot(name + "_asagi")
                        shell("input swipe 540 1800 540 500 400")
                        shot(name + "_asagi2")
                    }
                }
            }
            if (night) continue
            SettingsActivity.TITLES.keys.forEachIndexed { i, cat ->
                val intent = Intent(ctx, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, cat)
                ActivityScenario.launch<SettingsActivity>(intent).use { shot("6_ayar_${i}_$cat") }
            }
            ActivityScenario.launch<AppDetailActivity>(
                Intent(ctx, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_UID, Process.myUid())
            ).use { pause(2500); shot("7_uygulama_detay") }
            ActivityScenario.launch(OnboardingActivity::class.java).use { shot("8_kurulum") }
        }
        shell("cmd uimode night no")

        // Bildirim paneli ve durum çubuğu
        ctx.startForegroundService(Intent(ctx, SpeedService::class.java))
        pause(3000)
        inst.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        pause(1500)
        shot("9_bildirim_paneli")
        inst.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        pause(500)
    }
}
