package com.wheresmysun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wheresmysun.SkyViewModel
import com.wheresmysun.astro.Body
import com.wheresmysun.astro.BodyDay
import com.wheresmysun.astro.SkyCalculator
import com.wheresmysun.orientation.CompassAccuracy
import com.wheresmysun.ui.theme.LocalSkyPalette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Contents of the details sheet: times, directions, moon phase, and how the compass is doing. */
@Composable
fun SkyInfo(vm: SkyViewModel) {
    val tf = rememberTimeFormat()
    val day = vm.daySky
    val time = vm.selectedTime
    val loc = vm.activeLocation
    val palette = LocalSkyPalette.current

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            if (vm.dayOffset == 0) "Today, ${tf.date(time)}" else "Tomorrow, ${tf.date(time)}",
            style = MaterialTheme.typography.titleLarge,
        )
        if (day == null || loc == null) {
            Text("Waiting for your location…", Modifier.padding(vertical = 16.dp))
            return@Column
        }

        Section("Sun", palette.sun)
        BodyRows(day.sun, tf)
        day.solarNoon?.let { noon ->
            val alt = SkyCalculator.sunPosition(loc.point, noon.toInstant()).altitude
            InfoRow("Highest", "${tf.time(noon)} · ${formatAltitude(alt)} up")
        }
        day.dayLength?.let { InfoRow("Daylight", formatDuration(it)) }
        if (day.sun.rise != null && day.morningGoldenHourEnd != null) {
            InfoRow("Golden hour", "${tf.time(day.sun.rise.time)} – ${tf.time(day.morningGoldenHourEnd)}")
        }
        if (day.sun.set != null && day.eveningGoldenHourStart != null) {
            InfoRow(if (day.sun.rise != null) "" else "Golden hour", "${tf.time(day.eveningGoldenHourStart)} – ${tf.time(day.sun.set.time)}")
        }
        vm.position(Body.SUN)?.let { p ->
            InfoRow("At ${tf.time(time)}", if (p.altitude < day.sun.horizonAltitude) "below the horizon" else "${formatDirection(p.azimuth)} · ${formatAltitude(p.altitude)} up")
        }

        Section("Moon", palette.moonLine.color)
        BodyRows(day.moon, tf)
        val phase = SkyCalculator.moonPhase(loc.point, time.toInstant())
        InfoRow("Phase", "${phase.name.label()} · ${(phase.illumination * 100).roundToInt()}% lit")
        vm.position(Body.MOON)?.let { p ->
            InfoRow("At ${tf.time(time)}", if (p.altitude < day.moon.horizonAltitude) "below the horizon" else "${formatDirection(p.azimuth)} · ${formatAltitude(p.altitude)} up")
        }

        Section("Location & compass", MaterialTheme.colorScheme.onSurface)
        InfoRow(loc.label, String.format(Locale.US, "%.4f, %.4f", loc.point.latitude, loc.point.longitude))
        if (loc.isGps) {
            vm.locationTracker.location?.takeIf { it.hasAccuracy() }?.let { InfoRow("GPS accuracy", "±${it.accuracy.roundToInt()} m") }
        }
        val decl = vm.declinationDeg
        InfoRow("Magnetic north", "${abs(decl).roundToOneDecimal()}° ${if (decl >= 0) "east" else "west"} of true north (corrected)")
        InfoRow("Compass", when (vm.orientation.accuracy) {
            CompassAccuracy.HIGH -> "Good"
            CompassAccuracy.MEDIUM -> "OK"
            CompassAccuracy.LOW -> "Poor: calibrate with a figure-8"
            CompassAccuracy.UNRELIABLE -> "Unreliable: calibrate with a figure-8"
            CompassAccuracy.UNKNOWN -> if (vm.orientation.isAvailable) "Unknown" else "No compass on this phone"
        })
        val measured = vm.orientation.measuredField
        val expected = vm.orientation.expectedField
        if (measured != null && expected != null) {
            InfoRow(
                "Magnetic field",
                "${measured.strengthUt.roundToInt()} µT, dip ${measured.inclinationDeg.roundToInt()}° " +
                    "(Earth here: ${expected.strengthUt.roundToInt()} µT, ${expected.inclinationDeg.roundToInt()}°)" +
                    if (vm.orientation.magneticInterference) " · interference" else " · looks clean",
            )
        }
        if (vm.alignmentDeg != 0.0) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                InfoRow("Manual alignment", "${vm.alignmentDeg.roundToOneDecimal()}°", Modifier.weight(1f))
                TextButton(onClick = vm::resetAlignment) { Text("Reset") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Times are in your phone's time zone. Paths assume a flat horizon; buildings and hills " +
                "will hide the sun earlier than the times shown.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BodyRows(day: BodyDay, tf: TimeFormat) {
    val name = if (day.body == Body.SUN) "Sun" else "Moon"
    when {
        day.alwaysUp -> InfoRow("Rise / set", "$name stays up all day")
        day.alwaysDown -> InfoRow("Rise / set", "$name stays below the horizon")
        else -> {
            InfoRow("Rises", day.rise?.let { "${tf.time(it.time)} · ${formatDirection(it.azimuth)}" } ?: "doesn't rise this day")
            InfoRow("Sets", day.set?.let { "${tf.time(it.time)} · ${formatDirection(it.azimuth)}" } ?: "doesn't set this day")
        }
    }
}

@Composable
private fun Section(title: String, color: Color) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.SemiBold)
    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = color.copy(alpha = 0.3f))
}

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(0.38f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(0.62f), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Double.roundToOneDecimal(): String = String.format(Locale.getDefault(), "%.1f", this)
