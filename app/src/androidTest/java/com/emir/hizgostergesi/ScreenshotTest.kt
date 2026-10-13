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
        if (name.startsWith("9_")) {
            val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
            val f = File(dir, "$name.png")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            shell("mkdir -p /data/local/tmp/shots")
            shell("cp ${f.absolutePath} /data/local/tmp/shots/")
            return
        }
        // Yavaş emülatörde çıkan "uygulama yanıt vermiyor" gibi sistem pencerelerini kapat
        shell("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        pause(300)
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

    /** Widget'ları ana ekrana eklemeden, görünümlerini çizip kaydeder. */
    private fun widgetShots() {
        val d = ctx.resources.displayMetrics.density
        listOf(
            Triple("10_widget_kucuk", Widgets.smallViews(ctx), 90 to 90),
            Triple("10_widget_buyuk", Widgets.largeViews(ctx), 330 to 160),
            Triple("10_widget_klasik", SpeedWidget.views(ctx, "↓ 4,4 MB/s    ↑ 122 KB/s", "Bugün  Mobil 412 MB • Wi-Fi 1,2 GB", "Son 2 saat: 342 MB", 0xFFFFFFFF.toInt()), 250 to 80)
        ).forEach { (name, rv, size) ->
            inst.runOnMainSync {
                val parent = android.widget.FrameLayout(ctx)
                val v = rv.apply(ctx, parent)
                val w = (size.first * d).toInt()
                val h = (size.second * d).toInt()
                v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
                v.layout(0, 0, w, h)
                val bmp = Bitmap.createBitmap(w + 40, h + 40, Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(bmp)
                cv.drawColor(0xFF5B7A99.toInt())   // duvar kâğıdı yerine düz renk
                cv.translate(20f, 20f)
                v.draw(cv)
                val f = File(dir, "$name.png")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            shell("mkdir -p /data/local/tmp/shots")
            shell("cp ${File(dir, "$name.png").absolutePath} /data/local/tmp/shots/")
        }
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
        shell("settings put global hide_error_dialogs 1")
        shell("am force-stop com.google.android.apps.nexuslauncher")
        val resetDay = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_MONTH, -12) }.get(java.util.Calendar.DAY_OF_MONTH)
        Prefs.get(ctx).edit().clear().putBoolean(Prefs.ONBOARDED, true).putInt(Prefs.QUOTA_MB, 1024)
            .putBoolean(Prefs.PLAN_ON, true).putInt(Prefs.PLAN_MB, 8 * 1024).putInt(Prefs.PLAN_DAY, resetDay)
            .putBoolean(Prefs.PLAN_NIGHT, true).commit()
        seed()
        // Emülatörün kendi kayıtları neredeyse boş; örnek veriden hesaplansın
        shell("appops set $pkg GET_USAGE_STATS deny")
        PlanState.compute(ctx)
        shell("appops set $pkg GET_USAGE_STATS allow")
        fakeLive()
        widgetShots()


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

        // Hazır temalar (telefon açık temadayken "hep karanlık" ile)
        for (t in 1..3) {
            Prefs.get(ctx).edit().putInt(Prefs.THEME, t).putBoolean(Prefs.FORCE_DARK, true).commit()
            Ui.init(ctx)
            ActivityScenario.launch(MainActivity::class.java).use { sc ->
                fakeLive()
                pause(1800)
                shot("11_tema${t}_ana")
                shell("input swipe 540 1800 540 700 400")
                shot("11_tema${t}_ana_asagi")
                if (t == 1) {
                    sc.onActivity { it.select(3) }
                    pause(2500)
                    shot("11_tema1_analiz")
                }
            }
            if (t == 1) {
                val look = Intent(ctx, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, SettingsActivity.CAT_LOOK)
                ActivityScenario.launch<SettingsActivity>(look).use { shot("11_tema1_gorunum") }
                val plan = Intent(ctx, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, SettingsActivity.CAT_PLAN)
                ActivityScenario.launch<SettingsActivity>(plan).use { shot("11_tema1_paket") }
            }
        }
        // Açık hâl: tema seçili, hep karanlık kapalı
        Prefs.get(ctx).edit().putInt(Prefs.THEME, 2).putBoolean(Prefs.FORCE_DARK, false).commit()
        Ui.init(ctx)
        ActivityScenario.launch(MainActivity::class.java).use { fakeLive(); pause(1800); shot("11_tema2_acik") }
        Prefs.get(ctx).edit().putInt(Prefs.THEME, 0).commit()
        Ui.init(ctx)

        // Bildirim paneli ve durum çubuğu
        ctx.startForegroundService(Intent(ctx, SpeedService::class.java))
        pause(3000)
        shell("cmd statusbar expand-notifications")
        pause(2000)
        shot("9_bildirim_paneli")
        // Bildirimi genişletip tüm satırları da göster
        shell("input swipe 540 700 540 1300 300")
        pause(1200)
        shot("9_bildirim_paneli_genis")
        shell("cmd statusbar collapse")
        inst.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        pause(500)
    }
}
