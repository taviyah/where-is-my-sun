package com.wheresmysun.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.SystemClock
import android.util.Log
import com.wheresmysun.BuildConfig
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

enum class CompassAccuracy { UNKNOWN, UNRELIABLE, LOW, MEDIUM, HIGH }

/**
 * Tracks which way the phone points, as a device-to-world rotation matrix (see [SkyProjection]).
 *
 * Uses the fused rotation-vector sensor (gyro + accelerometer + compass) when the phone has one,
 * which is steady and quick to follow. Phones without it fall back to accelerometer + compass,
 * low-pass filtered, which is jumpier but works.
 */
class OrientationTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val isAvailable: Boolean = rotationSensor != null || (accelSensor != null && magSensor != null)

    /** Latest smoothed rotation matrix, or null until the first reading. Read it in draw code so
     *  only the drawing (not the whole screen) refreshes at sensor rate. */
    var rotation by mutableStateOf<FloatArray?>(null)
        private set

    var accuracy by mutableStateOf(CompassAccuracy.UNKNOWN)
        private set

    /** The rotation sensor's own estimate of its heading error, when it provides one. */
    var headingErrorDeg by mutableStateOf<Float?>(null)
        private set

    /** Set from the phone's location; enables the interference check. */
    var expectedField: ExpectedField? = null

    /** Smoothed magnetometer reading, refreshed about once a second. */
    var measuredField by mutableStateOf<MeasuredField?>(null)
        private set

    /** The magnetic field doesn't look like the Earth's: something nearby is bending the compass. */
    var magneticInterference by mutableStateOf(false)
        private set

    /** Android itself says the compass needs a figure-8 calibration. */
    val needsCalibration: Boolean
        get() = accuracy == CompassAccuracy.UNRELIABLE || accuracy == CompassAccuracy.LOW ||
            (headingErrorDeg ?: 0f) > 25f

    val compassTrouble: Boolean get() = needsCalibration || magneticInterference

    /**
     * For e-ink screens: publish a new orientation only when the phone has really moved, and at
     * most a few times a second. Every published value redraws the overlay, and e-ink panels smear
     * and flash when redrawn at sensor rate.
     */
    var calmUpdates = false

    private var lastPublished: FloatArray? = null
    private var lastPublishMs = 0L

    private var started = false
    private var smoothQ: FloatArray? = null // x, y, z, w
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private val monitor = MagneticMonitor()
    private var lastFieldPublishNs = 0L

    /** Latest unsmoothed orientation and how fast it is changing, for the interference check: it
     *  needs the tilt at the same moment as the magnetometer reading, not a smoothed one. */
    private var rawRotation: FloatArray? = null
    private var motionRef: FloatArray? = null
    private var motionRefNs = 0L
    private var angularSpeedDegPerS = 0.0
    private var haveGravity = false
    private var haveMag = false

    fun start() {
        if (started || sensorManager == null) return
        started = true
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            // Registered for its accuracy callbacks (figure-8 calibration needed) and for the
            // interference check, which compares its readings with the Earth's expected field.
            magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        } else {
            accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            magSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        sensorManager?.unregisterListener(this)
        smoothQ = null
        haveGravity = false
        haveMag = false
        rawRotation = null
        motionRef = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> onRotationVector(event.values, event.timestamp)
            Sensor.TYPE_ACCELEROMETER -> if (rotationSensor == null) {
                lowPass(event.values, gravity, if (haveGravity) 0.15f else 1f)
                haveGravity = true
                updateFromAccelMag(event.timestamp)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                if (rotationSensor == null) {
                    lowPass(event.values, geomagnetic, if (haveMag) 0.15f else 1f)
                    haveMag = true
                    updateFromAccelMag(event.timestamp)
                }
                checkField(event.values, event.timestamp)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type != Sensor.TYPE_MAGNETIC_FIELD && sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        this.accuracy = when (accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassAccuracy.UNRELIABLE
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
            else -> CompassAccuracy.UNKNOWN
        }
    }

    private fun onRotationVector(values: FloatArray, timestampNs: Long) {
        val raw = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(raw, values)
        trackMotion(raw, timestampNs)

        val wxyz = FloatArray(4)
        SensorManager.getQuaternionFromVector(wxyz, values)
        val q = floatArrayOf(wxyz[1], wxyz[2], wxyz[3], wxyz[0])
        val prev = smoothQ
        smoothQ = if (prev == null) q else smooth(prev, q)

        val r = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(r, smoothQ)
        publish(r)

        if (values.size > 4 && values[4] >= 0f) {
            headingErrorDeg = Math.toDegrees(values[4].toDouble()).toFloat()
        }
    }

    /**
     * Normalised lerp between quaternions. Heavier smoothing while the phone is nearly still (to
     * hide sensor jitter), lighter while it is turning (so the overlay doesn't lag behind the
     * camera image).
     */
    private fun smooth(prev: FloatArray, next: FloatArray): FloatArray {
        var dot = prev[0] * next[0] + prev[1] * next[1] + prev[2] * next[2] + prev[3] * next[3]
        val n = next.copyOf()
        if (dot < 0f) { // q and -q are the same rotation; take the short way round
            for (i in 0..3) n[i] = -n[i]
            dot = -dot
        }
        // dot = cos(half the angle between them). 0.9997 ≈ 2.8°.
        val alpha = if (dot > 0.9997f) 0.12f else 0.5f
        val out = FloatArray(4) { prev[it] + alpha * (n[it] - prev[it]) }
        val len = sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2] + out[3] * out[3])
        for (i in 0..3) out[i] /= len
        return out
    }

    /**
     * Measures turning speed over ~1/4 s windows. Between consecutive 50 Hz samples, sensor jitter
     * of a fraction of a degree already reads as ~10°/s (seen on a HiBreak lying on a desk).
     */
    private fun trackMotion(r: FloatArray, timestampNs: Long) {
        rawRotation = r
        val ref = motionRef
        if (ref == null) {
            motionRef = r
            motionRefNs = timestampNs
            return
        }
        val dt = (timestampNs - motionRefNs) / 1e9
        if (dt < 0.25) return
        angularSpeedDegPerS = angleBetweenDeg(ref, r) / dt
        motionRef = r
        motionRefNs = timestampNs
    }

    private fun checkField(values: FloatArray, timestampNs: Long) {
        val r = rawRotation ?: return
        val world = Vec3(
            (r[0] * values[0] + r[1] * values[1] + r[2] * values[2]).toDouble(),
            (r[3] * values[0] + r[4] * values[1] + r[5] * values[2]).toDouble(),
            (r[6] * values[0] + r[7] * values[1] + r[8] * values[2]).toDouble(),
        )
        val strength = MagneticCheck.strength(values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
        val expected = expectedField
        if (monitor.onReading(strength, MagneticCheck.inclination(world), angularSpeedDegPerS, expected, timestampNs / 1_000_000)) {
            magneticInterference = monitor.disturbed
        }

        // Publishing state recomposes the banners and the details sheet; once a second is plenty.
        if (timestampNs - lastFieldPublishNs < 1_000_000_000L) return
        lastFieldPublishNs = timestampNs
        if (!monitor.strengthUt.isNaN()) measuredField = MeasuredField(monitor.strengthUt, monitor.inclinationDeg)
        if (BuildConfig.DEBUG && expected != null) Log.i(
            "Compass",
            String.format(
                Locale.US, "field %.1f uT dip %.1f | expected %.1f uT dip %.1f | turning %.0f deg/s | accuracy %s | disturbed %b",
                monitor.strengthUt, monitor.inclinationDeg, expected.strengthUt, expected.inclinationDeg,
                angularSpeedDegPerS, accuracy, monitor.disturbed,
            ),
        )
    }

    private fun publish(r: FloatArray) {
        if (calmUpdates) {
            val now = SystemClock.uptimeMillis()
            val last = lastPublished
            if (last != null) {
                val moved = angleBetweenDeg(last, r)
                val sinceMs = now - lastPublishMs
                // Turning: up to ~4 updates a second. Nearly still: settle once the jitter stops.
                val due = (moved > 0.8 && sinceMs >= 250) || (moved > 0.15 && sinceMs >= 1500)
                if (!due) return
            }
            lastPublished = r
            lastPublishMs = now
        }
        rotation = r
    }

    /** Angle of the rotation taking [a] to [b], from trace(a^T b) = 1 + 2 cos(angle). */
    private fun angleBetweenDeg(a: FloatArray, b: FloatArray): Double {
        var trace = 0.0
        for (i in 0..8) trace += a[i] * b[i]
        return Math.toDegrees(acos(((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0)))
    }

    private fun updateFromAccelMag(timestampNs: Long) {
        if (!haveGravity || !haveMag) return
        val r = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, null, gravity, geomagnetic)) {
            trackMotion(r, timestampNs)
            publish(r)
        }
    }

    private fun lowPass(input: FloatArray, output: FloatArray, alpha: Float) {
        for (i in 0..2) output[i] = if (abs(alpha - 1f) < 1e-6f) input[i] else output[i] + alpha * (input[i] - output[i])
    }
}
