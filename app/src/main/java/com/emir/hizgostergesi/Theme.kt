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

/**
 * Kokpit tasarımı: her zaman saf siyah zemin, hafif kartlar ve tek bir vurgu rengi.
 * Kullanıcı sadece vurgu rengini seçer.
 */
object Themes {
    const val LIME = 0
    const val TURQUOISE = 1
    const val BLUE = 2
    const val ORANGE = 3
    const val PINK = 4

    val NAMES = listOf("Limon", "Turkuaz", "Mavi", "Turuncu", "Pembe")

    private fun c(v: Long) = v.toInt()

    /** (vurgu, vurgu üstündeki yazı) */
    private val ACCENTS = listOf(
        c(0xFFC6FF3D) to c(0xFF121A00),
        c(0xFF2EE6C5) to c(0xFF00201A),
        c(0xFF5AA8FF) to c(0xFF041A33),
        c(0xFFFF9F43) to c(0xFF2A1400),
        c(0xFFFF5C8A) to c(0xFF2B0010)
    )

    const val BG = 0xFF000000.toInt()
    const val SURFACE = 0xFF0C0E10.toInt()
    const val SURFACE_2 = 0xFF15181B.toInt()
    const val OUTLINE = 0xFF1C1F22.toInt()
    const val TEXT = 0xFFEDEFF2.toInt()
    const val TEXT_2 = 0xFF8B93A1.toInt()
    const val LABEL = 0xFF6B7280.toInt()

    /** Vurgunun koyulaştırılmış hâli (dolgu ve degradeler için). */
    private fun dim(color: Int, f: Float = 0.55f): Int {
        val r = ((color shr 16 and 0xFF) * f).toInt()
        val g = ((color shr 8 and 0xFF) * f).toInt()
        val b = ((color and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun palette(theme: Int, @Suppress("UNUSED_PARAMETER") night: Boolean = true): Palette {
        val (accent, on) = ACCENTS[theme.coerceIn(0, ACCENTS.size - 1)]
        return Palette(BG, SURFACE, TEXT, TEXT_2, OUTLINE, accent, dim(accent), accent, on)
    }

    fun preview(theme: Int): Palette = palette(theme)

    /**
     * Seçili vurgu. Eski sürümlerden geçişte: AMOLED → turkuaz, Gece mavisi → mavi, Grafit mor → pembe.
     */
    fun current(c: Context): Int {
        val p = Prefs.get(c)
        if (!p.contains(Prefs.ACCENT_CHOICE)) {
            val old = p.getInt(Prefs.THEME, 0)
            val mapped = when (old) { 1 -> TURQUOISE; 2 -> BLUE; 3 -> PINK; else -> LIME }
            p.edit().putInt(Prefs.ACCENT_CHOICE, mapped).apply()
            return mapped
        }
        return p.getInt(Prefs.ACCENT_CHOICE, LIME).coerceIn(0, ACCENTS.size - 1)
    }

    /** Kokpit tasarımı her zaman karanlık. */
    @Suppress("UNUSED_PARAMETER")
    fun forceDark(c: Context): Boolean = true

    /** Etkinliğin bağlamını karanlık moda çevirir (sistem pencereleri, iletişim kutuları da koyu olur). */
    fun wrap(base: Context): Context {
        val conf = Configuration(base.resources.configuration)
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        return base.createConfigurationContext(conf)
    }
}

/** Rakamlar için sabit genişlikli yazı tipi (gömülü JetBrains Mono alt kümesi). */
object Mono {
    @Volatile private var regular: Typeface? = null
    @Volatile private var medium: Typeface? = null
    @Volatile private var bold: Typeface? = null

    private fun load(c: Context, id: Int): Typeface = try {
        c.resources.getFont(id)
    } catch (e: Exception) {
        Typeface.MONOSPACE
    }

    fun regular(c: Context) = regular ?: load(c, R.font.mono_regular).also { regular = it }
    fun medium(c: Context) = medium ?: load(c, R.font.mono_medium).also { medium = it }
    fun bold(c: Context) = bold ?: load(c, R.font.mono_bold).also { bold = it }
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
