package com.wheresmysun.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** How a path is drawn. Dash lengths are in dp so they look the same on every screen. */
@Immutable
data class LineStyle(
    val color: Color,
    val widthDp: Float,
    /** On/off dash lengths in dp, or empty for a solid line. */
    val dashDp: List<Float> = emptyList(),
) {
    fun width(density: Density): Float = with(density) { widthDp.dp.toPx() }

    fun pathEffect(density: Density): PathEffect? =
        if (dashDp.isEmpty()) null
        else with(density) { PathEffect.dashPathEffect(dashDp.map { it.dp.toPx() }.toFloatArray()) }
}

/**
 * Everything the sky drawings need to know about colour. Two variants: the normal colourful one,
 * and one for e-ink screens, which show greys only, ghost on large dark areas and redraw slowly.
 * The e-ink palette tells the sun and moon apart by shape and line style instead of colour.
 */
@Immutable
data class SkyPalette(
    val isEink: Boolean,
    val sun: Color,
    val moon: Color,
    /** The dark part of the moon icon. */
    val moonShadow: Color,
    /** Edge of the moon icon (e-ink only needs one, to separate a white disc from white paper). */
    val moonEdge: Color?,
    val horizon: Color,
    /** Halo drawn under lines and icons so they stand out against any camera image. */
    val halo: Color,
    val label: Color,
    val labelHalo: Color,
    val sunLine: LineStyle,
    val goldenLine: LineStyle,
    val moonLine: LineStyle,
    val mapSky: Color,
    val mapSkyEdge: Color,
    val mapRing: Color,
    val north: Color,
    val warning: Color,
    /** Panels over the camera: see-through on a normal screen, solid with a border on e-ink. */
    val panelAlpha: Float,
    val panelBorder: BorderStroke?,
)

val NormalPalette = SkyPalette(
    isEink = false,
    sun = Color(0xFFFFC94A),
    moon = Color(0xFFD5DEEC),
    moonShadow = Color(0xFF39445A),
    moonEdge = null,
    horizon = Color(0xCCFFFFFF),
    halo = Color(0x99000000),
    label = Color.White,
    labelHalo = Color.Black,
    sunLine = LineStyle(Color(0xFFFFC94A), 4f),
    goldenLine = LineStyle(Color(0xFFFF8A3D), 4f),
    moonLine = LineStyle(Color(0xFFD5DEEC), 2.5f, listOf(5f, 4f)),
    mapSky = Color(0xFF14263F),
    mapSkyEdge = Color(0xFF0B1626),
    mapRing = Color(0x2EFFFFFF),
    north = Color(0xFFFF6B6B),
    warning = Color(0xFFFFB74D),
    panelAlpha = 0.88f,
    panelBorder = null,
)

val EinkPalette = SkyPalette(
    isEink = true,
    sun = Color.Black,
    moon = Color.White,
    moonShadow = Color(0xFF555555),
    moonEdge = Color.Black,
    horizon = Color.Black,
    halo = Color(0xE6FFFFFF),
    label = Color.Black,
    labelHalo = Color.White,
    // Solid = sun, long dashes = sun in golden hour, dots = moon.
    sunLine = LineStyle(Color.Black, 4.5f),
    goldenLine = LineStyle(Color.Black, 4.5f, listOf(9f, 4f)),
    moonLine = LineStyle(Color(0xFF444444), 3f, listOf(1.5f, 4.5f)),
    mapSky = Color.White,
    mapSkyEdge = Color.White,
    mapRing = Color(0xFF999999),
    north = Color.Black,
    warning = Color.Black,
    panelAlpha = 1f,
    panelBorder = BorderStroke(1.5.dp, Color.Black),
)

val LocalSkyPalette = staticCompositionLocalOf { NormalPalette }

private val darkScheme = darkColorScheme(
    primary = Color(0xFFFFC94A),
    onPrimary = Color(0xFF3A2A00),
    primaryContainer = Color(0xFF574200),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFFB8C7E0),
    onSecondary = Color(0xFF223147),
    secondaryContainer = Color(0xFF39475E),
    onSecondaryContainer = Color(0xFFD5E3FD),
    background = Color(0xFF0B1626),
    onBackground = Color(0xFFE2E6EE),
    surface = Color(0xFF111D2E),
    onSurface = Color(0xFFE2E6EE),
    surfaceVariant = Color(0xFF253247),
    onSurfaceVariant = Color(0xFFC0C8D6),
    surfaceContainer = Color(0xFF16233A),
    surfaceContainerHigh = Color(0xFF1C2A42),
    surfaceContainerHighest = Color(0xFF23324C),
)

/** Pure black on white, with mid greys only where something must look "inactive". */
private val einkScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDDDDD),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF333333),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDDDDD),
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF333333),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFEEEEEE),
    outline = Color.Black,
    outlineVariant = Color(0xFF888888),
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
)

@Composable
fun WheresMySunTheme(eink: Boolean = false, content: @Composable () -> Unit) {
    val scheme = if (eink) einkScheme else darkScheme
    MaterialTheme(colorScheme = scheme) {
        // The panels over the camera use see-through surface colours, which Material can't map
        // to a content colour, so it falls back to LocalContentColor. Make that fallback readable.
        CompositionLocalProvider(
            LocalContentColor provides scheme.onSurface,
            LocalSkyPalette provides if (eink) EinkPalette else NormalPalette,
            content = content,
        )
    }
}
