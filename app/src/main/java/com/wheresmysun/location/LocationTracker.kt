package com.wheresmysun.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Keeps the phone's location while the app is open, using the platform LocationManager (no Google
 * Play services needed). Sun and moon directions change by well under a degree across a city, so
 * one decent fix is plenty; updates are requested rarely just to follow a user who drives off.
 */
class LocationTracker(context: Context) : LocationListener {

    private val manager = context.getSystemService(LocationManager::class.java)
    private var started = false

    var location by mutableStateOf<Location?>(null)
        private set

    @SuppressLint("MissingPermission") // callers check the permission first
    fun start() {
        if (started || manager == null) return
        started = true
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) || it == LocationManager.PASSIVE_PROVIDER }

        for (p in providers) {
            runCatching { manager.getLastKnownLocation(p) }.getOrNull()?.let { offer(it) }
        }
        for (p in providers) {
            if (p == LocationManager.PASSIVE_PROVIDER) continue
            runCatching { manager.requestLocationUpdates(p, 30_000L, 50f, this, Looper.getMainLooper()) }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        manager?.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) = offer(location)

    // Older Android versions call these and crash on the default abstract-method error otherwise.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    private fun offer(candidate: Location) {
        val current = location
        if (current == null || isBetter(candidate, current)) location = candidate
    }

    private fun isBetter(candidate: Location, current: Location): Boolean {
        val ageDiff = candidate.time - current.time
        if (ageDiff > 2 * 60_000) return true
        if (ageDiff < -2 * 60_000) return false
        return candidate.accuracy <= current.accuracy || ageDiff > 0 && candidate.accuracy < current.accuracy * 1.5f
    }
}
