package com.emir.hizgostergesi

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.EditText
import android.widget.ImageView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Ekranları XML yazmadan, tutarlı bir görünümle kurmak için yardımcılar.
 * Material You açıksa renkler telefonun duvar kâğıdından gelir (Android 12+).
 */
object Ui {
    var ACCENT = 0xFFC6FF3D.toInt()
    var ACCENT_2 = 0xFF6D8C22.toInt()
    /** Vurgu renginin üstündeki yazı rengi (açık vurgu renklerinde koyu). */
    var ON_ACCENT = 0xFF121A00.toInt()
    private var pal: Palette = Themes.palette(Themes.LIME)

    /** Kokpit tasarımında her zaman true (eski kodla uyum için). */
    fun themed() = true

    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** Uygulama açılışında ve vurgu rengi değişince çağrılır. */
    fun init(c: Context) {
        Fmt.base = if (Prefs.get(c).getInt(Prefs.UNIT_BASE, 1000) == 1024) 1024L else 1000L
        pal = Themes.palette(Themes.current(c))
        ACCENT = pal.accent
        ACCENT_2 = pal.accent2
        ON_ACCENT = pal.onAccent
    }

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    /** Kokpit tasarımı hep karanlık. */
    @Suppress("UNUSED_PARAMETER")
    fun night(c: Context) = true

    @Suppress("UNUSED_PARAMETER") fun bg(c: Context) = pal.bg
    @Suppress("UNUSED_PARAMETER") fun surface(c: Context) = pal.surface
    @Suppress("UNUSED_PARAMETER") fun primary(c: Context) = pal.primary
    @Suppress("UNUSED_PARAMETER") fun secondary(c: Context) = pal.secondary
    @Suppress("UNUSED_PARAMETER") fun outline(c: Context) = pal.outline
    /** Küçük büyük harfli etiketler için soluk gri. */
    @Suppress("UNUSED_PARAMETER") fun labelColor(c: Context) = Themes.LABEL
    @Suppress("UNUSED_PARAMETER") fun accentText(c: Context): Int = pal.accentText

    /** Mobil veri ve Wi-Fi için grafik renkleri: mobil vurgu rengi, Wi-Fi ona uyan ikinci renk. */
    val MOBILE get() = ACCENT
    val WIFI get() = if (ACCENT == Themes.palette(Themes.BLUE).accent) 0xFF2EE6C5.toInt() else 0xFF7AA7FF.toInt()

    /** Sabit genişlikli rakam yazısı uygular. */
    fun mono(tv: TextView, weight: Int = 1): TextView {
        tv.typeface = when (weight) {
            0 -> Mono.regular(tv.context)
            2 -> Mono.bold(tv.context)
            else -> Mono.medium(tv.context)
        }
        tv.fontFeatureSettings = "tnum"
        return tv
    }

    /** Vurgu renginin hafif (saydam) hâli. */
    fun accentSoft(alpha: Int = 0x26) = (ACCENT and 0x00FFFFFF) or (alpha shl 24)

    private val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())

    fun rounded(color: Int, radiusPx: Float, strokePx: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

    /** Basılınca hafifçe içe gömülen (küçülen) görünüm; tıklama olaylarına dokunmaz. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View, scale: Float = 0.97f): View {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(scale).scaleY(scale).setDuration(90).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }
        return v
    }

    /** Ekranın altında birkaç saniye duran "… · Geri al" çubuğu. */
    fun undoBar(a: Activity, message: String, onUndo: () -> Unit) {
        val content = a.findViewById<android.widget.FrameLayout>(android.R.id.content) ?: return
        content.findViewWithTag<View>("undo")?.let { content.removeView(it) }
        val bottomInset = a.window.decorView.rootWindowInsets?.let { barInsets(it).second } ?: 0
        val bar = LinearLayout(a).apply {
            tag = "undo"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(a, 18), dp(a, 6), dp(a, 6), dp(a, 6))
            background = rounded(Themes.SURFACE_2, dp(a, 14).toFloat(), dp(a, 1), outline(a))
            elevation = dp(a, 6).toFloat()
            addView(TextView(a).apply {
                text = message
                textSize = 14f
                setTextColor(primary(a))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val dismiss = Runnable {
            bar.animate().alpha(0f).translationY(dp(a, 24).toFloat()).setDuration(200).withEndAction { content.removeView(bar) }.start()
        }
        bar.addView(TextView(a).apply {
            text = "Geri al"
            textSize = 14f
            typeface = MEDIUM
            setTextColor(ACCENT)
            setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 10))
            isClickable = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onUndo()
                bar.removeCallbacks(dismiss)
                dismiss.run()
            }
        })
        content.addView(bar, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ).apply { setMargins(dp(a, 16), 0, dp(a, 16), dp(a, 20) + bottomInset) })
        bar.alpha = 0f
        bar.translationY = dp(a, 24).toFloat()
        bar.animate().alpha(1f).translationY(0f).setDuration(220).start()
        bar.postDelayed(dismiss, 6000)
    }

    /** Dokununca dalga efekti veren arka plan. */
    fun ripple(content: GradientDrawable, rippleColor: Int = 0x33808080) =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, null)

    /**
     * Kenardan kenara, kaydırılabilir sayfa. İçerik sistem çubuklarının altına taşmasın diye
     * onların boyu kadar boşluk bırakılır.
     */
    /** Pencereyi kenardan kenara, doğru durum çubuğu renkleriyle hazırlar. */
    fun setupWindow(a: Activity) {
        init(a)
        a.actionBar?.hide()
        a.window.decorView.setBackgroundColor(bg(a))
        if (Build.VERSION.SDK_INT >= 30) {
            a.window.setDecorFitsSystemWindows(false)
            val light = !night(a)
            a.window.insetsController?.setSystemBarsAppearance(
                if (light) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }
    }

    /** Sistem çubuklarının (üst, alt) yüksekliği. */
    fun barInsets(insets: WindowInsets): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            val b = insets.getInsets(WindowInsets.Type.systemBars())
            b.top to b.bottom
        } else {
            insets.systemWindowInsetTop to insets.systemWindowInsetBottom
        }

    /** Sekme içinde kullanılan kaydırılabilir sayfa (pencere ayarına dokunmaz). */
    fun scrollPage(c: Context): Pair<ScrollView, LinearLayout> {
        val p = dp(c, 16)
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p / 2, p, p)
        }
        val scroll = ScrollView(c).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(root)
        }
        return scroll to root
    }

    fun page(a: Activity): Pair<ScrollView, LinearLayout> {
        init(a)
        a.actionBar?.hide()
        val p = dp(a, 16)
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(p, p, p, p * 2)
        }
        val scroll = ScrollView(a).apply {
            setBackgroundColor(bg(a))
            isVerticalScrollBarEnabled = false
            addView(root)
        }
        a.window.decorView.setBackgroundColor(bg(a))
        if (Build.VERSION.SDK_INT >= 30) {
            a.window.setDecorFitsSystemWindows(false)
            val light = !night(a)
            a.window.insetsController?.setSystemBarsAppearance(
                if (light) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        }
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                top = bars.top
                bottom = bars.bottom
            } else {
                top = insets.systemWindowInsetTop
                bottom = insets.systemWindowInsetBottom
            }
            root.setPadding(p, p / 2 + top, p, p * 2 + bottom)
            insets
        }
        return scroll to root
    }

    /** Ekranın üst çubuğu: geri düğmesi, başlık ve isteğe bağlı sağ düğme. */
    fun topBar(a: Activity, title: String, back: Boolean = true, action: Pair<String, () -> Unit>? = null): View {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(a, 12))
        }
        if (back) row.addView(iconButton(a, R.drawable.ic_back, "Geri") { a.finish() })
        row.addView(TextView(a).apply {
            text = title
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primary(a))
            setPadding(if (back) dp(a, 12) else dp(a, 4), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null) row.addView(circleButton(a, action.first, action.second))
        return row
    }

    fun iconButton(c: Context, icon: Int, description: String, onClick: () -> Unit) = ImageView(c).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(primary(c))
        contentDescription = description
        val p = dp(c, 10)
        setPadding(p, p, p, p)
        background = ripple(rounded(surface(c), dp(c, 24).toFloat()))
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
    }

    fun circleButton(c: Context, label: String, onClick: () -> Unit) = TextView(c).apply {
        text = label
        textSize = 20f
        gravity = Gravity.CENTER
        setTextColor(primary(c))
        background = ripple(rounded(surface(c), dp(c, 24).toFloat()))
        isClickable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(c, 44), dp(c, 44))
    }

    /** Yuvarlak köşeli bölüm kartı; başlığın solunda isteğe bağlı simge balonu. */
    fun card(c: Context, title: String? = null, icon: String? = null): LinearLayout {
        val pad = dp(c, 16)
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = rounded(surface(c), dp(c, 16).toFloat())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(c, 8) }
            if (title != null) {
                // Kokpit başlığı: küçük vurgu simgesi + aralıklı büyük harf etiket
                val head = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, dp(c, 10))
                }
                if (icon != null) {
                    head.addView(TextView(c).apply {
                        textSize = 16f
                        gravity = Gravity.CENTER
                        Icons.apply(this, icon, accentText(c))
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(c, 8) })
                }
                head.addView(TextView(c).apply {
                    text = title.uppercase(java.util.Locale("tr", "TR"))
                    textSize = 12f
                    typeface = MEDIUM
                    letterSpacing = 0.09f
                    setTextColor(labelColor(c))
                })
                addView(head)
            }
        }
    }

    fun text(
        c: Context, s: String, size: Float = 15f, bold: Boolean = false,
        topDp: Int = 0, color: Int? = null
    ) = TextView(c).apply {
        text = s
        textSize = size
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setTextColor(color ?: primary(c))
        setLineSpacing(0f, 1.08f)
        setPadding(0, dp(c, topDp), 0, dp(c, 4))
    }

    // Türkçe büyük harf: "i" → "İ" (Locale.ROOT "I" yapardı)
    fun label(c: Context, s: String) = text(c, s.uppercase(java.util.Locale("tr", "TR")), 11f, topDp = 12, color = labelColor(c)).apply {
        typeface = MEDIUM
        letterSpacing = 0.09f
    }

    fun header(c: Context, s: String) = text(c, s, 19f, bold = true, topDp = 18)

    fun hint(c: Context, s: String) = text(c, s, 13f, color = secondary(c))

    /** Hap şeklinde düğme. filled=true: dolu vurgu rengi, false: hafif vurgu. */
    fun button(c: Context, label: String, filled: Boolean = false, onClick: () -> Unit) = TextView(c).apply {
        text = label
        textSize = 15f
        typeface = MEDIUM
        gravity = Gravity.CENTER
        setTextColor(if (filled) ON_ACCENT else accentText(c))
        val v = dp(c, 13)
        setPadding(v, v, v, v)
        background = ripple(if (filled) rounded(ACCENT, dp(c, 14).toFloat())
            else rounded(0, dp(c, 14).toFloat(), dp(c, 1), outline(c)))
        isClickable = true
        pressable(this, 0.96f)
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(c, 10) }
    }

    /** Seçenekler hap düğmeler olarak; sığmazsa alt satıra geçer. */
    fun <T> choiceRow(c: Context, options: List<Pair<String, T>>, current: T, onPick: (T) -> Unit): View {
        val flow = FlowLayout(c, dp(c, 8), dp(c, 8)).apply { setPadding(0, dp(c, 4), 0, dp(c, 6)) }
        val radius = dp(c, 12).toFloat()
        val selected = arrayOf(android.R.attr.state_selected)
        val chips = ArrayList<TextView>()
        options.forEach { (label, value) ->
            val chip = TextView(c).apply {
                text = label
                textSize = 14f
                typeface = MEDIUM
                gravity = Gravity.CENTER
                setPadding(dp(c, 15), dp(c, 9), dp(c, 15), dp(c, 9))
                background = RippleDrawable(ColorStateList.valueOf(0x33808080), StateListDrawable().apply {
                    addState(selected.toIntArray(), rounded(ACCENT, radius))
                    addState(intArrayOf(), rounded(Color.TRANSPARENT, radius, dp(c, 1), outline(c)))
                }, rounded(Color.WHITE, radius))
                setTextColor(ColorStateList(arrayOf(selected.toIntArray(), intArrayOf()), intArrayOf(ON_ACCENT, primary(c))))
                isSelected = value == current
                isClickable = true
                setOnClickListener { v ->
                    if (v.isSelected) return@setOnClickListener
                    v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    chips.forEach { it.isSelected = it === v }
                    onPick(value)
                }
            }
            chips += chip
            flow.addView(chip)
        }
        return flow
    }

    fun switch(c: Context, label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(c).apply {
        text = label
        textSize = 15f
        setTextColor(primary(c))
        isChecked = checked
        thumbTintList = ColorStateList(checkedStates, intArrayOf(ACCENT, 0xFF5B616B.toInt()))
        trackTintList = ColorStateList(checkedStates, intArrayOf(accentSoft(0x66), 0xFF1C1F22.toInt()))
        setPadding(0, dp(c, 9), 0, dp(c, 9))
        setOnCheckedChangeListener { b, v ->
            b.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onChange(v)
        }
    }

    /** Başlığında değeri gösteren kaydırma çubuğu. */
    fun seekRow(c: Context, title: String, lo: Int, hi: Int, current: Int, suffix: String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(c, 8), 0, 0)
        }
        val label = text(c, "$title: $current$suffix", 14f)
        val bar = SeekBar(c).apply {
            max = hi
            min = lo
            progress = current
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(ACCENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, v: Int, fromUser: Boolean) {
                    label.text = "$title: $v$suffix"
                    if (fromUser) onChange(v)
                }

                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        box.addView(label)
        box.addView(bar)
        return box
    }

    /** Sekme başlığı: büyük başlık ve isteğe bağlı alt yazı. */
    fun screenTitle(c: Context, title: String, subtitle: String? = null): View = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 4), dp(c, 8), dp(c, 4), dp(c, 14))
        addView(TextView(c).apply {
            text = title
            textSize = 28f
            typeface = MEDIUM
            letterSpacing = -0.01f
            setTextColor(primary(c))
        })
        if (subtitle != null) addView(TextView(c).apply {
            text = subtitle.uppercase(java.util.Locale("tr", "TR"))
            textSize = 11f
            typeface = MEDIUM
            letterSpacing = 0.09f
            setTextColor(labelColor(c))
            setPadding(0, dp(c, 2), 0, 0)
        })
    }

    /** Boş durum: simge, başlık, açıklama ve isteğe bağlı düğme. */
    fun emptyState(c: Context, icon: String, title: String, text: String, action: Pair<String, () -> Unit>? = null): View =
        LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(c, 8), dp(c, 18), dp(c, 8), dp(c, 12))
            addView(TextView(c).apply {
                textSize = 32f
                gravity = Gravity.CENTER
                Icons.apply(this, icon, accentText(c))
                background = rounded(0, dp(c, 16).toFloat(), dp(c, 1), outline(c))
            }, LinearLayout.LayoutParams(dp(c, 64), dp(c, 64)).apply { bottomMargin = dp(c, 12) })
            addView(text(c, title, 16f).apply {
                typeface = MEDIUM
                gravity = Gravity.CENTER
            })
            addView(hint(c, text).apply { gravity = Gravity.CENTER })
            if (action != null) addView(button(c, action.first, filled = true, onClick = action.second))
        }

    /** Yüklenirken nabız gibi atan gri yer tutucular. */
    fun skeleton(c: Context, rows: Int = 3): View = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        repeat(rows) { i ->
            addView(View(c).apply {
                background = rounded(outline(c), dp(c, 8).toFloat())
            }, LinearLayout.LayoutParams(
                if (i % 2 == 0) LinearLayout.LayoutParams.MATCH_PARENT else dp(c, 180), dp(c, 14)
            ).apply { bottomMargin = dp(c, 10) })
        }
        // Nabız animasyonu sadece ekrandayken çalışsın; kaldırılınca dursun (yoksa sonsuza dek işlemci yer)
        val pulse = ObjectAnimator.ofFloat(this, View.ALPHA, 0.35f, 1f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = pulse.start()
            override fun onViewDetachedFromWindow(v: View) = pulse.cancel()
        })
    }

    /** Yuvarlak arama kutusu. */
    fun searchField(c: Context, hintText: String, onChange: (String) -> Unit): View {
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 14), 0, dp(c, 14), 0)
            background = rounded(surface(c), dp(c, 14).toFloat())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 50)).apply {
                bottomMargin = dp(c, 12)
            }
        }
        row.addView(ImageView(c).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(secondary(c))
        }, LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)).apply { rightMargin = dp(c, 10) })
        row.addView(EditText(c).apply {
            hint = hintText
            isSingleLine = true
            textSize = 15f
            background = null
            setTextColor(primary(c))
            setHintTextColor(secondary(c))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = onChange(s?.toString() ?: "")
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /** Ayar menüsü satırı: simge balonu, başlık, özet ve ok. */
    fun navRow(c: Context, icon: String, title: String, summary: String, onClick: () -> Unit): View =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = dp(c, 12)
            setPadding(p, p, p, p)
            minimumHeight = dp(c, 64)
            background = ripple(rounded(0, dp(c, 18).toFloat()))
            isClickable = true
            pressable(this, 0.98f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            addView(TextView(c).apply {
                textSize = 22f
                gravity = Gravity.CENTER
                Icons.apply(this, icon, accentText(c))
                background = rounded(0, dp(c, 12).toFloat(), dp(c, 1), outline(c))
            }, LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)).apply { rightMargin = dp(c, 14) })
            val texts = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(text(c, title, 16f).apply {
                typeface = MEDIUM
                setPadding(0, 0, 0, dp(c, 1))
            })
            if (summary.isNotEmpty()) texts.addView(hint(c, summary).apply { maxLines = 2 })
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(c).apply {
                setImageResource(R.drawable.ic_chevron)
                imageTintList = ColorStateList.valueOf(secondary(c))
            }, LinearLayout.LayoutParams(dp(c, 18), dp(c, 18)))
        }

    /** Kartın içinde ince ayırıcı çizgi. */
    fun divider(c: Context): View = View(c).apply {
        setBackgroundColor(outline(c))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = dp(c, 2)
            bottomMargin = dp(c, 2)
        }
    }

    /** Görünürken yumuşakça belirme. */
    fun fadeIn(v: View, delay: Long = 0) {
        v.alpha = 0f
        v.translationY = dp(v.context, 12).toFloat()
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(320)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    /** Başlık, büyük değer ve alt bilgi içeren küçük özet kutusu. */
    class StatTile(c: Context, title: String) : LinearLayout(c) {
        val title: TextView
        val value: TextView
        val sub: TextView

        init {
            orientation = VERTICAL
            val pad = Ui.dp(c, 14)
            setPadding(pad, pad, pad, pad)
            this.title = Ui.label(c, title).apply { setPadding(0, 0, 0, Ui.dp(c, 4)) }
            addView(this.title)
            isClickable = true
            background = Ui.ripple(Ui.rounded(Ui.surface(c), Ui.dp(c, 16).toFloat()))
            value = Ui.mono(Ui.text(c, "—", 21f))
            sub = Ui.hint(c, "")
            addView(value)
            addView(sub)
        }
    }

    /** İki kutuyu yan yana koyar. */
    fun pairRow(c: Context, left: View, right: View): View = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(c, 8) }
        addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { rightMargin = dp(c, 4) })
        addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = dp(c, 4) })
    }
}
