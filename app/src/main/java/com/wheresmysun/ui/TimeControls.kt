package com.wheresmysun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wheresmysun.SkyViewModel
import com.wheresmysun.ui.theme.LocalSkyPalette
import kotlin.math.roundToInt

/** Today/Tomorrow, a time-of-day slider, and a jump back to "now". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeControls(vm: SkyViewModel, onInfo: () -> Unit, modifier: Modifier = Modifier) {
    val tf = rememberTimeFormat()
    val time = vm.selectedTime
    val minute = time.hour * 60 + time.minute
    val day = vm.daySky
    val palette = LocalSkyPalette.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = palette.panelAlpha),
        border = palette.panelBorder,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = vm.dayOffset == 0,
                        onClick = { vm.selectDay(0) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Today") }
                    SegmentedButton(
                        selected = vm.dayOffset == 1,
                        onClick = { vm.selectDay(1) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Tomorrow") }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(tf.time(time), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (vm.isLive) "now" else tf.date(time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // A thin track: the default thick filled bar was the heaviest thing on screen.
            val sliderColors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            )
            Slider(
                value = minute.toFloat(),
                onValueChange = { vm.selectMinute((it / 5f).roundToInt() * 5) },
                valueRange = 0f..(24 * 60 - 1).toFloat(),
                colors = sliderColors,
                track = { state ->
                    SliderDefaults.Track(
                        sliderState = state,
                        modifier = Modifier.height(4.dp),
                        colors = sliderColors,
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                    )
                },
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (day != null) {
                    val sun = day.sun
                    val summary = when {
                        sun.alwaysUp -> "Sun up all day"
                        sun.alwaysDown -> "Sun stays down"
                        else -> listOfNotNull(
                            sun.rise?.let { "↑ ${tf.time(it.time)}" },
                            sun.set?.let { "↓ ${tf.time(it.time)}" },
                        ).joinToString("   ")
                    }
                    Text(summary, color = palette.sun, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.weight(1f))
                if (!vm.isLive) {
                    FilledTonalButton(onClick = vm::backToNow) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Now")
                    }
                }
                IconButton(onClick = onInfo) {
                    Icon(Icons.Outlined.Info, contentDescription = "Sun and moon details")
                }
            }
        }
    }
}
