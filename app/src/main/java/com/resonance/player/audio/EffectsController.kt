package com.resonance.player.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** The five reverb options requested, mapped to Android's built-in preset reverbs. */
enum class Reverb(val label: String, val preset: Short) {
    NONE("None", PresetReverb.PRESET_NONE),
    SMALL_ROOM("Small room", PresetReverb.PRESET_SMALLROOM),
    MEDIUM_ROOM("Medium room", PresetReverb.PRESET_MEDIUMROOM),
    LARGE_ROOM("Large room", PresetReverb.PRESET_LARGEROOM),
    MEDIUM_HALL("Medium hall", PresetReverb.PRESET_MEDIUMHALL),
    LARGE_HALL("Large hall", PresetReverb.PRESET_LARGEHALL),
}

/** Band centre frequencies (Hz) and gain range (millibels) reported by the device's equalizer. */
data class BandInfo(val centerHz: List<Int>, val minMb: Int, val maxMb: Int)

data class EqState(
    val enabled: Boolean = true,
    val preset: String = "Flat",
    val bands: BandInfo = BandInfo(listOf(60, 230, 910, 3600, 14000), -1500, 1500),
    /** Gain per band in millibels (100 mB = 1 dB). */
    val levels: List<Int> = List(5) { 0 },
    /** 0..1000 */
    val bassBoost: Int = 0,
    /** 0..1000 */
    val trebleBoost: Int = 0,
    val reverb: Reverb = Reverb.NONE,
)

object EffectsController {
    const val CUSTOM = "Custom"

    /** Reference curves in dB for a classic 5-band layout (low -> high); resampled to the device's band count. */
    private val PRESET_DB: Map<String, FloatArray> = linkedMapOf(
        "Normal" to floatArrayOf(3f, 0f, 0f, 0f, 3f),
        "Classical" to floatArrayOf(5f, 3f, -2f, 4f, 4f),
        "Dance" to floatArrayOf(6f, 0f, 2f, 4f, 1f),
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Folk" to floatArrayOf(3f, 0f, 0f, 2f, -1f),
        "Heavy Metal" to floatArrayOf(4f, 1f, 9f, 3f, 0f),
        "Hip Hop" to floatArrayOf(5f, 3f, 0f, 1f, 3f),
        "Jazz" to floatArrayOf(4f, 2f, -2f, 2f, 5f),
        "Pop" to floatArrayOf(-1f, 2f, 5f, 1f, -2f),
        "Rock" to floatArrayOf(5f, 3f, -1f, 3f, 5f),
    )

    val presetNames: List<String> = PRESET_DB.keys.toList() + CUSTOM

    private var prefs: SharedPreferences? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var reverbFx: PresetReverb? = null
    private var customLevels: List<Int> = emptyList()

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("effects", Context.MODE_PRIVATE)
        prefs = p
        customLevels = (p.getString("custom", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }
        _state.value = _state.value.copy(
            enabled = p.getBoolean("enabled", true),
            preset = p.getString("preset", "Flat") ?: "Flat",
            bassBoost = p.getInt("bass", 0),
            trebleBoost = p.getInt("treble", 0),
            reverb = Reverb.entries.getOrElse(p.getInt("reverb", 0)) { Reverb.NONE },
        ).withLevels()
    }

    /** Called by the playback service whenever the player's audio session is (re)created. */
    @Synchronized
    fun attach(sessionId: Int) {
        if (sessionId <= 0) return
        releaseEffects()
        try {
            val e = Equalizer(0, sessionId)
            val range = e.bandLevelRange
            val centers = List(e.numberOfBands.toInt()) { e.getCenterFreq(it.toShort()) / 1000 }
            eq = e
            _state.update { it.copy(bands = BandInfo(centers, range[0].toInt(), range[1].toInt())).withLevels() }
        } catch (_: Throwable) {
            eq = null
        }
        bass = try { BassBoost(0, sessionId) } catch (_: Throwable) { null }
        reverbFx = try { PresetReverb(0, sessionId) } catch (_: Throwable) { null }
        apply()
    }

    @Synchronized
    fun release() = releaseEffects()

    private fun releaseEffects() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        runCatching { reverbFx?.release() }
        eq = null; bass = null; reverbFx = null
    }

    // ---- public setters used by the UI ----

    fun setEnabled(on: Boolean) = commit(_state.value.copy(enabled = on))

    fun selectPreset(name: String) = commit(_state.value.copy(preset = name).withLevels())

    /** Moving any band slider switches to the user's own "Custom" setting (saved automatically). */
    fun setBand(index: Int, mb: Int) {
        val s = _state.value
        val l = s.levels.toMutableList()
        if (index !in l.indices) return
        l[index] = mb.coerceIn(s.bands.minMb, s.bands.maxMb)
        customLevels = l
        commit(s.copy(preset = CUSTOM, levels = l))
    }

    fun setBass(strength: Int) = commit(_state.value.copy(bassBoost = strength.coerceIn(0, 1000)))

    fun setTreble(strength: Int) = commit(_state.value.copy(trebleBoost = strength.coerceIn(0, 1000)))

    fun setReverb(r: Reverb) = commit(_state.value.copy(reverb = r))

    // ---- internals ----

    private fun commit(s: EqState) {
        _state.value = s
        prefs?.edit()
            ?.putBoolean("enabled", s.enabled)
            ?.putString("preset", s.preset)
            ?.putString("custom", customLevels.joinToString(","))
            ?.putInt("bass", s.bassBoost)
            ?.putInt("treble", s.trebleBoost)
            ?.putInt("reverb", s.reverb.ordinal)
            ?.apply()
        apply()
    }

    private fun EqState.withLevels(): EqState = copy(levels = levelsFor(preset, bands))

    private fun levelsFor(preset: String, bands: BandInfo): List<Int> {
        val n = bands.centerHz.size
        if (preset == CUSTOM) {
            return if (customLevels.size == n) customLevels else List(n) { 0 }
        }
        val db = PRESET_DB[preset] ?: return List(n) { 0 }
        return List(n) { i ->
            val pos = if (n == 1) 0f else i * (db.size - 1).toFloat() / (n - 1)
            val lo = floor(pos).toInt()
            val hi = min(lo + 1, db.size - 1)
            val f = pos - lo
            ((db[lo] * (1 - f) + db[hi] * f) * 100).roundToInt().coerceIn(bands.minMb, bands.maxMb)
        }
    }

    /**
     * Android has no dedicated treble-boost effect, so it is built on the equalizer:
     * the upper bands (>= 2 kHz) get extra gain on top of the preset / custom curve, up to +6 dB.
     */
    private fun effectiveLevel(s: EqState, i: Int): Int {
        val base = s.levels.getOrElse(i) { 0 }
        val hz = s.bands.centerHz.getOrElse(i) { 1000 }
        val weight = when {
            hz >= 4000 -> 1f
            hz >= 2000 -> 0.5f
            else -> 0f
        }
        val boost = (s.trebleBoost / 1000f * 600f * weight).roundToInt()
        return (base + boost).coerceIn(s.bands.minMb, s.bands.maxMb)
    }

    @Synchronized
    private fun apply() {
        val s = _state.value
        try {
            eq?.let { e ->
                e.setEnabled(s.enabled)
                for (i in 0 until e.numberOfBands) {
                    e.setBandLevel(i.toShort(), effectiveLevel(s, i).toShort())
                }
            }
        } catch (_: Throwable) {
        }
        try {
            bass?.let { b ->
                val on = s.enabled && s.bassBoost > 0
                b.setEnabled(on)
                if (on) b.setStrength(s.bassBoost.toShort())
            }
        } catch (_: Throwable) {
        }
        try {
            reverbFx?.let { r ->
                val on = s.enabled && s.reverb != Reverb.NONE
                r.setEnabled(on)
                if (on) r.setPreset(s.reverb.preset)
            }
        } catch (_: Throwable) {
        }
    }
}
