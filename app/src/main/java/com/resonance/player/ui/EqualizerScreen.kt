package com.resonance.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resonance.player.audio.EffectsController
import com.resonance.player.audio.Reverb
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val s by EffectsController.state.collectAsStateWithLifecycle()
    val accent = LocalPalette.current.accent
    val chipColors = FilterChipDefaults.filterChipColors(
        labelColor = Color.White.copy(alpha = 0.85f),
        selectedContainerColor = accent.copy(alpha = 0.35f),
        selectedLabelColor = Color.White,
    )

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text("Equalizer", fontSize = 30.sp, modifier = Modifier.weight(1f))
            Switch(checked = s.enabled, onCheckedChange = { EffectsController.setEnabled(it) }, modifier = Modifier.padding(end = 12.dp))
        }

        Section("Presets")
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EffectsController.presetNames.forEach { name ->
                FilterChip(
                    selected = s.preset == name,
                    onClick = { EffectsController.selectPreset(name) },
                    label = { Text(name, fontSize = 17.sp) },
                    enabled = s.enabled,
                    colors = chipColors,
                )
            }
        }

        Section("Bands  (drag a slider to create your own Custom setting)")
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            s.bands.centerHz.forEachIndexed { i, hz ->
                val level = s.levels.getOrElse(i) { 0 }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%+.1f dB".format(level / 100f), fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    VerticalSlider(
                        value = level, min = s.bands.minMb, max = s.bands.maxMb, enabled = s.enabled,
                        onChange = { EffectsController.setBand(i, it) },
                        modifier = Modifier.height(190.dp),
                    )
                    Text(freqLabel(hz), fontSize = 14.sp)
                }
            }
        }

        Section("Boost")
        LabeledSlider("Bass boost", s.bassBoost / 1000f, s.enabled) { EffectsController.setBass((it * 1000).roundToInt()) }
        LabeledSlider("Treble boost", s.trebleBoost / 1000f, s.enabled) { EffectsController.setTreble((it * 1000).roundToInt()) }

        Section("Reverb")
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Reverb.entries.forEach { r ->
                FilterChip(
                    selected = s.reverb == r,
                    onClick = { EffectsController.setReverb(r) },
                    label = { Text(r.label, fontSize = 17.sp) },
                    enabled = s.enabled,
                    colors = chipColors,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, fontSize = 22.sp, color = LocalPalette.current.accent,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 19.sp, modifier = Modifier.width(120.dp))
        Slider(
            value = value, onValueChange = onChange, enabled = enabled, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = LocalPalette.current.accent, activeTrackColor = LocalPalette.current.accent),
        )
        Text("${(value * 100).roundToInt()}%", fontSize = 16.sp, modifier = Modifier.width(48.dp))
    }
}

private fun freqLabel(hz: Int) = if (hz >= 1000) {
    val k = hz / 1000f
    (if (k == k.toInt().toFloat()) "${k.toInt()}" else "%.1f".format(k)) + " kHz"
} else "$hz Hz"

/** Vertical slider drawn on a Canvas; fills from the 0 dB line to the thumb. */
@Composable
fun VerticalSlider(value: Int, min: Int, max: Int, enabled: Boolean, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalPalette.current.accent
    val color = if (enabled) accent else Color.White.copy(alpha = 0.3f)
    fun valueAt(y: Float, height: Float, pad: Float): Int {
        val f = 1f - ((y - pad) / (height - 2 * pad)).coerceIn(0f, 1f)
        return (min + f * (max - min)).roundToInt()
    }
    Canvas(
        modifier
            .width(48.dp)
            .pointerInput(min, max, enabled) {
                if (!enabled) return@pointerInput
                val pad = 14.dp.toPx()
                detectTapGestures { o -> onChange(valueAt(o.y, size.height.toFloat(), pad)) }
            }
            .pointerInput(min, max, enabled) {
                if (!enabled) return@pointerInput
                val pad = 14.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { o -> onChange(valueAt(o.y, size.height.toFloat(), pad)) },
                ) { change, _ ->
                    change.consume()
                    onChange(valueAt(change.position.y, size.height.toFloat(), pad))
                }
            },
    ) {
        val cx = size.width / 2
        val pad = 14.dp.toPx()
        val h = size.height - 2 * pad
        val range = (max - min).coerceAtLeast(1)
        val thumbY = pad + h * (1f - (value - min).toFloat() / range)
        val zeroY = pad + h * (1f - (0 - min).toFloat() / range)
        drawLine(Color.White.copy(alpha = 0.2f), Offset(cx, pad), Offset(cx, pad + h), 4.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.35f), Offset(cx - 10.dp.toPx(), zeroY), Offset(cx + 10.dp.toPx(), zeroY), 1.5.dp.toPx())
        drawLine(color, Offset(cx, zeroY), Offset(cx, thumbY), 4.dp.toPx(), StrokeCap.Round)
        drawCircle(color, 11.dp.toPx(), Offset(cx, thumbY))
    }
}
