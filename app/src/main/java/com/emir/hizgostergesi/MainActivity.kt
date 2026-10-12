package com.emir.hizgostergesi

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Ayar ekranı: sayaç süresi, sıfırlama, renk ve yüzen pencere. */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var usageText: TextView
    private lateinit var preview: TextView
    private lateinit var overlaySwitch: Switch
    private val swatches = mutableListOf<Pair<View, Int>>()
    private val handler = Handler(Looper.getMainLooper())

    private val colors = listOf(
        "Beyaz" to 0xFFFFFFFF, "Yeşil" to 0xFF4CAF50, "Camgöbeği" to 0xFF00BCD4,
        "Mavi" to 0xFF2196F3, "Mor" to 0xFF9C27B0, "Pembe" to 0xFFE91E63,
        "Kırmızı" to 0xFFF44336, "Turuncu" to 0xFFFF9800, "Sarı" to 0xFFFFEB3B,
        "Siyah" to 0xFF000000
    ).map { it.first to it.second.toInt() }

    private val refresh = object : Runnable {
        override fun run() {
            refreshUsage()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(buildUi())

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        startForegroundService(Intent(this, SpeedService::class.java))
    }

    override fun onResume() {
        super.onResume()
        // Kullanıcı izin ekranından döndüyse anahtarı gerçek duruma göre ayarla
        overlaySwitch.isChecked = prefs.getBoolean(Prefs.OVERLAY, false) && Settings.canDrawOverlays(this)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }

        root.addView(text("Hız Göstergesi", 26f, bold = true))

        // ----- Kullanım sayacı -----
        root.addView(header("Kullanım sayacı"))
        usageText = text("", 16f)
        root.addView(usageText)
        root.addView(text("Sayaç kaç saatte bir sıfırlansın?", 14f, topDp = 14))

        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val current = prefs.getInt(Prefs.HOURS, 1)
        Prefs.HOUR_OPTIONS.forEach { h ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = "$h sa"
                isChecked = h == current
                setOnClickListener {
                    prefs.edit().putInt(Prefs.HOURS, h).apply()
                    refreshUsage()
                }
            })
        }
        root.addView(HorizontalScrollView(this).apply { addView(group) })

        root.addView(Button(this).apply {
            text = "Sayacı şimdi sıfırla"
            setOnClickListener {
                startForegroundService(
                    Intent(this@MainActivity, SpeedService::class.java).setAction(SpeedService.ACTION_RESET)
                )
                Toast.makeText(this@MainActivity, "Sayaç sıfırlandı", Toast.LENGTH_SHORT).show()
            }
        })

        // ----- Renk -----
        root.addView(header("Renk"))
        root.addView(text("Bildirim panelindeki yazıda ve yüzen pencerede kullanılır.", 14f))

        preview = TextView(this).apply {
            text = "↓ 4,2 MB/s  ↑ 120 KB/s"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(0xCC000000.toInt())
                cornerRadius = dp(12).toFloat()
            }
        }
        root.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12); bottomMargin = dp(8) })

        colors.chunked(5).forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (name, col) ->
                val swatch = View(this).apply {
                    contentDescription = name
                    setOnClickListener { setColor(col) }
                }
                line.addView(swatch, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
                swatches += swatch to col
            }
            root.addView(line)
        }

        val hexRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hexInput = EditText(this).apply {
            hint = "Özel renk, ör. #FF9800"
            isSingleLine = true
        }
        hexRow.addView(hexInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        hexRow.addView(Button(this).apply {
            text = "Uygula"
            setOnClickListener {
                val raw = hexInput.text.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                try {
                    setColor(Color.parseColor(raw))
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(this@MainActivity, "Geçersiz renk kodu", Toast.LENGTH_SHORT).show()
                }
            }
        })
        root.addView(hexRow)

        // ----- Yüzen pencere -----
        root.addView(header("Yüzen pencere"))
        overlaySwitch = Switch(this).apply {
            text = "Ekranda yüzen hız penceresi göster"
            textSize = 16f
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(Prefs.OVERLAY, checked).apply()
                if (checked && !Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(
                        this@MainActivity,
                        "Hız Göstergesi'ne \"Diğer uygulamaların üzerinde göster\" izni ver, sonra geri dön",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                }
            }
        }
        root.addView(overlaySwitch)
        root.addView(text("Pencereyi parmağınla sürükleyip istediğin yere taşıyabilirsin.", 14f, topDp = 6))

        setColor(prefs.getInt(Prefs.COLOR, Prefs.DEFAULT_COLOR), save = false)
        refreshUsage()

        return ScrollView(this).apply { addView(root) }
    }

    private fun setColor(color: Int, save: Boolean = true) {
        if (save) prefs.edit().putInt(Prefs.COLOR, color).apply()
        preview.setTextColor(color)
        swatches.forEach { (view, col) ->
            val selected = col == color
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(col)
                setStroke(if (selected) dp(4) else dp(1), if (selected) 0xFF9E9E9E.toInt() else 0x66888888)
            }
        }
    }

    private fun refreshUsage() {
        val hours = prefs.getInt(Prefs.HOURS, 1)
        val now = System.currentTimeMillis()
        val start = prefs.getLong(Prefs.WINDOW_START, now)
        val used = prefs.getLong(Prefs.USED_RX, 0L) + prefs.getLong(Prefs.USED_TX, 0L)
        val left = start + hours * 3_600_000L - now
        usageText.text = "Bu $hours saatlik dönemde: ${Fmt.bytes(used)}\n${Fmt.remaining(left)} sonra sıfırlanır"
    }

    private fun header(s: String) = text(s, 19f, bold = true, topDp = 28)

    private fun text(s: String, size: Float, bold: Boolean = false, topDp: Int = 0) =
        TextView(this).apply {
            text = s
            textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(topDp), 0, dp(4))
        }
}
