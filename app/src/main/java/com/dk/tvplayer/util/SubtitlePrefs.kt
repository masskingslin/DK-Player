package com.dk.tvplayer.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class SubtitleSize(val label: String, val sp: Float) {
    SMALL("Small", 14f),
    NORMAL("Normal", 18f),
    LARGE("Large", 24f),
    EXTRA_LARGE("Extra large", 30f)
}

/** Outline thickness, in dp of visible edge around each letter. */
enum class OutlineSize(val label: String, val dp: Float) {
    THIN("Thin", 1f),
    NORMAL("Normal", 2f),
    THICK("Thick", 3.5f)
}

/** Everything about how subtitles look. Colours are opaque RGB; opacity is separate (0..1). */
data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.NORMAL,
    val bold: Boolean = false,
    val color: Int = Color.WHITE,
    val opacity: Float = 1f,
    val backgroundEnabled: Boolean = false,
    val backgroundColor: Int = Color.BLACK,
    val backgroundOpacity: Float = 0.6f,
    val shadowEnabled: Boolean = true,
    val shadowColor: Int = Color.BLACK,
    val shadowOpacity: Float = 0.5f,
    val outlineEnabled: Boolean = true,
    val outlineColor: Int = Color.BLACK,
    val outlineOpacity: Float = 1f,
    val outlineSize: OutlineSize = OutlineSize.NORMAL
)

data class SubtitlePreset(val name: String, val style: SubtitleStyle)

/** Choosable text encodings for external subtitle files. */
enum class SubtitleEncoding(val label: String, val charsetName: String?) {
    DEFAULT("Default (UTF-8, else Windows-1252)", null),
    UTF8("UTF-8", "UTF-8"),
    WIN1252("Windows-1252 (Western European)", "windows-1252"),
    LATIN1("ISO-8859-1 (Latin-1)", "ISO-8859-1"),
    LATIN2("ISO-8859-2 (Central European)", "ISO-8859-2"),
    WIN1250("Windows-1250 (Central European)", "windows-1250"),
    WIN1251("Windows-1251 (Cyrillic)", "windows-1251"),
    WIN1253("Windows-1253 (Greek)", "windows-1253"),
    WIN1254("Windows-1254 (Turkish)", "windows-1254"),
    WIN1255("Windows-1255 (Hebrew)", "windows-1255"),
    WIN1256("Windows-1256 (Arabic)", "windows-1256"),
    WIN1257("Windows-1257 (Baltic)", "windows-1257"),
    TIS620("TIS-620 (Thai)", "TIS-620"),
    GBK("GBK (Simplified Chinese)", "GBK"),
    BIG5("Big5 (Traditional Chinese)", "Big5"),
    SHIFT_JIS("Shift_JIS (Japanese)", "Shift_JIS"),
    EUC_KR("EUC-KR (Korean)", "EUC-KR")
}

/** (label, BCP-47 tag); an empty tag means "no preference". */
val SubtitleLanguages: List<Pair<String, String>> = listOf(
    "No language preference" to "", "English" to "en", "Spanish" to "es", "French" to "fr",
    "German" to "de", "Portuguese" to "pt", "Italian" to "it", "Hindi" to "hi", "Tamil" to "ta",
    "Telugu" to "te", "Malayalam" to "ml", "Kannada" to "kn", "Bengali" to "bn", "Arabic" to "ar",
    "Russian" to "ru", "Turkish" to "tr", "Indonesian" to "id", "Japanese" to "ja",
    "Korean" to "ko", "Chinese" to "zh"
)

object SubtitlePrefs {
    private const val PREFS = "dk_subtitles"
    private var appContext: Context? = null

    val presets: List<SubtitlePreset> = listOf(
        SubtitlePreset("Default", SubtitleStyle()),
        SubtitlePreset("Classic (white, black outline)", SubtitleStyle(shadowEnabled = false)),
        SubtitlePreset("High contrast (white on black box)",
            SubtitleStyle(backgroundEnabled = true, backgroundOpacity = 0.85f, outlineEnabled = false, shadowEnabled = false)),
        SubtitlePreset("Cinema (yellow, soft shadow)",
            SubtitleStyle(color = 0xFFFFEB3B.toInt(), outlineEnabled = false, shadowEnabled = true, shadowOpacity = 0.8f)),
        SubtitlePreset("Large & bold",
            SubtitleStyle(size = SubtitleSize.LARGE, bold = true)),
        SubtitlePreset("Accessible (large yellow on black box)",
            SubtitleStyle(size = SubtitleSize.EXTRA_LARGE, bold = true, color = 0xFFFFEB3B.toInt(),
                backgroundEnabled = true, backgroundOpacity = 0.9f, outlineEnabled = false, shadowEnabled = false))
    )

    private val _style = MutableStateFlow(SubtitleStyle())
    val style: StateFlow<SubtitleStyle> = _style.asStateFlow()

    private val _autoLoad = MutableStateFlow(true)
    val autoLoad: StateFlow<Boolean> = _autoLoad.asStateFlow()

    private val _encoding = MutableStateFlow(SubtitleEncoding.DEFAULT)
    val encoding: StateFlow<SubtitleEncoding> = _encoding.asStateFlow()

    private val _language = MutableStateFlow("")
    val language: StateFlow<String> = _language.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val p = prefs() ?: return
        val d = SubtitleStyle()
        _style.value = SubtitleStyle(
            size = runCatching { SubtitleSize.valueOf(p.getString("size", d.size.name)!!) }.getOrDefault(d.size),
            bold = p.getBoolean("bold", d.bold),
            color = p.getInt("color", d.color),
            opacity = p.getFloat("opacity", d.opacity),
            backgroundEnabled = p.getBoolean("bgOn", d.backgroundEnabled),
            backgroundColor = p.getInt("bgColor", d.backgroundColor),
            backgroundOpacity = p.getFloat("bgOpacity", d.backgroundOpacity),
            shadowEnabled = p.getBoolean("shadowOn", d.shadowEnabled),
            shadowColor = p.getInt("shadowColor", d.shadowColor),
            shadowOpacity = p.getFloat("shadowOpacity", d.shadowOpacity),
            outlineEnabled = p.getBoolean("outlineOn", d.outlineEnabled),
            outlineColor = p.getInt("outlineColor", d.outlineColor),
            outlineOpacity = p.getFloat("outlineOpacity", d.outlineOpacity),
            outlineSize = runCatching { OutlineSize.valueOf(p.getString("outlineSize", d.outlineSize.name)!!) }
                .getOrDefault(d.outlineSize)
        )
        _autoLoad.value = p.getBoolean("autoLoad", true)
        _encoding.value = runCatching { SubtitleEncoding.valueOf(p.getString("encoding", "DEFAULT")!!) }
            .getOrDefault(SubtitleEncoding.DEFAULT)
        _language.value = p.getString("language", "") ?: ""
    }

    fun update(transform: (SubtitleStyle) -> SubtitleStyle) {
        val s = transform(_style.value)
        _style.value = s
        prefs()?.edit()
            ?.putString("size", s.size.name)?.putBoolean("bold", s.bold)
            ?.putInt("color", s.color)?.putFloat("opacity", s.opacity)
            ?.putBoolean("bgOn", s.backgroundEnabled)?.putInt("bgColor", s.backgroundColor)
            ?.putFloat("bgOpacity", s.backgroundOpacity)
            ?.putBoolean("shadowOn", s.shadowEnabled)?.putInt("shadowColor", s.shadowColor)
            ?.putFloat("shadowOpacity", s.shadowOpacity)
            ?.putBoolean("outlineOn", s.outlineEnabled)?.putInt("outlineColor", s.outlineColor)
            ?.putFloat("outlineOpacity", s.outlineOpacity)
            ?.putString("outlineSize", s.outlineSize.name)
            ?.apply()
    }

    fun setAutoLoad(v: Boolean) { _autoLoad.value = v; prefs()?.edit()?.putBoolean("autoLoad", v)?.apply() }
    fun setEncoding(v: SubtitleEncoding) { _encoding.value = v; prefs()?.edit()?.putString("encoding", v.name)?.apply() }
    fun setLanguage(tag: String) { _language.value = tag; prefs()?.edit()?.putString("language", tag)?.apply() }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

private fun withAlpha(rgb: Int, opacity: Float): Int =
    (rgb and 0x00FFFFFF) or ((opacity.coerceIn(0f, 1f) * 255).toInt() shl 24)

/**
 * Applies [style] to a Media3 SubtitleView. This is only the fallback used for picture-based
 * subtitles (e.g. PGS/DVB discs); ordinary text subtitles are drawn by StyledSubtitleText, which
 * supports outline thickness and outline + shadow together.
 */
fun applySubtitleStyle(view: SubtitleView, style: SubtitleStyle) {
    view.setApplyEmbeddedStyles(false)
    view.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, style.size.sp)
    val (edgeType, edgeColor) = when {
        style.outlineEnabled ->
            CaptionStyleCompat.EDGE_TYPE_OUTLINE to withAlpha(style.outlineColor, style.outlineOpacity)
        style.shadowEnabled ->
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to withAlpha(style.shadowColor, style.shadowOpacity)
        else -> CaptionStyleCompat.EDGE_TYPE_NONE to Color.BLACK
    }
    view.setStyle(
        CaptionStyleCompat(
            withAlpha(style.color, style.opacity),
            if (style.backgroundEnabled) withAlpha(style.backgroundColor, style.backgroundOpacity) else Color.TRANSPARENT,
            Color.TRANSPARENT,
            edgeType,
            edgeColor,
            if (style.bold) Typeface.DEFAULT_BOLD else null
        )
    )
}

/** Shows a sample line in [view] so settings screens can preview the style. */
fun showSubtitlePreview(view: SubtitleView, style: SubtitleStyle) {
    applySubtitleStyle(view, style)
    view.setCues(listOf(Cue.Builder().setText("This is how subtitles will look").build()))
}

/**
 * Finds subtitle files that sit next to a local video (same name, e.g. Movie.srt,
 * Movie.en.srt) and turns them into Media3 subtitle configurations, converting them to
 * UTF-8 first when their encoding isn't UTF-8.
 */
object SidecarSubtitles {
    private val exts = mapOf(
        "srt" to MimeTypes.APPLICATION_SUBRIP,
        "vtt" to MimeTypes.TEXT_VTT,
        "ass" to MimeTypes.TEXT_SSA,
        "ssa" to MimeTypes.TEXT_SSA
    )
    private const val MAX_BYTES = 5L * 1024 * 1024

    fun find(context: Context, mediaPath: String, encoding: SubtitleEncoding): List<MediaItem.SubtitleConfiguration> {
        if (mediaPath.startsWith("http") || mediaPath.startsWith("content:")) return emptyList()
        return runCatching {
            val video = File(mediaPath)
            val dir = video.parentFile ?: return emptyList()
            val base = video.nameWithoutExtension.lowercase()
            val candidates = (dir.listFiles() ?: emptyArray())
                .filter { it.isFile && it.extension.lowercase() in exts && it.length() in 1..MAX_BYTES }
                .filter {
                    val n = it.nameWithoutExtension.lowercase()
                    n == base || n.startsWith("$base.")
                }
                .sortedBy { it.name }
            candidates.mapIndexedNotNull { index, file ->
                val uri = prepare(context, file, encoding) ?: return@mapIndexedNotNull null
                val lang = file.nameWithoutExtension.substringAfterLast('.', "").takeIf { it.length in 2..3 }
                MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(exts.getValue(file.extension.lowercase()))
                    .setLanguage(lang)
                    .setLabel(file.name)
                    .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
        }.getOrDefault(emptyList())
    }

    private fun prepare(context: Context, file: File, encoding: SubtitleEncoding): Uri? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val source: Charset = when (val name = encoding.charsetName) {
            null -> if (isValidUtf8(bytes)) return Uri.fromFile(file) else Charset.forName("windows-1252")
            else -> Charset.forName(name)
        }
        if (source.name().equals("UTF-8", ignoreCase = true)) return Uri.fromFile(file)
        val text = String(bytes, source)
        val out = File(File(context.cacheDir, "subs").apply { mkdirs() },
            "${file.absolutePath.hashCode()}_${encoding.name}.${file.extension}")
        out.writeText(text, Charsets.UTF_8)
        return Uri.fromFile(out)
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (e: Exception) {
        false
    }
}
