package com.emir.hizgostergesi

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.widget.TextView

/** Uygulama renk takımı. */
data class Palette(
    val bg: Int, val surface: Int, val primary: Int, val secondary: Int, val outline: Int,
    val accent: Int, val accent2: Int, val accentText: Int, val onAccent: Int
)

/** Hazır temalar. 0 = sistem (Material You ya da klasik mavi). */
object Themes {
    const val SYSTEM = 0
    const val AMOLED = 1
    const val NIGHT_BLUE = 2
    const val GRAPHITE = 3

    val NAMES = listOf("Sistem", "AMOLED siyah", "Gece mavisi", "Grafit mor")

    private fun c(v: Long) = v.toInt()

    private val dark = mapOf(
        AMOLED to Palette(c(0xFF000000), c(0xFF111214), c(0xFFF2F3F5), c(0xFF8B9099), c(0xFF25282E),
            c(0xFF2EE6C5), c(0xFF1FA9E0), c(0xFF2EE6C5), c(0xFF00201A)),
        NIGHT_BLUE to Palette(c(0xFF0A1020), c(0xFF131B30), c(0xFFE8EEFA), c(0xFF8A97B3), c(0xFF243049),
            c(0xFF5AA8FF), c(0xFF7B6CFF), c(0xFF8CC2FF), c(0xFF041A33)),
        GRAPHITE to Palette(c(0xFF121016), c(0xFF1D1A23), c(0xFFEFEAF7), c(0xFF9A92A8), c(0xFF302A3A),
            c(0xFFB48CFF), c(0xFFFF7AC6), c(0xFFC7A8FF), c(0xFF1E0A3D))
    )

    /** Telefon açık temadayken ve "hep karanlık" kapalıyken kullanılan açık hâller. */
    private val light = mapOf(
        AMOLED to Palette(c(0xFFF1F5F4), c(0xFFFFFFFF), c(0xFF121514), c(0xFF5B6563), c(0xFFDCE3E1),
            c(0xFF00897B), c(0xFF0277BD), c(0xFF00796B), c(0xFFFFFFFF)),
        NIGHT_BLUE to Palette(c(0xFFEEF2F9), c(0xFFFFFFFF), c(0xFF101828), c(0xFF56627A), c(0xFFD8DFEC),
            c(0xFF2F6FDB), c(0xFF5B4BDB), c(0xFF2459B8), c(0xFFFFFFFF)),
        GRAPHITE to Palette(c(0xFFF4F1F8), c(0xFFFFFFFF), c(0xFF1A1520), c(0xFF625A6E), c(0xFFE2DCEA),
            c(0xFF7A4FD6), c(0xFFC2408E), c(0xFF6A3FC4), c(0xFFFFFFFF))
    )

    fun palette(theme: Int, night: Boolean): Palette? = (if (night) dark else light)[theme]

    /** Önizleme için koyu takım. */
    fun preview(theme: Int): Palette? = dark[theme]

    fun current(c: Context) = Prefs.get(c).getInt(Prefs.THEME, SYSTEM)

    /** Hazır tema seçili ve "hep karanlık" açıksa uygulama telefon ayarından bağımsız koyu açılır. */
    fun forceDark(c: Context): Boolean {
        val p = Prefs.get(c)
        return p.getInt(Prefs.THEME, SYSTEM) != SYSTEM && p.getBoolean(Prefs.FORCE_DARK, true)
    }

    /** Etkinliğin bağlamını gerekirse karanlık moda çevirir (sistem pencereleri, iletişim kutuları da koyu olur). */
    fun wrap(base: Context): Context {
        if (!forceDark(base)) return base
        val conf = Configuration(base.resources.configuration)
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        return base.createConfigurationContext(conf)
    }
}

/** Uygulama içi simgeler: emojiler yerine tek tip çizgi simgeler (gömülü Material Symbols alt kümesi). */
object Icons {
    private val map = mapOf(
        "📦" to 0xF569, "🪟" to 0xE8AA, "🔋" to 0xE1A5, "📶" to 0xE202, "🔔" to 0xE7F5, "🔍" to 0xEF7A,
        "📭" to 0xE156, "📡" to 0xEBBA, "🌙" to 0xF159, "📋" to 0xE0EE, "⏱" to 0xE425, "🚨" to 0xF083,
        "🎨" to 0xE40A, "🧭" to 0xE87A, "⚙" to 0xE8B8, "📱" to 0xE7BA, "🧮" to 0xEA5F, "🔐" to 0xE899,
        "🍩" to 0xE917, "📈" to 0xE8E5, "📅" to 0xEBCC, "🗂" to 0xE2C8, "🚀" to 0xEB9B, "🕒" to 0xEFD6,
        "⚡" to 0xEA0B, "⇅" to 0xE8D5, "⏳" to 0xE88B, "👁" to 0xE8F4, "📝" to 0xE745, "🎛" to 0xE429,
        "🔘" to 0xE837, "🕐" to 0xEFD6, "📬" to 0xE159, "🎮" to 0xEA28, "🖍" to 0xE23C, "🧩" to 0xE87B,
        "💾" to 0xE161, "🏆" to 0xEA23, "🔕" to 0xE7F6, "🛡" to 0xE9E0, "🌱" to 0xEA35, "👋" to 0xE766,
        "✅" to 0xF0BE, "ℹ" to 0xE88E, "🔄" to 0xE627, "↩" to 0xE166, "📊" to 0xE26B, "🌍" to 0xE80B,
        "🔥" to 0xEA0B, "📤" to 0xF09B, "📥" to 0xF090, "🔌" to 0xE328, "📌" to 0xF10D, "📳" to 0xF2CB,
        "🌗" to 0xEB37, "🌐" to 0xE80B, "🔗" to 0xE1E2, "➕" to 0xE990, "📲" to 0xE1E2, "✈" to 0xE80B
    )

    @Volatile
    private var font: Typeface? = null

    fun typeface(c: Context): Typeface? = font ?: try {
        c.resources.getFont(R.font.icons).also { font = it }
    } catch (e: Exception) {
        null
    }

    /** Emojinin karşılığı olan simge karakteri (yoksa null). */
    fun glyph(emoji: String): String? {
        val key = emoji.replace("️", "").trim()
        return map[key]?.let { String(Character.toChars(it)) }
    }

    /** TextView'a simgeyi koyar; karşılığı yoksa emojiyi olduğu gibi yazar. */
    fun apply(tv: TextView, emoji: String, color: Int) {
        val g = glyph(emoji)
        val tf = typeface(tv.context)
        if (g != null && tf != null) {
            tv.typeface = tf
            tv.text = g
            tv.setTextColor(color)
            tv.includeFontPadding = false
        } else {
            tv.text = emoji
        }
    }
}
