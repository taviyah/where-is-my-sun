package com.wheresmysun

import android.app.Application
import android.hardware.GeomagneticField
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wheresmysun.astro.Body
import com.wheresmysun.astro.DaySky
import com.wheresmysun.astro.GeoPoint
import com.wheresmysun.astro.SkyCalculator
import com.wheresmysun.astro.SkyPath
import com.wheresmysun.astro.SkyPoint
import com.wheresmysun.data.EinkDetector
import com.wheresmysun.data.EinkMode
import com.wheresmysun.data.Prefs
import com.wheresmysun.data.SavedPlace
import com.wheresmysun.data.Settings
import com.wheresmysun.data.ViewMode
import com.wheresmysun.location.LocationTracker
import com.wheresmysun.orientation.ExpectedField
import com.wheresmysun.orientation.OrientationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToLong

/** Where the sky is being computed for, and what to call it. */
data class ActiveLocation(val point: GeoPoint, val label: String, val isGps: Boolean)

/** Result of a tap-to-align attempt, shown to the user briefly. */
sealed interface AlignResult {
    data class Adjusted(val body: Body, val byDegrees: Double) : AlignResult
    data object NothingUp : AlignResult
    data object TooFar : AlignResult
}

class SkyViewModel(app: Application) : AndroidViewModel(app) {

    val orientation = OrientationTracker(app)
    val locationTracker = LocationTracker(app)
    private val prefs = Prefs(app)

    var settings by mutableStateOf(prefs.loadSettings())
        private set
    var places by mutableStateOf(prefs.loadPlaces())
        private set
    var selectedPlaceId by mutableStateOf(prefs.selectedPlaceId)
        private set
    var mode by mutableStateOf(prefs.mode)
        private set

    /** 0 = today, 1 = tomorrow. */
    var dayOffset by mutableIntStateOf(0)
        private set
    /** Minutes after midnight picked on the slider, or null to follow the clock. */
    var selectedMinute by mutableStateOf<Int?>(null)
        private set

    /** Manual heading correction from tap-to-align, in degrees. Kept for this session only: the
     *  error it fixes depends on what metal is nearby, so it doesn't carry over to the next place. */
    var alignmentDeg by mutableDoubleStateOf(0.0)
        private set
    var aligning by mutableStateOf(false)
        private set
    var lastAlignResult by mutableStateOf<AlignResult?>(null)
        private set

    var now by mutableStateOf(ZonedDateTime.now())
        private set
    var daySky by mutableStateOf<DaySky?>(null)
        private set

    val zone: ZoneId get() = now.zone

    val isEinkDevice: Boolean = EinkDetector.isEinkDevice()

    val einkActive: Boolean
        get() = when (settings.einkMode) {
            EinkMode.ON -> true
            EinkMode.OFF -> false
            EinkMode.AUTO -> isEinkDevice
        }

    val activeLocation: ActiveLocation? by derivedStateOf {
        val place = places.firstOrNull { it.id == selectedPlaceId }
        if (place != null) {
            ActiveLocation(place.point, place.name, isGps = false)
        } else {
            locationTracker.location?.let {
                ActiveLocation(GeoPoint(it.latitude, it.longitude, if (it.hasAltitude()) it.altitude else 0.0), "Current location", isGps = true)
            }
        }
    }

    val selectedDate: LocalDate get() = now.toLocalDate().plusDays(dayOffset.toLong())

    /** The moment everything on screen is drawn for. */
    val selectedTime: ZonedDateTime
        get() = selectedMinute?.let { selectedDate.atStartOfDay(zone).plusMinutes(it.toLong()) }
            ?: now.plusDays(dayOffset.toLong())

    val isLive: Boolean get() = selectedMinute == null && dayOffset == 0

    /** Magnetic declination at the active location: how far magnetic north is east of true north. */
    val declinationDeg: Double by derivedStateOf {
        val loc = activeLocation?.point ?: return@derivedStateOf 0.0
        GeomagneticField(loc.latitude.toFloat(), loc.longitude.toFloat(), loc.elevation.toFloat(), System.currentTimeMillis())
            .declination.toDouble()
    }

    /** Add to a frame azimuth to get a true azimuth. See SkyProjection. */
    val northOffsetDeg: Double get() = declinationDeg + alignmentDeg

    init {
        orientation.calmUpdates = einkActive
        viewModelScope.launch {
            // The interference check needs the Earth's field where the phone actually is, so this
            // follows GPS even when the sky is shown for a saved place.
            snapshotFlow {
                val gps = locationTracker.location
                (gps?.let { GeoPoint(it.latitude, it.longitude, if (it.hasAltitude()) it.altitude else 0.0) } ?: activeLocation?.point)
                    ?.let { roundKey(it) to it }
            }
                .distinctUntilChanged { a, b -> a?.first == b?.first }
                .collect { keyed ->
                    val p = keyed?.second ?: return@collect
                    val field = GeomagneticField(p.latitude.toFloat(), p.longitude.toFloat(), p.elevation.toFloat(), System.currentTimeMillis())
                    orientation.expectedField = ExpectedField(field.fieldStrength / 1000.0, field.inclination.toDouble())
                }
        }
        viewModelScope.launch {
            while (true) {
                now = ZonedDateTime.now()
                delay(5_000)
            }
        }
        viewModelScope.launch {
            // Recompute the day's paths only when the date or the location (to ~1 km) changes,
            // not on every GPS update.
            snapshotFlow { activeLocation?.point?.let { roundKey(it) } to selectedDate }
                .distinctUntilChanged()
                .collectLatest { (key, date) ->
                    val loc = activeLocation?.point
                    if (key == null || loc == null) {
                        daySky = null
                        return@collectLatest
                    }
                    val zone = zone
                    daySky = withContext(Dispatchers.Default) { SkyCalculator.computeDay(loc, date, zone) }
                }
        }
    }

    private fun roundKey(p: GeoPoint) = (p.latitude * 100).roundToLong() to (p.longitude * 100).roundToLong()

    fun position(body: Body, time: ZonedDateTime = selectedTime): SkyPoint? =
        activeLocation?.point?.let { SkyCalculator.position(body, it, time.toInstant()) }

    // --- time controls ---

    fun selectDay(offset: Int) {
        dayOffset = offset.coerceIn(0, 1)
    }

    fun selectMinute(minute: Int) {
        selectedMinute = minute.coerceIn(0, 24 * 60 - 1)
    }

    fun backToNow() {
        selectedMinute = null
        dayOffset = 0
    }

    // --- modes, settings, places ---

    fun selectMode(m: ViewMode) {
        mode = m
        prefs.mode = m
        if (m != ViewMode.CAMERA) aligning = false
    }

    fun updateSettings(s: Settings) {
        settings = s
        prefs.saveSettings(s)
        orientation.calmUpdates = einkActive
    }

    fun selectPlace(id: String?) {
        selectedPlaceId = id
        prefs.selectedPlaceId = id
    }

    fun addPlace(name: String, point: GeoPoint, select: Boolean = true) {
        val place = SavedPlace(Prefs.newPlaceId(), name.ifBlank { "Saved place" }, point)
        places = places + place
        prefs.savePlaces(places)
        if (select) selectPlace(place.id)
    }

    fun deletePlace(id: String) {
        places = places.filterNot { it.id == id }
        prefs.savePlaces(places)
        if (selectedPlaceId == id) selectPlace(null)
    }

    // --- tap to align ---

    fun startAligning() {
        // Alignment compares against where the sun is right now, so show "now" while aligning.
        backToNow()
        aligning = true
        lastAlignResult = null
    }

    fun cancelAligning() {
        aligning = false
    }

    fun resetAlignment() {
        alignmentDeg = 0.0
        lastAlignResult = null
    }

    fun clearAlignResult() {
        lastAlignResult = null
    }

    /** The body the user should tap on: the sun if it is up right now, else the moon. */
    fun alignmentTarget(): Pair<Body, SkyPoint>? {
        val sun = position(Body.SUN, now) ?: return null
        if (sun.altitude > 1.0) return Body.SUN to sun
        val moon = position(Body.MOON, now) ?: return null
        if (moon.altitude > 1.0) return Body.MOON to moon
        return null
    }

    /**
     * The user tapped where they see the real sun (or moon), at sky direction [tapped] as the
     * overlay currently maps it. Shift the heading so the computed position lands on the tap.
     */
    fun alignTo(tapped: SkyPoint) {
        val target = alignmentTarget()
        if (target == null) {
            lastAlignResult = AlignResult.NothingUp
            aligning = false
            return
        }
        val delta = SkyPath.signedDelta(tapped.azimuth, target.second.azimuth)
        if (abs(delta) > 90.0) {
            lastAlignResult = AlignResult.TooFar
            return
        }
        alignmentDeg = SkyPath.signedDelta(0.0, alignmentDeg + delta)
        lastAlignResult = AlignResult.Adjusted(target.first, delta)
        aligning = false
    }
}
