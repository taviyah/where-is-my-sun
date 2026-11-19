package com.wheresmysun.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheresmysun.SkyViewModel
import com.wheresmysun.astro.Body
import com.wheresmysun.astro.BodyDay
import com.wheresmysun.astro.HorizonEvent
import com.wheresmysun.astro.SkyCalculator
import com.wheresmysun.astro.SkyPoint
import com.wheresmysun.orientation.SkyProjection
import com.wheresmysun.ui.TimeFormat
import com.wheresmysun.ui.drawLabel
import com.wheresmysun.ui.drawMoonGlyph
import com.wheresmysun.ui.drawRuns
import com.wheresmysun.ui.drawSunGlyph
import com.wheresmysun.ui.labelStyle
import com.wheresmysun.ui.rememberTimeFormat
import com.wheresmysun.ui.theme.LocalSkyPalette
import com.wheresmysun.ui.theme.SkyPalette
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A top-down chart of the sky: the edge is the horizon, the centre is straight overhead. Turns with
 * the phone so "up" on screen is the way you're facing (unless north-up is chosen in settings).
 */
@Composable
fun SkyMap(vm: SkyViewModel, modifier: Modifier = Modifier) {
    val day = vm.daySky
    val settings = vm.settings
    val tf = rememberTimeFormat()
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val palette = LocalSkyPalette.current

    val sunSegments = remember(day) { day?.sun?.visibleSegments().orEmpty() }
    val moonSegments = remember(day) { day?.moon?.visibleSegments().orEmpty() }
    val sunMarks = remember(day) { day?.sun?.hourMarks().orEmpty() }
    val moonMarks = remember(day) { day?.moon?.hourMarks().orEmpty() }

    val time = vm.selectedTime
    val location = vm.activeLocation?.point
    val sunNow = remember(time, location) { vm.position(Body.SUN, time) }
    val moonNow = remember(time, location) { vm.position(Body.MOON, time) }
    val phase = remember(time, location) { location?.let { SkyCalculator.moonPhase(it, time.toInstant()) } }
    val timeLabel = if (vm.isLive) null else tf.time(time)

    Canvas(modifier.fillMaxSize()) {
        val rotation = vm.orientation.rotation
        val heading = rotation?.let { SkyProjection.heading(it, vm.northOffsetDeg) }
        val up = if (settings.mapFollowsCompass && heading != null) heading else 0.0

        val radius = min(size.width, size.height) / 2f - 36.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        val chart = Chart(center, radius, up)

        // Sky disc and altitude rings.
        drawCircle(Brush.radialGradient(listOf(palette.mapSky, palette.mapSkyEdge), center, radius), radius, center)
        for (alt in listOf(30, 60)) {
            drawCircle(palette.mapRing, radius * (90 - alt) / 90f, center, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            drawLabel(measurer, "$alt°", Offset(center.x + 4.dp.toPx(), center.y - radius * (90 - alt) / 90f), labelStyle(palette, palette.label.copy(alpha = 0.6f), 10.sp))
        }
        drawCircle(palette.horizon, radius, center, style = Stroke((if (palette.isEink) 2f else 1.5f).dp.toPx()))

        drawCompassRose(palette, chart, measurer)

        // The way the phone faces: a soft wedge about as wide as a camera's view.
        if (heading != null) {
            val wedge = Path().apply {
                moveTo(center.x, center.y)
                val a0 = chart.screenAngle(heading - 30)
                val a1 = chart.screenAngle(heading + 30)
                arcTo(androidx.compose.ui.geometry.Rect(center, radius), Math.toDegrees(a0).toFloat(), Math.toDegrees(a1 - a0).toFloat().let { if (it < 0) it + 360f else it }, false)
                close()
            }
            drawPath(wedge, palette.label.copy(alpha = if (palette.isEink) 0.1f else 0.07f))
            val tip = chart.at(SkyPoint(heading, 0.0))
            drawLine(palette.label.copy(alpha = 0.5f), center, tip, strokeWidth = 1.dp.toPx())
        }

        if (day != null) {
            if (settings.showMoon) {
                for (seg in moonSegments) {
                    drawRuns(palette, seg.map { chart.at(it.position) }, { palette.moonLine })
                }
                if (settings.showHourLabels) drawMarks(palette, chart, moonMarks.map { it.time to it.position }, measurer, tf, palette.moonLine.color, small = true)
                drawEvents(palette, chart, day.moon, measurer, tf, palette.moonLine.color, prefix = if (palette.isEink) "☾" else "")
            }
            for (seg in sunSegments) {
                drawRuns(palette, seg.map { chart.at(it.position) }, { i ->
                    if (settings.highlightGoldenHour && seg[i].position.altitude < 6.0) palette.goldenLine else palette.sunLine
                })
            }
            if (settings.showHourLabels) drawMarks(palette, chart, sunMarks.map { it.time to it.position }, measurer, tf, palette.sunLine.color, small = false)
            drawEvents(palette, chart, day.sun, measurer, tf, palette.sunLine.color, prefix = "")
        }

        val glyph = 12.dp.toPx()
        if (settings.showMoon && moonNow != null && phase != null && (day == null || day.moon.isUp(moonNow))) {
            val c = chart.at(moonNow)
            drawMoonGlyph(palette, c, glyph, phase.illumination, phase.waxing)
            timeLabel?.let { drawLabel(measurer, it, Offset(c.x, c.y + 22.dp.toPx()), labelStyle(palette, bold = true)) }
        }
        if (sunNow != null && (day == null || day.sun.isUp(sunNow))) {
            val c = chart.at(sunNow)
            drawSunGlyph(palette, c, glyph)
            timeLabel?.let { drawLabel(measurer, it, Offset(c.x, c.y + 24.dp.toPx()), labelStyle(palette, bold = true)) }
        }
    }
}

/** Polar mapping: azimuth around the circle (relative to [up]), altitude inwards. */
private class Chart(val center: Offset, val radius: Float, val up: Double) {
    /** Screen angle in radians, 0 = right, clockwise, for a compass azimuth. */
    fun screenAngle(azimuth: Double): Double = Math.toRadians(azimuth - up - 90.0)

    fun at(p: SkyPoint, extraRadius: Float = 0f): Offset {
        val r = radius * (90.0 - p.altitude.coerceIn(-5.0, 90.0)) / 90.0 + extraRadius
        val a = screenAngle(p.azimuth)
        return Offset((center.x + r * cos(a)).toFloat(), (center.y + r * sin(a)).toFloat())
    }
}

private fun DrawScope.drawCompassRose(palette: SkyPalette, chart: Chart, measurer: TextMeasurer) {
    for (az in 0 until 360 step 15) {
        val major = az % 45 == 0
        val inner = chart.at(SkyPoint(az.toDouble(), 0.0), extraRadius = -(if (major) 8 else 4).dp.toPx())
        val outer = chart.at(SkyPoint(az.toDouble(), 0.0))
        drawLine(palette.horizon, inner, outer, strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx())
    }
    for ((az, label) in listOf(0 to "N", 45 to "NE", 90 to "E", 135 to "SE", 180 to "S", 225 to "SW", 270 to "W", 315 to "NW")) {
        val p = chart.at(SkyPoint(az.toDouble(), 0.0), extraRadius = 18.dp.toPx())
        val color = if (az == 0) palette.north else palette.label
        drawLabel(measurer, label, Offset(p.x, p.y - 9.dp.toPx()), labelStyle(palette, color, if (label.length == 1) 16.sp else 12.sp, bold = label.length == 1))
    }
}

private fun DrawScope.drawMarks(
    palette: SkyPalette,
    chart: Chart,
    marks: List<Pair<java.time.ZonedDateTime, SkyPoint>>,
    measurer: TextMeasurer,
    tf: TimeFormat,
    color: Color,
    small: Boolean,
) {
    val r = if (small) 2.5.dp.toPx() else 3.5.dp.toPx()
    for ((t, p) in marks) {
        val c = chart.at(p)
        drawCircle(palette.halo, r + 1.5f, c)
        drawCircle(color, r, c)
        drawLabel(measurer, tf.hour(t), Offset(c.x, c.y - r - 2.dp.toPx()), labelStyle(palette, if (palette.isEink) palette.label else color, if (small) 10.sp else 12.sp, bold = !small), above = true)
    }
}

/** Rise and set times written just outside the horizon where they happen. */
private fun DrawScope.drawEvents(palette: SkyPalette, chart: Chart, day: BodyDay, measurer: TextMeasurer, tf: TimeFormat, color: Color, prefix: String) {
    fun draw(e: HorizonEvent?, arrow: String) {
        e ?: return
        val p = chart.at(SkyPoint(e.azimuth, 0.0), extraRadius = (-14).dp.toPx())
        drawLabel(measurer, "$prefix$arrow${tf.time(e.time)}", Offset(p.x, p.y - 8.dp.toPx()), labelStyle(palette, color, 11.sp, bold = true))
    }
    draw(day.rise, "↑")
    draw(day.set, "↓")
}
