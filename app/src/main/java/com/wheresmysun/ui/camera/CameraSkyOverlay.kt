package com.wheresmysun.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wheresmysun.SkyViewModel
import com.wheresmysun.astro.Body
import com.wheresmysun.astro.PathPoint
import com.wheresmysun.astro.PathSample
import com.wheresmysun.astro.SkyCalculator
import com.wheresmysun.astro.SkyPoint
import com.wheresmysun.orientation.CameraOptics
import com.wheresmysun.orientation.SkyProjection
import com.wheresmysun.ui.TimeFormat
import com.wheresmysun.ui.drawArrowHead
import com.wheresmysun.ui.drawLabel
import com.wheresmysun.ui.drawMoonGlyph
import com.wheresmysun.ui.drawRuns
import com.wheresmysun.ui.drawSunGlyph
import com.wheresmysun.ui.labelStyle
import com.wheresmysun.ui.rememberTimeFormat
import com.wheresmysun.ui.theme.LineStyle
import com.wheresmysun.ui.theme.LocalSkyPalette
import com.wheresmysun.ui.theme.SkyPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val CompassLabels = listOf(0 to "N", 45 to "NE", 90 to "E", 135 to "SE", 180 to "S", 225 to "SW", 270 to "W", 315 to "NW")

/** Points this close to 90° off the camera axis are skipped: they'd land thousands of pixels off screen. */
private const val MIN_FORWARD = 0.03

/**
 * Everything drawn over the camera: horizon, compass marks, sun and moon paths with hour marks,
 * the sun/moon at the selected time, and arrows to whatever is off screen.
 *
 * [safeArea] is the part of the screen not covered by controls, used to keep arrows visible.
 */
@Composable
fun CameraSkyOverlay(
    vm: SkyViewModel,
    optics: CameraOptics,
    safeArea: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val day = vm.daySky
    val settings = vm.settings
    val tf = rememberTimeFormat()
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val layoutDirection = LocalLayoutDirection.current
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
    val alignTarget = if (vm.aligning) vm.alignmentTarget() else null
    val timeLabel = if (vm.isLive) null else tf.time(time)

    val lastProjection = remember { arrayOfNulls<SkyProjection>(1) }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(vm.aligning) {
                if (vm.aligning) {
                    detectTapGestures { tap ->
                        lastProjection[0]?.let { vm.alignTo(it.unproject(tap.x, tap.y)) }
                    }
                }
            },
    ) {
        val rotation = vm.orientation.rotation ?: return@Canvas
        val proj = SkyProjection(rotation, vm.northOffsetDeg, optics.focalPx(size.width, size.height), size.width, size.height)
        lastProjection[0] = proj

        drawHorizon(palette, proj, measurer)

        if (day != null) {
            if (settings.showMoon) {
                drawBodyPath(palette, proj, moonSegments) { _ -> palette.moonLine }
                if (settings.showHourLabels) drawHourMarks(palette, proj, moonMarks, measurer, tf, palette.moonLine.color, small = true)
            }
            drawBodyPath(palette, proj, sunSegments) { p ->
                if (settings.highlightGoldenHour && p.position.altitude < 6.0) palette.goldenLine else palette.sunLine
            }
            if (settings.showHourLabels) drawHourMarks(palette, proj, sunMarks, measurer, tf, palette.sunLine.color, small = false)
        }

        val glyph = 13.dp.toPx()
        if (settings.showMoon && moonNow != null && phase != null) {
            val below = day != null && !day.moon.isUp(moonNow)
            drawMarker(palette, proj, moonNow, measurer, timeLabel.takeIf { !below }) { c ->
                drawMoonGlyph(palette, c, glyph, phase.illumination, phase.waxing, belowHorizon = below)
            }
        }
        if (sunNow != null) {
            val below = day != null && !day.sun.isUp(sunNow)
            drawMarker(palette, proj, sunNow, measurer, timeLabel.takeIf { !below }) { c ->
                drawSunGlyph(palette, c, glyph, belowHorizon = below)
            }
        }

        if (alignTarget != null) {
            // Show where the app thinks the body is right now, so the user sees the gap to close.
            val s = proj.project(alignTarget.second)
            if (s.inFront) {
                drawCircle(palette.label, 34.dp.toPx(), Offset(s.x, s.y), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
            }
        }

        // Arrows to off-screen targets: where the body really is at the selected time, even
        // below the horizon (then drawn hollow, like its marker).
        val left = safeArea.calculateLeftPadding(layoutDirection).toPx()
        val right = safeArea.calculateRightPadding(layoutDirection).toPx()
        val top = safeArea.calculateTopPadding().toPx()
        val bottom = safeArea.calculateBottomPadding().toPx()
        val arrowArea = androidx.compose.ui.geometry.Rect(left + 40.dp.toPx(), top + 40.dp.toPx(), size.width - right - 40.dp.toPx(), size.height - bottom - 40.dp.toPx())
        if (day != null) {
            val sunArrow = sunNow
                ?.takeIf { !proj.isOnScreen(it, margin = -24f) }
                ?.let { edgeArrow(proj.offscreenDirection(it), arrowArea) }
            val moonArrow = if (settings.showMoon && phase != null) {
                moonNow
                    ?.takeIf { !proj.isOnScreen(it, margin = -24f) }
                    ?.let { edgeArrow(proj.offscreenDirection(it), arrowArea) }
            } else null
            val sunBelow = sunNow != null && !day.sun.isUp(sunNow)
            val moonBelow = moonNow != null && !day.moon.isUp(moonNow)

            // When both point the same way, slide the moon's arrow along the edge so they don't
            // sit on top of each other.
            val spacing = 64.dp.toPx()
            val moonPlaced = if (sunArrow != null && moonArrow != null && (sunArrow.tip - moonArrow.tip).getDistance() < spacing) {
                val along = Offset(-sin(moonArrow.angle).toFloat(), cos(moonArrow.angle).toFloat())
                val nudged = moonArrow.tip + along * spacing
                val clamped = Offset(nudged.x.coerceIn(arrowArea.left, arrowArea.right), nudged.y.coerceIn(arrowArea.top, arrowArea.bottom))
                val final = if ((clamped - sunArrow.tip).getDistance() < spacing * 0.9f) moonArrow.tip - along * spacing else clamped
                moonArrow.copy(tip = Offset(final.x.coerceIn(arrowArea.left, arrowArea.right), final.y.coerceIn(arrowArea.top, arrowArea.bottom)))
            } else moonArrow

            moonPlaced?.let { a ->
                drawEdgeArrow(palette, a, palette.moonLine.color) { c -> drawMoonGlyph(palette, c, 9.dp.toPx(), phase!!.illumination, phase.waxing, belowHorizon = moonBelow) }
            }
            sunArrow?.let { a -> drawEdgeArrow(palette, a, palette.sun) { c -> drawSunGlyph(palette, c, 9.dp.toPx(), belowHorizon = sunBelow) } }
        }
    }
}

private fun DrawScope.drawHorizon(palette: SkyPalette, proj: SkyProjection, measurer: TextMeasurer) {
    val points = (0..360 step 2).map { az ->
        val p = proj.project(SkyPoint(az.toDouble(), 0.0))
        if (forward(proj, SkyPoint(az.toDouble(), 0.0)) > MIN_FORWARD) Offset(p.x, p.y) else null
    }
    val horizon = LineStyle(palette.horizon, if (palette.isEink) 2f else 1.5f)
    drawRuns(palette, points, { horizon })

    val tick = 6.dp.toPx()
    for (az in 0 until 360 step 15) {
        val sp = SkyPoint(az.toDouble(), 0.0)
        if (forward(proj, sp) <= MIN_FORWARD) continue
        val p = proj.project(sp)
        if (p.x < -50 || p.x > size.width + 50 || p.y < -50 || p.y > size.height + 50) continue
        val major = az % 45 == 0
        val len = if (major) tick * 1.6f else tick
        drawLine(palette.horizon, Offset(p.x, p.y - len), Offset(p.x, p.y + len), strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(), cap = StrokeCap.Round)
        val label = CompassLabels.firstOrNull { it.first == az }?.second
        if (label != null) {
            drawLabel(measurer, label, Offset(p.x, p.y + len + 2.dp.toPx()), labelStyle(palette, size = if (label.length == 1) 16.sp else 13.sp, bold = label.length == 1))
        } else if (az % 30 == 0) {
            drawLabel(measurer, "$az°", Offset(p.x, p.y + len + 2.dp.toPx()), labelStyle(palette, color = palette.label.copy(alpha = 0.7f), size = 11.sp))
        }
    }
}

private fun forward(proj: SkyProjection, p: SkyPoint): Double = -proj.worldToDevice(proj.toWorld(p)).z

private fun DrawScope.drawBodyPath(
    palette: SkyPalette,
    proj: SkyProjection,
    segments: List<List<PathPoint>>,
    styleOf: (PathPoint) -> LineStyle,
) {
    for (segment in segments) {
        val pts = segment.map { pp ->
            if (forward(proj, pp.position) > MIN_FORWARD) proj.project(pp.position).let { Offset(it.x, it.y) } else null
        }
        drawRuns(palette, pts, { i -> styleOf(segment[i]) })
    }
}

private fun DrawScope.drawHourMarks(
    palette: SkyPalette,
    proj: SkyProjection,
    marks: List<PathSample>,
    measurer: TextMeasurer,
    tf: TimeFormat,
    color: Color,
    small: Boolean,
) {
    val r = if (small) 3.dp.toPx() else 4.dp.toPx()
    val style = labelStyle(palette, color = if (palette.isEink) palette.label else color, size = if (small) 11.sp else 13.sp, bold = !small)
    for (m in marks) {
        if (forward(proj, m.position) <= MIN_FORWARD) continue
        val s = proj.project(m.position)
        if (s.x < -40 || s.x > size.width + 40 || s.y < -40 || s.y > size.height + 40) continue
        val c = Offset(s.x, s.y)
        drawCircle(palette.halo, r + 1.5f, c)
        drawCircle(color, r, c)
        drawLabel(measurer, tf.hour(m.time), Offset(c.x, c.y - r - 3.dp.toPx()), style, above = true)
    }
}

private fun DrawScope.drawMarker(
    palette: SkyPalette,
    proj: SkyProjection,
    position: SkyPoint,
    measurer: TextMeasurer,
    label: String?,
    glyph: DrawScope.(Offset) -> Unit,
) {
    if (forward(proj, position) <= MIN_FORWARD) return
    val s = proj.project(position)
    if (s.x < -100 || s.x > size.width + 100 || s.y < -100 || s.y > size.height + 100) return
    val c = Offset(s.x, s.y)
    glyph(c)
    if (label != null) {
        drawLabel(measurer, label, Offset(c.x, c.y + 26.dp.toPx()), labelStyle(palette, size = 13.sp, bold = true))
    }
}

private data class EdgeArrow(val tip: Offset, val angle: Double)

/** Where on the edge of [area] an arrow pointing along [angle] from its centre lands. */
private fun edgeArrow(angle: Double, area: androidx.compose.ui.geometry.Rect): EdgeArrow {
    val center = area.center
    val dx = cos(angle)
    val dy = sin(angle)
    val hw = area.width / 2.0
    val hh = area.height / 2.0
    val t = min(if (abs(dx) > 1e-6) hw / abs(dx) else Double.MAX_VALUE, if (abs(dy) > 1e-6) hh / abs(dy) else Double.MAX_VALUE)
    return EdgeArrow(Offset((center.x + dx * t).toFloat(), (center.y + dy * t).toFloat()), angle)
}

private fun DrawScope.drawEdgeArrow(palette: SkyPalette, arrow: EdgeArrow, color: Color, glyph: DrawScope.(Offset) -> Unit) {
    val size = 22.dp.toPx()
    drawArrowHead(palette, arrow.tip, arrow.angle, size, color)
    val back = size * 1.9
    glyph(Offset((arrow.tip.x - cos(arrow.angle) * back).toFloat(), (arrow.tip.y - sin(arrow.angle) * back).toFloat()))
}
