package com.emir.hizgostergesi

import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Uçtan uca duman testleri: her ekran, her ayar kombinasyonu ve arka plan servisi gerçek bir
 * Android'de açılıp çalıştırılır. Herhangi bir çökme uygulama sürecini öldürür ve testi düşürür.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SmokeTest {

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val pkg = ctx.packageName

    private fun shell(cmd: String) {
        inst.uiAutomation.executeShellCommand(cmd).close()
    }

    private fun pause(ms: Long) = SystemClock.sleep(ms)

    @Before
    fun setUp() {
        shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        shell("pm grant $pkg android.permission.READ_PHONE_STATE")
        shell("appops set $pkg GET_USAGE_STATS allow")
        shell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        pause(300)
        Prefs.get(ctx).edit().putBoolean(Prefs.ONBOARDED, true).commit()
    }

    private fun startService() {
        ctx.startForegroundService(Intent(ctx, SpeedService::class.java))
        pause(2500)
    }

    @Test
    fun a01_serviceStartsAndPostsNotification() {
        startService()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        assertTrue("Hız bildirimi görünmeli", nm.activeNotifications.any { it.id == SpeedService.NOTIF_ID })
    }

    @Test
    fun a02_allTabsOpenAndTick() {
        startService()
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            repeat(2) {
                for (tab in 0..4) {
                    sc.onActivity { it.select(tab) }
                    pause(1600)
                }
            }
            // Ana sayfaya geri tuşu ile dönüş
            sc.onActivity { it.select(MainActivity.TAB_SETTINGS) }
            pause(500)
            sc.onActivity {
                @Suppress("DEPRECATION")
                it.onBackPressed()
            }
            pause(800)
        }
    }

    @Test
    fun a03_openTabFromIntent() {
        val i = Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ANALYSIS)
        ActivityScenario.launch<MainActivity>(i).use { pause(2500) }
    }

    @Test
    fun a04_everySettingsCategoryOpens() {
        SettingsActivity.TITLES.keys.forEach { cat ->
            val i = Intent(ctx, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, cat)
            ActivityScenario.launch<SettingsActivity>(i).use { pause(700) }
        }
        // Bilinmeyen kategori de çökmemeli
        val bad = Intent(ctx, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, "yok-boyle-bir-sey")
        ActivityScenario.launch<SettingsActivity>(bad).use { pause(500) }
    }

    @Test
    fun a05_onboardingAndRedirect() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { pause(1200) }
        Prefs.get(ctx).edit().putBoolean(Prefs.ONBOARDED, false).commit()
        // Ana pencere kurulum yapılmamışsa kendini kapatıp sihirbazı açmalı (kapanması hata değil)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { pause(1500) }
        } catch (e: AssertionError) {
            pause(1500)
        }
        Prefs.get(ctx).edit().putBoolean(Prefs.ONBOARDED, true).commit()
    }

    @Test
    fun a06_appDetailForVariousUids() {
        listOf(Process.myUid(), 1000, 0, -4, -5, -1, 99999).forEach { uid ->
            val i = Intent(ctx, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_UID, uid)
            ActivityScenario.launch<AppDetailActivity>(i).use { pause(1500) }
        }
    }

    @Test
    fun a07_statusIconAllCombinations() {
        val p = Prefs.get(ctx)
        for (mode in 0..3) for (unit in 0..2) for (size in 0..2) for (bold in listOf(true, false)) for (bits in listOf(true, false)) {
            p.edit().putInt(Prefs.SB_MODE, mode).putInt(Prefs.SB_UNIT, unit).putInt(Prefs.SB_SIZE, size)
                .putBoolean(Prefs.SB_BOLD, bold).commit()
            for (speed in listOf(0L, 999L, 1_000_000L, 999_999_999L, Long.MAX_VALUE / 16)) {
                StatusIcon.draw(p, speed, speed / 3, bits)
            }
        }
        p.edit().putBoolean(Prefs.SB_HIDE_IDLE, true).commit()
        StatusIcon.draw(p, 0, 0, false)
        p.edit().putBoolean(Prefs.SB_HIDE_IDLE, false).commit()
    }

    @Test
    fun a08_notificationSettingCombinations() {
        startService()
        val p = Prefs.get(ctx)
        val combos = listOf(
            mapOf(Prefs.ROLL_POS to 0, Prefs.N_SIZE to 0, Prefs.N_ARROWS to 0, Prefs.N_BG to 0),
            mapOf(Prefs.ROLL_POS to 1, Prefs.N_SIZE to 2, Prefs.N_ARROWS to 1, Prefs.N_BG to SettingsActivity.BG_ACCENT),
            mapOf(Prefs.ROLL_POS to 2, Prefs.N_SIZE to 1, Prefs.N_ARROWS to 2, Prefs.N_BG to 0xFF000000.toInt()),
            mapOf(Prefs.ROLL_SCOPE to 1, Prefs.ROLL_HOURS to 24, Prefs.HOURS to 24, Prefs.QUOTA_MB to 250),
            mapOf(Prefs.ROLL_SCOPE to 2, Prefs.ROLL_HOURS to 2, Prefs.INTERVAL_SEC to 2, Prefs.UNIT_BASE to 1024)
        )
        val flags = listOf(Prefs.N_MONO, Prefs.N_LINE_PEAK, Prefs.N_LINE_MONTH, Prefs.N_BTN_RESET, Prefs.N_BTN_ROLL_RESET, Prefs.UNIT_BITS, Prefs.MOBILE_ONLY)
        combos.forEachIndexed { i, c ->
            val e = p.edit()
            c.forEach { (k, v) -> e.putInt(k, v) }
            flags.forEachIndexed { j, f -> e.putBoolean(f, (i + j) % 2 == 0) }
            // Tüm satırlar kapalı durumu da denensin
            val allOff = i == 2
            listOf(Prefs.N_LINE_SPEED, Prefs.N_LINE_PERIOD, Prefs.N_LINE_TODAY).forEach { e.putBoolean(it, !allOff) }
            e.commit()
            pause(2300)
        }
        // Servis eylemleri
        listOf(SpeedService.ACTION_RESET, SpeedService.ACTION_RESET_ROLL, SpeedService.ACTION_TOGGLE_OVERLAY, SpeedService.ACTION_TOGGLE_OVERLAY)
            .forEach {
                ctx.startForegroundService(Intent(ctx, SpeedService::class.java).setAction(it))
                pause(1200)
            }
        p.edit().clear().putBoolean(Prefs.ONBOARDED, true).commit()
        pause(1500)
    }

    @Test
    fun a09_overlayShowsAndHides() {
        startService()
        Prefs.get(ctx).edit().putBoolean(Prefs.OVERLAY, true).putInt(Prefs.OVERLAY_X, 99999).putInt(Prefs.OVERLAY_Y, -500).commit()
        pause(3000)
        Prefs.get(ctx).edit().putInt(Prefs.OVERLAY_SIZE, 24).putInt(Prefs.OVERLAY_ALPHA, 0).commit()
        pause(2000)
        Prefs.get(ctx).edit().putBoolean(Prefs.OVERLAY, false).commit()
        pause(2000)
    }

    @Test
    fun a10_backgroundChecksWidgetAndStorage() {
        Insights.runChecks(ctx)
        SpeedWidget().onUpdate(ctx, AppWidgetManager.getInstance(ctx), intArrayOf())
        val today = StatsStore.today(ctx)
        today.add(true, 1_000_000, 10_000, 1.0, 12)
        StatsStore.save(ctx, force = true)
        StatsStore.sumRange(ctx, 0, System.currentTimeMillis() + 86_400_000L)
        StatsStore.aggregate(ctx, StatsStore.key().take(4))
        NetStats.queryApps(ctx, System.currentTimeMillis() - 7 * 86_400_000L, System.currentTimeMillis())
        NetStats.deviceTotal(ctx, System.currentTimeMillis() - 86_400_000L, System.currentTimeMillis())
        ConnInfo.refresh(ctx)
    }

    @Test
    fun a11_darkModeAndMaterialYouOff() {
        shell("cmd uimode night yes")
        pause(800)
        Prefs.get(ctx).edit().putBoolean(Prefs.DYNAMIC_COLOR, false).commit()
        Ui.init(ctx)
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            for (tab in 0..4) {
                sc.onActivity { it.select(tab) }
                pause(900)
            }
        }
        shell("cmd uimode night no")
        Prefs.get(ctx).edit().putBoolean(Prefs.DYNAMIC_COLOR, true).commit()
        Ui.init(ctx)
        pause(500)
    }

    @Test
    fun a12_withoutPermissions() {
        shell("appops set $pkg GET_USAGE_STATS deny")
        shell("appops set $pkg SYSTEM_ALERT_WINDOW deny")
        Prefs.get(ctx).edit().putBoolean(Prefs.OVERLAY, true).commit()
        startService()
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            for (tab in 0..4) {
                sc.onActivity { it.select(tab) }
                pause(1000)
            }
        }
        ActivityScenario.launch<AppDetailActivity>(
            Intent(ctx, AppDetailActivity::class.java).putExtra(AppDetailActivity.EXTRA_UID, Process.myUid())
        ).use { pause(800) }
        Prefs.get(ctx).edit().putBoolean(Prefs.OVERLAY, false).commit()
        shell("appops set $pkg GET_USAGE_STATS allow")
        shell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
    }

    @Test
    fun a13_rotationKeepsWorking() {
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { it.select(MainActivity.TAB_STATS) }
            pause(800)
            sc.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            pause(1500)
            sc.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            pause(1500)
        }
    }
}
