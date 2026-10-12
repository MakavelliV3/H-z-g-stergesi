package com.emir.hizgostergesi

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

/** Hangi uygulama ne kadar mobil veri / Wi-Fi kullandı. "Kullanım erişimi" izni ister. */
class AppsActivity : Activity() {

    private class Row(val uid: Int) {
        var mobile = 0L
        var wifi = 0L
        var name = ""
        var icon: Drawable? = null
        val total get() = mobile + wifi
    }

    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private var range = 0
    private var loadId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, root) = Ui.page(this)

        root.addView(Ui.choiceRow(
            this,
            listOf("Bugün" to 0, "Bu dönem" to 1, "Son 7 gün" to 2, "Son 30 gün" to 3),
            range
        ) {
            range = it
            load()
        })
        status = Ui.hint(this, "")
        root.addView(status)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (hasAccess()) load() else askAccess()
    }

    private fun hasAccess(): Boolean {
        val ops = getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    private fun askAccess() {
        status.text = ""
        content.removeAllViews()
        content.addView(Ui.text(this, "İzin gerekiyor", 18f, bold = true, topDp = 12))
        content.addView(Ui.text(this,
            "Uygulamaların ne kadar veri kullandığını görmek için Android'in \"Kullanım erişimi\" izni lazım. " +
                "Açılan listede Hız Göstergesi'ni bulup izni aç, sonra geri dön.", 15f))
        content.addView(Ui.button(this, "İzin ver", filled = true) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
    }

    private fun load() {
        if (!hasAccess()) {
            askAccess()
            return
        }
        val id = ++loadId
        status.text = "Yükleniyor…"
        val (start, end) = rangeMillis()
        Thread {
            val rows = query(start, end)
            runOnUiThread { if (id == loadId) render(rows) }
        }.start()
    }

    private fun rangeMillis(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val start = when (range) {
            0 -> Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            1 -> Prefs.get(this).getLong(Prefs.WINDOW_START, now - 3_600_000L)
            2 -> now - 7 * 86_400_000L
            else -> now - 30 * 86_400_000L
        }
        return start to now + 60_000L
    }

    @Suppress("DEPRECATION")
    private fun query(start: Long, end: Long): List<Row> {
        val nsm = getSystemService(NetworkStatsManager::class.java)
        val map = HashMap<Int, Row>()

        fun collect(type: Int) {
            try {
                val stats = nsm.querySummary(type, null, start, end)
                val b = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    val row = map.getOrPut(b.uid) { Row(b.uid) }
                    val bytes = b.rxBytes + b.txBytes
                    if (type == ConnectivityManager.TYPE_MOBILE) row.mobile += bytes else row.wifi += bytes
                }
                stats.close()
            } catch (e: Exception) {
                // bu ağ türü için veri yok
            }
        }
        collect(ConnectivityManager.TYPE_MOBILE)
        collect(ConnectivityManager.TYPE_WIFI)

        val rows = map.values.filter { it.total > 0 }.sortedByDescending { it.total }.take(60)
        rows.forEach { describe(it) }
        return rows
    }

    private fun describe(r: Row) {
        when (r.uid) {
            NetworkStats.Bucket.UID_REMOVED -> r.name = "Kaldırılmış uygulamalar"
            NetworkStats.Bucket.UID_TETHERING -> r.name = "Hotspot / internet paylaşımı"
            Process.SYSTEM_UID -> r.name = "Android sistemi"
            0 -> r.name = "Sistem çekirdeği"
            else -> {
                val pkgs = packageManager.getPackagesForUid(r.uid)
                if (pkgs.isNullOrEmpty()) {
                    r.name = "Bilinmeyen (uid ${r.uid})"
                    return
                }
                try {
                    val ai = packageManager.getApplicationInfo(pkgs[0], 0)
                    val label = ai.loadLabel(packageManager).toString()
                    r.name = if (pkgs.size > 1) "$label +${pkgs.size - 1}" else label
                    r.icon = ai.loadIcon(packageManager)
                } catch (e: Exception) {
                    r.name = pkgs[0]
                }
            }
        }
    }

    private fun render(rows: List<Row>) {
        content.removeAllViews()
        if (rows.isEmpty()) {
            status.text = "Bu aralık için kayıt bulunamadı."
            return
        }
        val total = rows.sumOf { it.total }
        status.text = "Toplam ${Fmt.bytes(total)} • en çok kullanan ${rows.size} uygulama"

        val iconSize = Ui.dp(this, 38)
        rows.forEach { r ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(this@AppsActivity, 8), 0, Ui.dp(this@AppsActivity, 8))
            }
            val icon = ImageView(this).apply {
                if (r.icon != null) setImageDrawable(r.icon) else setImageResource(android.R.drawable.sym_def_app_icon)
            }
            line.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize).apply {
                rightMargin = Ui.dp(this@AppsActivity, 12)
            })

            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(this).apply {
                text = r.name
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            })
            texts.addView(TextView(this).apply {
                text = "Mobil ${Fmt.bytes(r.mobile)}  •  Wi-Fi ${Fmt.bytes(r.wifi)}"
                textSize = 13f
                alpha = 0.75f
            })
            line.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            line.addView(TextView(this).apply {
                text = Fmt.bytes(r.total)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            })
            content.addView(line)
        }
    }
}
