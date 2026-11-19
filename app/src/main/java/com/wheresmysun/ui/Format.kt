package com.wheresmysun.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.wheresmysun.astro.MoonPhaseName
import com.wheresmysun.astro.compassName
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Time formatting that follows the phone's 12/24-hour setting. */
class TimeFormat(context: Context) {
    val is24h: Boolean = DateFormat.is24HourFormat(context)
    private val time = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault())
    private val hour = DateTimeFormatter.ofPattern(if (is24h) "HH" else "ha", Locale.getDefault())
    private val date = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

    fun time(t: ZonedDateTime): String = time.format(t)
    fun hour(t: ZonedDateTime): String = hour.format(t).lowercase(Locale.getDefault())
    fun date(t: ZonedDateTime): String = date.format(t)
}

@Composable
fun rememberTimeFormat(): TimeFormat {
    val context = LocalContext.current
    return remember(context) { TimeFormat(context) }
}

fun formatDirection(azimuth: Double): String = "${compassName(azimuth)} ${azimuth.roundToInt()}°"

fun formatAltitude(altitude: Double): String = "${altitude.roundToInt()}°"

fun formatDuration(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutes() % 60
    return "${h}h ${m}m"
}

fun MoonPhaseName.label(): String = when (this) {
    MoonPhaseName.NEW_MOON -> "New moon"
    MoonPhaseName.WAXING_CRESCENT -> "Waxing crescent"
    MoonPhaseName.FIRST_QUARTER -> "First quarter"
    MoonPhaseName.WAXING_GIBBOUS -> "Waxing gibbous"
    MoonPhaseName.FULL_MOON -> "Full moon"
    MoonPhaseName.WANING_GIBBOUS -> "Waning gibbous"
    MoonPhaseName.LAST_QUARTER -> "Last quarter"
    MoonPhaseName.WANING_CRESCENT -> "Waning crescent"
}
