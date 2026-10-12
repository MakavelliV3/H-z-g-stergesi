package com.emir.hizgostergesi

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Uygulamanın ana penceresi: altta 5 sekmeli menü, üstte seçili sekmenin içeriği.
 * Sekmeler: Ana sayfa, İstatistik, Uygulamalar, Analiz, Ayarlar.
 */
class MainActivity : Activity() {

    companion object {
        const val EXTRA_TAB = "sekme"
        const val TAB_HOME = 0
        const val TAB_STATS = 1
        const val TAB_APPS = 2
        const val TAB_ANALYSIS = 3
        const val TAB_SETTINGS = 4
    }

    private class NavItem(val title: String, val icon: Int)

    private val items = listOf(
        NavItem("Ana sayfa", R.drawable.ic_nav_home),
        NavItem("İstatistik", R.drawable.ic_nav_stats),
        NavItem("Uygulamalar", R.drawable.ic_nav_apps),
        NavItem("Analiz", R.drawable.ic_nav_insights),
        NavItem("Ayarlar", R.drawable.ic_nav_settings)
    )

    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private val pills = ArrayList<FrameLayout>()
    private val icons = ArrayList<ImageView>()
    private val labels = ArrayList<TextView>()
    private val screens = arrayOfNulls<Screen>(5)
    private var current = -1
    private var topInset = 0
    private var bottomInset = 0
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false

    private val ticker = object : Runnable {
        override fun run() {
            screens.getOrNull(current)?.tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.get(this).getBoolean(Prefs.ONBOARDED, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        Ui.setupWindow(this)

        val root = FrameLayout(this).apply { setBackgroundColor(Ui.bg(this@MainActivity)) }
        content = FrameLayout(this)
        nav = buildNav()
        root.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(nav, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        // Kaydırınca içerik saat/pil simgelerinin altına girmesin diye yarı saydam şerit
        val scrim = View(this).apply { setBackgroundColor((Ui.bg(this@MainActivity) and 0x00FFFFFF) or 0xEB000000.toInt()) }
        root.addView(scrim, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val (t, b) = Ui.barInsets(insets)
            topInset = t
            bottomInset = b
            nav.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10) + b)
            scrim.layoutParams = (scrim.layoutParams as FrameLayout.LayoutParams).apply { height = t }
            screens.forEach { it?.applyInsets(topInset, navSpace()) }
            insets
        }
        setContentView(root)

        select(savedInstanceState?.getInt(EXTRA_TAB) ?: intent.getIntExtra(EXTRA_TAB, TAB_HOME), animate = false)
        startForegroundService(Intent(this, SpeedService::class.java))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_TAB, current)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_TAB)) select(intent.getIntExtra(EXTRA_TAB, TAB_HOME))
    }

    override fun onResume() {
        super.onResume()
        if (current < 0) return
        resumed = true
        screens[current]?.onShow()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(ticker)
        if (current >= 0) screens[current]?.onHide()
        super.onPause()
    }

    @Deprecated("Geri tuşu: ana sayfaya dön, oradaysa çık")
    override fun onBackPressed() {
        if (current > TAB_HOME) select(TAB_HOME) else @Suppress("DEPRECATION") super.onBackPressed()
    }

    /** Alt menünün içerikle çakışmaması için içerikte bırakılan boşluk. */
    private fun navSpace() = Ui.dp(this, 88) + bottomInset

    private fun buildNav(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Ui.surface(this@MainActivity))
            elevation = Ui.dp(this@MainActivity, 6).toFloat()
        }
        items.forEachIndexed { i, item ->
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isClickable = true
                contentDescription = item.title
                background = Ui.ripple(Ui.rounded(0, Ui.dp(this@MainActivity, 16).toFloat()))
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (i == current) screens[i]?.scrollToTop() else select(i)
                }
            }
            val pill = FrameLayout(this)
            val icon = ImageView(this).apply { setImageResource(item.icon) }
            pill.addView(icon, FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22), Gravity.CENTER))
            col.addView(pill, LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 32)))
            val label = TextView(this).apply {
                text = item.title
                textSize = 11.5f
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(this@MainActivity, 4), 0, 0)
            }
            col.addView(label)
            pills += pill
            icons += icon
            labels += label
            bar.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        return bar
    }

    private fun screen(i: Int): Screen = screens[i] ?: when (i) {
        TAB_STATS -> StatsScreen(this)
        TAB_APPS -> AppsScreen(this)
        TAB_ANALYSIS -> AnalysisScreen(this)
        TAB_SETTINGS -> SettingsScreen(this)
        else -> HomeScreen(this)
    }.also {
        screens[i] = it
        it.applyInsets(topInset, navSpace())
    }

    fun select(i: Int, animate: Boolean = true) {
        val index = i.coerceIn(0, items.lastIndex)
        if (index == current) return
        val old = screens.getOrNull(current)
        val next = screen(index)
        val forward = index > current

        old?.onHide()
        old?.view?.let { v ->
            if (animate) {
                v.animate().alpha(0f).translationX(Ui.dp(this, if (forward) -24 else 24).toFloat())
                    .setDuration(160).withEndAction { content.removeView(v) }.start()
            } else {
                content.removeView(v)
            }
        }
        val nv = next.view
        nv.animate().cancel()
        if (nv.parent != null) (nv.parent as FrameLayout).removeView(nv)
        content.addView(nv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (animate) {
            nv.alpha = 0f
            nv.translationX = Ui.dp(this, if (forward) 24 else -24).toFloat()
            nv.animate().alpha(1f).translationX(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        } else {
            nv.alpha = 1f
            nv.translationX = 0f
        }
        current = index
        updateNav()
        // İlk açılışta onResume zaten onShow çağırır; iki kez yüklememek için sadece görünürken çağır
        if (resumed) next.onShow()
    }

    private fun updateNav() {
        items.indices.forEach { i ->
            val on = i == current
            pills[i].background = if (on) Ui.rounded(Ui.accentSoft(0x33), Ui.dp(this, 16).toFloat()) else null
            icons[i].imageTintList = ColorStateList.valueOf(if (on) Ui.accentText(this) else Ui.secondary(this))
            labels[i].setTextColor(if (on) Ui.primary(this) else Ui.secondary(this))
            labels[i].typeface = if (on) Ui.MEDIUM else android.graphics.Typeface.DEFAULT
        }
    }
}

/**
 * Bir sekmenin içeriği. Görünüm ilk açılışta bir kez kurulur; sekme görünürken her saniye [tick] çağrılır.
 */
abstract class Screen(val a: MainActivity) {
    protected val prefs = Prefs.get(a)
    protected lateinit var scroll: ScrollView
    protected lateinit var root: LinearLayout

    val view: View by lazy {
        val (s, r) = Ui.scrollPage(a)
        scroll = s
        root = r
        build()
        s
    }

    /** İçeriği [root]'a ekler. */
    protected abstract fun build()

    open fun onShow() {}
    open fun onHide() {}
    open fun tick() {}

    fun applyInsets(top: Int, bottom: Int) {
        view
        val p = Ui.dp(a, 16)
        root.setPadding(p, Ui.dp(a, 8) + top, p, bottom)
    }

    fun scrollToTop() {
        if (::scroll.isInitialized) scroll.smoothScrollTo(0, 0)
    }

    protected fun dp(v: Int) = Ui.dp(a, v)
}
