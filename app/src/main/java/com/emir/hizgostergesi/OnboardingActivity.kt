package com.emir.hizgostergesi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** İlk açılışta izinleri adım adım isteyen kısa kurulum. */
class OnboardingActivity : Activity() {

    private class Step(
        val icon: String,
        val title: String,
        val text: String,
        val optional: Boolean,
        val granted: () -> Boolean,
        val request: (() -> Unit)?
    )

    private lateinit var steps: List<Step>
    private var index = 0
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        steps = buildSteps()
        val (scroll, r) = Ui.page(this)
        root = r
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    @SuppressLint("BatteryLife")
    private fun buildSteps(): List<Step> {
        val list = ArrayList<Step>()
        list += Step(
            "👋", "Hız Göstergesi'ne hoş geldin",
            "Anlık internet hızını durum çubuğunda gösterir, ne kadar veri harcadığını uygulama uygulama takip eder.\n\n" +
                "İnternet izni yoktur: hiçbir veri telefondan çıkmaz. Birkaç izinle her şeyi hazırlayalım.",
            optional = false, granted = { true }, request = null
        )
        if (Build.VERSION.SDK_INT >= 33) {
            list += Step(
                "🔔", "Bildirim izni",
                "Hızın durum çubuğunda görünmesi için gerekli. Hız, kalıcı bir bildirimin simgesine yazılır.",
                optional = false,
                granted = { checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED },
                request = { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
            )
        }
        list += Step(
            "🔋", "Arka planda kapanmasın",
            "Samsung, pil tasarrufu için arka plandaki uygulamaları kapatabilir. Muaf tutarsan gösterge hiç kaybolmaz. " +
                "Uygulama zaten çok az pil kullanır.",
            optional = true,
            granted = { getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName) },
            request = {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        )
        list += Step(
            "📱", "Kullanım erişimi",
            "Hangi uygulamanın ne kadar veri harcadığını, kategorileri ve geçmiş haftaları görmek için. " +
                "Açılan listede Hız Göstergesi'ni bulup izni aç.",
            optional = true,
            granted = { NetStats.hasAccess(this) },
            request = { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        )
        list += Step(
            "📡", "Bağlantı türü",
            "Hızın yanında 5G / 4.5G / 4G / 3G yazabilmek için telefon durumunu okuma izni. " +
                "Arama ya da numara bilgisine erişilmez, sadece şebeke türü okunur.",
            optional = true,
            granted = { checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED },
            request = { requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 2) }
        )
        list += Step(
            "🪟", "Yüzen pencere",
            "Hızı her uygulamanın üstünde küçük, sürüklenebilir bir pencerede görmek istersen. Sonradan ayarlardan da açabilirsin.",
            optional = true,
            granted = { Settings.canDrawOverlays(this) },
            request = {
                Prefs.get(this).edit().putBoolean(Prefs.OVERLAY, true).apply()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        )
        list += Step(
            "✅", "Her şey hazır",
            "Hız artık durum çubuğunda. İstatistik, uygulamalar ve analiz ekranlarını ana sayfadan açabilirsin. " +
                "Görünümü ayarlardan dilediğin gibi değiştirebilirsin.",
            optional = false, granted = { true }, request = null
        )
        return list
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2) ConnInfo.refresh(this)
        render()
    }

    private fun render() {
        root.removeAllViews()
        val step = steps[index]
        val granted = step.granted()

        // İlerleme noktaları
        val dots = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@OnboardingActivity, 24), 0, Ui.dp(this@OnboardingActivity, 36))
        }
        steps.indices.forEach { i ->
            dots.addView(View(this).apply {
                background = Ui.rounded(
                    if (i == index) Ui.ACCENT else Ui.outline(this@OnboardingActivity),
                    Ui.dp(this@OnboardingActivity, 4).toFloat()
                )
            }, LinearLayout.LayoutParams(Ui.dp(this, if (i == index) 24 else 8), Ui.dp(this, 8)).apply {
                leftMargin = Ui.dp(this@OnboardingActivity, 3)
                rightMargin = Ui.dp(this@OnboardingActivity, 3)
            })
        }
        root.addView(dots)

        // Büyük simge
        root.addView(TextView(this).apply {
            text = step.icon
            textSize = 44f
            gravity = Gravity.CENTER
            background = Ui.rounded(Ui.accentSoft(), Ui.dp(this@OnboardingActivity, 56).toFloat())
        }, LinearLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = Ui.dp(this@OnboardingActivity, 28)
        })

        root.addView(Ui.text(this, step.title, 26f, bold = true).apply { gravity = Gravity.CENTER })
        root.addView(Ui.text(this, step.text, 16f, topDp = 10, color = Ui.secondary(this)).apply { gravity = Gravity.CENTER })

        if (step.request != null) {
            root.addView(Ui.text(
                this,
                if (granted) "✓ İzin verildi" else if (step.optional) "İsteğe bağlı" else "Gerekli",
                14f, bold = true, topDp = 18,
                color = if (granted) 0xFF2E7D32.toInt() else Ui.secondary(this)
            ).apply { gravity = Gravity.CENTER })
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(this@OnboardingActivity, 28), 0, 0)
        }
        val last = index == steps.lastIndex
        when {
            step.request != null && !granted -> {
                buttons.addView(Ui.button(this, "İzin ver", filled = true) { step.request?.invoke() })
                buttons.addView(Ui.button(this, if (step.optional) "Şimdilik atla" else "Sonra") { next() })
            }
            last -> buttons.addView(Ui.button(this, "Başla", filled = true) { finishSetup() })
            else -> buttons.addView(Ui.button(this, if (index == 0) "Kuruluma başla" else "Devam", filled = true) { next() })
        }
        if (index > 0) {
            buttons.addView(android.widget.TextView(this).apply {
                text = "← Geri"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Ui.secondary(this@OnboardingActivity))
                setPadding(0, Ui.dp(this@OnboardingActivity, 14), 0, Ui.dp(this@OnboardingActivity, 6))
                isClickable = true
                setOnClickListener { back() }
            })
        }
        root.addView(buttons)
        Ui.fadeIn(root)
    }

    private fun back() {
        if (index > 0) {
            index--
            render()
        }
    }

    @Deprecated("Geri tuşu: bir önceki adıma dön")
    override fun onBackPressed() {
        if (index > 0) back() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun next() {
        if (index < steps.lastIndex) index++
        render()
    }

    private fun finishSetup() {
        Prefs.get(this).edit().putBoolean(Prefs.ONBOARDED, true).apply()
        startForegroundService(Intent(this, SpeedService::class.java))
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
