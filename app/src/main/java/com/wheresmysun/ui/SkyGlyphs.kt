package com.wheresmysun.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.wheresmysun.ui.theme.LineStyle
import com.wheresmysun.ui.theme.SkyPalette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Drawing helpers shared by the camera overlay and the sky map. */

private fun belowHorizonStroke(radius: Float) =
    Stroke(radius * 0.25f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(radius * 0.4f, radius * 0.3f)))

fun DrawScope.drawSunGlyph(palette: SkyPalette, center: Offset, radius: Float, belowHorizon: Boolean = false) {
    if (belowHorizon) {
        drawCircle(palette.halo, radius * 1.1f, center, style = Stroke(radius * 0.45f))
        drawCircle(palette.sun, radius, center, style = belowHorizonStroke(radius))
        return
    }
    for (i in 0 until 8) {
        val a = i * Math.PI / 4
        val inner = radius * 1.25f
        val outer = radius * 1.7f
        val from = Offset(center.x + inner * cos(a).toFloat(), center.y + inner * sin(a).toFloat())
        val to = Offset(center.x + outer * cos(a).toFloat(), center.y + outer * sin(a).toFloat())
        drawLine(palette.halo, from, to, strokeWidth = radius * 0.45f, cap = StrokeCap.Round)
        drawLine(palette.sun, from, to, strokeWidth = radius * 0.22f, cap = StrokeCap.Round)
    }
    drawCircle(palette.halo, radius * 1.12f, center)
    drawCircle(palette.sun, radius, center)
}

/**
 * Moon disc with its phase. [illumination] 0..1; the lit side is drawn on the right while waxing
 * and on the left while waning (as seen from the northern hemisphere, which is close enough for a
 * small icon).
 */
fun DrawScope.drawMoonGlyph(
    palette: SkyPalette,
    center: Offset,
    radius: Float,
    illumination: Double,
    waxing: Boolean,
    belowHorizon: Boolean = false,
) {
    val edge = palette.moonEdge ?: palette.moon
    if (belowHorizon) {
        drawCircle(palette.halo, radius * 1.1f, center, style = Stroke(radius * 0.45f))
        drawCircle(edge, radius, center, style = belowHorizonStroke(radius))
        return
    }
    drawCircle(palette.halo, radius * 1.15f, center)
    drawCircle(palette.moonShadow, radius, center)
    val k = illumination.coerceIn(0.0, 1.0)
    if (k >= 0.02) {
        val disc = Rect(center, radius)
        val e = (radius * abs(1 - 2 * k)).toFloat()
        val ellipse = Rect(center.x - e, center.y - radius, center.x + e, center.y + radius)
        val gibbous = k > 0.5
        val path = Path().apply {
            moveTo(center.x, center.y - radius)
            if (waxing) {
                arcTo(disc, -90f, 180f, false) // right limb, top to bottom
                arcTo(ellipse, 90f, if (gibbous) 180f else -180f, false)
            } else {
                arcTo(disc, -90f, -180f, false) // left limb
                arcTo(ellipse, 90f, if (gibbous) -180f else 180f, false)
            }
            close()
        }
        drawPath(path, palette.moon)
    }
    palette.moonEdge?.let { drawCircle(it, radius, center, style = Stroke(radius * 0.14f)) }
}

/** A filled triangle pointing along [angle] (radians, screen convention), tip at [tip]. */
fun DrawScope.drawArrowHead(palette: SkyPalette, tip: Offset, angle: Double, size: Float, color: Color) {
    rotate(Math.toDegrees(angle).toFloat(), pivot = tip) {
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - size, tip.y - size * 0.6f)
            lineTo(tip.x - size * 0.7f, tip.y)
            lineTo(tip.x - size, tip.y + size * 0.6f)
            close()
        }
        drawPath(path, palette.halo, style = Stroke(size * 0.25f, join = StrokeJoin.Round))
        drawPath(path, color)
    }
}

fun labelStyle(palette: SkyPalette, color: Color = palette.label, size: TextUnit = 13.sp, bold: Boolean = false) = TextStyle(
    color = color,
    fontSize = size,
    // E-ink has no colour or soft shading to lean on, so labels are always a bit heavier there.
    fontWeight = if (bold || palette.isEink) FontWeight.SemiBold else FontWeight.Normal,
    shadow = Shadow(palette.labelHalo, blurRadius = if (palette.isEink) 8f else 6f),
)

/** Draws [text] centred horizontally on [anchor.x], with its top (or bottom when [above]) at [anchor.y]. */
fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, anchor: Offset, style: TextStyle, above: Boolean = false) {
    val layout = measurer.measure(text, style)
    val x = anchor.x - layout.size.width / 2f
    val y = if (above) anchor.y - layout.size.height else anchor.y
    drawText(layout, topLeft = Offset(x, y))
}

/**
 * Draws a line through [points] as continuous runs. [styleAt] picks the style per point and a new
 * run starts whenever it changes; null breaks the line (point not drawable).
 */
fun DrawScope.drawRuns(
    palette: SkyPalette,
    points: List<Offset?>,
    styleAt: (Int) -> LineStyle?,
    outline: Boolean = true,
) {
    var path: Path? = null
    var runStyle: LineStyle? = null
    fun flush() {
        val p = path ?: return
        val s = runStyle ?: return
        val width = s.width(this)
        val effect = s.pathEffect(this)
        val cap = if (s.dashDp.isNotEmpty() && s.dashDp[0] < 2f) StrokeCap.Round else StrokeCap.Butt
        // The halo is drawn solid, so dashed lines read as a dashed line on a band, not as specks.
        if (outline) drawPath(p, palette.halo, style = Stroke(width * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, s.color, style = Stroke(width, cap = if (effect == null) StrokeCap.Round else cap, join = StrokeJoin.Round, pathEffect = effect))
        path = null
    }
    var last: Offset? = null
    for (i in points.indices) {
        val pt = points[i]
        val style = if (pt == null) null else styleAt(i)
        if (pt == null || style == null) {
            flush(); last = null; runStyle = null
            continue
        }
        if (path == null || style != runStyle) {
            flush()
            path = Path().apply { moveTo((last ?: pt).x, (last ?: pt).y) }
            runStyle = style
        }
        path!!.lineTo(pt.x, pt.y)
        last = pt
    }
    flush()
}
