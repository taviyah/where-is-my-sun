package com.wheresmysun.orientation

/**
 * Decides over time whether the compass is disturbed, from a stream of field readings.
 *
 * A single reading isn't enough: while the phone is turning, the magnetometer and the tilt estimate
 * are a few milliseconds apart, which wrecks the dip calculation (measured on a HiBreak: readings
 * swung from -41° to +57° during a figure-8). So readings only count once the phone has been
 * fairly still for a moment, and the verdict flips only after two checks in a row agree.
 */
class MagneticMonitor(
    private val stillSpeedDegPerS: Double = 15.0,
    private val settleMs: Long = 1_000,
    private val checkEveryMs: Long = 1_000,
    private val agreeingChecks: Int = 2,
) {
    var disturbed = false
        private set

    /** Smoothed values of the last still stretch, for display. */
    var strengthUt = Double.NaN
        private set
    var inclinationDeg = Double.NaN
        private set

    private var stillSinceMs = Long.MIN_VALUE
    private var lastCheckMs = Long.MIN_VALUE
    private var pendingVerdict: Boolean? = null
    private var pendingCount = 0

    /**
     * Feeds one reading. [angularSpeedDegPerS] is how fast the phone is turning right now.
     * Returns true when [disturbed] changed.
     */
    fun onReading(
        strengthUt: Double,
        inclinationDeg: Double,
        angularSpeedDegPerS: Double,
        expected: ExpectedField?,
        timeMs: Long,
    ): Boolean {
        if (angularSpeedDegPerS > stillSpeedDegPerS) {
            stillSinceMs = Long.MIN_VALUE
            pendingVerdict = null
            pendingCount = 0
            return false
        }
        if (stillSinceMs == Long.MIN_VALUE) {
            // First still reading: start averaging afresh, so swings from the motion don't linger.
            stillSinceMs = timeMs
            this.strengthUt = strengthUt
            this.inclinationDeg = inclinationDeg
        } else {
            this.strengthUt += 0.15 * (strengthUt - this.strengthUt)
            this.inclinationDeg += 0.15 * (inclinationDeg - this.inclinationDeg)
        }

        if (expected == null) return false
        if (timeMs - stillSinceMs < settleMs) return false
        if (lastCheckMs != Long.MIN_VALUE && timeMs - lastCheckMs < checkEveryMs) return false
        lastCheckMs = timeMs

        val verdict = MagneticCheck.isDisturbed(MeasuredField(this.strengthUt, this.inclinationDeg), expected, disturbed)
        if (verdict == disturbed) {
            pendingVerdict = null
            pendingCount = 0
            return false
        }
        if (pendingVerdict == verdict) pendingCount++ else {
            pendingVerdict = verdict
            pendingCount = 1
        }
        if (pendingCount >= agreeingChecks) {
            disturbed = verdict
            pendingVerdict = null
            pendingCount = 0
            return true
        }
        return false
    }
}
