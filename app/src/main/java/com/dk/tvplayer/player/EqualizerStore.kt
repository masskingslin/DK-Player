package com.dk.tvplayer.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln

/** Centre frequencies (Hz) of the 10 user-facing equalizer bands. */
val EqBandFrequencies = intArrayOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

const val EQ_MIN_DB = -20f
const val EQ_MAX_DB = 20f

data class EqPreset(
    val id: String,
    val name: String,
    val preampDb: Float,
    val bandsDb: List<Float>,
    val builtIn: Boolean
)

/** What the equalizer is currently set to. [presetId] is empty once a band has been
 *  hand-tuned away from any saved preset ("Custom"). */
data class EqualizerSettings(
    val enabled: Boolean = false,
    val presetId: String = "builtin:Flat",
    val preampDb: Float = 0f,
    val bandsDb: List<Float> = List(10) { 0f },
    val snapBands: Boolean = true
)

/**
 * Built-in presets, modelled on VLC's. VLC's own presets carry a +12 dB "preamp" baseline;
 * here the preamp is stored relative to that, so Flat sits at 0 dB and the louder presets
 * come with negative headroom to avoid clipping.
 */
object EqPresets {
    private fun p(name: String, vlcPreamp: Float, vararg b: Float) =
        EqPreset("builtin:$name", name, vlcPreamp - 12f, b.toList(), builtIn = true)

    val builtIn: List<EqPreset> = listOf(
        p("Flat", 12f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        p("Classical", 12f, 0f, 0f, 0f, 0f, 0f, 0f, -7.2f, -7.2f, -7.2f, -9.6f),
        p("Club", 6f, 0f, 0f, 8f, 5.6f, 5.6f, 5.6f, 3.2f, 0f, 0f, 0f),
        p("Dance", 5f, 9.6f, 7.2f, 2.4f, 0f, 0f, -5.6f, -7.2f, -7.2f, 0f, 0f),
        p("Full bass", 5f, -8f, 9.6f, 9.6f, 5.6f, 1.6f, -4f, -8f, -10.4f, -11.2f, -11.2f),
        p("Full bass and treble", 4f, 7.2f, 5.6f, 0f, -7.2f, -4.8f, 1.6f, 8f, 11.2f, 12f, 12f),
        p("Full treble", 3f, -9.6f, -9.6f, -9.6f, -4f, 2.4f, 11.2f, 16f, 16f, 16f, 16.8f),
        p("Headphones", 4f, 4.8f, 11.2f, 5.6f, -3.2f, -2.4f, 1.6f, 4.8f, 9.6f, 12.8f, 14.4f),
        p("Large Hall", 5f, 10.4f, 10.4f, 5.6f, 5.6f, 0f, -4.8f, -4.8f, -4.8f, 0f, 0f),
        p("Live", 7f, -4.8f, 0f, 4f, 5.6f, 5.6f, 5.6f, 4f, 2.4f, 2.4f, 2.4f),
        p("Party", 6f, 7.2f, 7.2f, 0f, 0f, 0f, 0f, 0f, 0f, 7.2f, 7.2f),
        p("Pop", 5f, -1.6f, 4.8f, 7.2f, 8f, 5.6f, 0f, -2.4f, -2.4f, -1.6f, -1.6f),
        p("Reggae", 8f, 0f, 0f, 0f, -5.6f, 0f, 6.4f, 6.4f, 0f, 0f, 0f),
        p("Rock", 5f, 8f, 4.8f, -5.6f, -8f, -3.2f, 4f, 8.8f, 11.2f, 11.2f, 11.2f),
        p("Ska", 6f, -2.4f, -4.8f, -4f, 0f, 4f, 5.6f, 8.8f, 9.6f, 11.2f, 9.6f),
        p("Soft", 5f, 4.8f, 1.6f, 0f, -2.4f, 0f, 4f, 8f, 9.6f, 11.2f, 12f),
        p("Soft rock", 7f, 4f, 4f, 2.4f, 0f, -4f, -5.6f, -3.2f, 0f, 2.4f, 8.8f),
        p("Techno", 5f, 8f, 5.6f, 0f, -5.6f, -4.8f, 0f, 8f, 9.6f, 9.6f, 8.8f)
    )

    /** Interpolates the 10-band curve (in log-frequency) at [freqHz]. */
    fun levelAt(bandsDb: List<Float>, freqHz: Float): Float {
        if (bandsDb.isEmpty()) return 0f
        val f = freqHz.coerceIn(EqBandFrequencies.first().toFloat(), EqBandFrequencies.last().toFloat())
        for (i in 0 until EqBandFrequencies.size - 1) {
            val lo = EqBandFrequencies[i].toFloat()
            val hi = EqBandFrequencies[i + 1].toFloat()
            if (f <= hi) {
                val t = (ln(f) - ln(lo)) / (ln(hi) - ln(lo))
                return bandsDb[i] + (bandsDb[i + 1] - bandsDb[i]) * t
            }
        }
        return bandsDb.last()
    }
}

/** Persists the equalizer settings and the user's custom presets (SharedPreferences,
 *  so no database migration is needed). */
object EqualizerStore {
    private const val PREFS = "dk_equalizer"
    private var appContext: Context? = null

    private val _state = MutableStateFlow(EqualizerSettings())
    val state: StateFlow<EqualizerSettings> = _state.asStateFlow()

    private val _custom = MutableStateFlow<List<EqPreset>>(emptyList())
    val customPresets: StateFlow<List<EqPreset>> = _custom.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = prefs() ?: return
        val bands = prefs.getString("bands", null)?.split(",")?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 10 } ?: List(10) { 0f }
        _state.value = EqualizerSettings(
            enabled = prefs.getBoolean("enabled", false),
            presetId = prefs.getString("presetId", "builtin:Flat") ?: "builtin:Flat",
            preampDb = prefs.getFloat("preamp", 0f),
            bandsDb = bands,
            snapBands = prefs.getBoolean("snap", true)
        )
        runCatching {
            val arr = JSONArray(prefs.getString("custom", "[]"))
            _custom.value = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val b = o.getJSONArray("bands")
                EqPreset(
                    id = "custom:" + o.getString("name"),
                    name = o.getString("name"),
                    preampDb = o.optDouble("preamp", 0.0).toFloat(),
                    bandsDb = (0 until b.length()).map { b.getDouble(it).toFloat() },
                    builtIn = false
                )
            }.filter { it.bandsDb.size == 10 }
        }
    }

    fun allPresets(): List<EqPreset> = EqPresets.builtIn + _custom.value

    fun findPreset(id: String): EqPreset? = allPresets().firstOrNull { it.id == id }

    fun update(transform: (EqualizerSettings) -> EqualizerSettings) {
        _state.value = transform(_state.value)
        persistState()
    }

    fun selectPreset(preset: EqPreset) = update {
        it.copy(presetId = preset.id, preampDb = preset.preampDb, bandsDb = preset.bandsDb, enabled = true)
    }

    /** Saves the current curve under [name] (replacing a custom preset of that name). */
    fun saveCurrentAs(name: String): EqPreset {
        val s = _state.value
        val preset = EqPreset("custom:$name", name, s.preampDb, s.bandsDb, builtIn = false)
        _custom.value = _custom.value.filterNot { it.id == preset.id } + preset
        persistCustom()
        update { it.copy(presetId = preset.id) }
        return preset
    }

    fun renameCustom(id: String, newName: String) {
        val old = _custom.value.firstOrNull { it.id == id } ?: return
        val renamed = old.copy(id = "custom:$newName", name = newName)
        _custom.value = _custom.value.map { if (it.id == id) renamed else it }
        persistCustom()
        if (_state.value.presetId == id) update { it.copy(presetId = renamed.id) }
    }

    fun deleteCustom(id: String) {
        _custom.value = _custom.value.filterNot { it.id == id }
        persistCustom()
        if (_state.value.presetId == id) selectPreset(EqPresets.builtIn.first())
    }

    /** Keeps a custom preset in sync while its sliders are being edited. */
    fun writeThroughIfCustom() {
        val s = _state.value
        val idx = _custom.value.indexOfFirst { it.id == s.presetId }
        if (idx >= 0) {
            _custom.value = _custom.value.toMutableList().also {
                it[idx] = it[idx].copy(preampDb = s.preampDb, bandsDb = s.bandsDb)
            }
            persistCustom()
        }
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun persistState() {
        val s = _state.value
        prefs()?.edit()
            ?.putBoolean("enabled", s.enabled)
            ?.putString("presetId", s.presetId)
            ?.putFloat("preamp", s.preampDb)
            ?.putString("bands", s.bandsDb.joinToString(","))
            ?.putBoolean("snap", s.snapBands)
            ?.apply()
    }

    private fun persistCustom() {
        val arr = JSONArray()
        _custom.value.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("preamp", it.preampDb.toDouble())
                    .put("bands", JSONArray(it.bandsDb.map { v -> v.toDouble() }))
            )
        }
        prefs()?.edit()?.putString("custom", arr.toString())?.apply()
    }
}
