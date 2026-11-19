package com.wheresmysun.orientation

import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * What we know about the back camera's optics, used to turn angles into pixels.
 *
 * @param focalLengthMm lens focal length
 * @param sensorLongMm physical size of the sensor's long side
 * @param previewAspect long side / short side of the preview stream (4:3 = 1.333)
 */
data class CameraOptics(
    val focalLengthMm: Double,
    val sensorLongMm: Double,
    val previewAspect: Double,
) {
    /**
     * Focal length in screen pixels for a preview that fills a [viewWidth] x [viewHeight] view
     * with centre-crop scaling (PreviewView's FILL_CENTER).
     *
     * Cropping a stream to a different aspect (16:9 from a 4:3 sensor) cuts the short side, so the
     * long side always spans the full sensor. That makes the long side the safe one to scale from.
     */
    fun focalPx(viewWidth: Float, viewHeight: Float): Double {
        val viewLong = max(viewWidth, viewHeight).toDouble()
        val viewShort = min(viewWidth, viewHeight).toDouble()
        val displayedLong = max(viewLong, viewShort * previewAspect)
        return focalLengthMm * displayedLong / sensorLongMm
    }

    companion object {
        /** Typical phone main camera: about 66° across the long side, 4:3 stream. */
        val FALLBACK = fromLongSideFov(66.0, 4.0 / 3.0)

        fun fromLongSideFov(fovDeg: Double, aspect: Double): CameraOptics {
            // Any focal length works; pick 1 mm and derive the matching sensor size.
            val sensorLong = 2.0 * tan(Math.toRadians(fovDeg / 2.0))
            return CameraOptics(1.0, sensorLong, aspect)
        }
    }
}
