package com.wheresmysun.data

import android.content.Context
import com.wheresmysun.astro.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedPlace(val id: String, val name: String, val point: GeoPoint)

enum class ViewMode { CAMERA, MAP }

/** E-ink look: follow device detection, or force it on or off. */
enum class EinkMode { AUTO, ON, OFF }

data class Settings(
    val showMoon: Boolean = true,
    val showHourLabels: Boolean = true,
    val highlightGoldenHour: Boolean = true,
    /** Sky map rotates with the phone; off = north always up. */
    val mapFollowsCompass: Boolean = true,
    val einkMode: EinkMode = EinkMode.AUTO,
)

/** Small key-value storage. Everything here is a few hundred bytes, so plain SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("wheres_my_sun", Context.MODE_PRIVATE)

    fun loadSettings() = Settings(
        showMoon = sp.getBoolean(KEY_SHOW_MOON, true),
        showHourLabels = sp.getBoolean(KEY_HOUR_LABELS, true),
        highlightGoldenHour = sp.getBoolean(KEY_GOLDEN, true),
        mapFollowsCompass = sp.getBoolean(KEY_MAP_FOLLOWS, true),
        einkMode = runCatching { EinkMode.valueOf(sp.getString(KEY_EINK, null) ?: "") }.getOrDefault(EinkMode.AUTO),
    )

    fun saveSettings(s: Settings) {
        sp.edit()
            .putBoolean(KEY_SHOW_MOON, s.showMoon)
            .putBoolean(KEY_HOUR_LABELS, s.showHourLabels)
            .putBoolean(KEY_GOLDEN, s.highlightGoldenHour)
            .putBoolean(KEY_MAP_FOLLOWS, s.mapFollowsCompass)
            .putString(KEY_EINK, s.einkMode.name)
            .apply()
    }

    var mode: ViewMode
        get() = runCatching { ViewMode.valueOf(sp.getString(KEY_MODE, null) ?: "") }.getOrDefault(ViewMode.CAMERA)
        set(value) = sp.edit().putString(KEY_MODE, value.name).apply()

    /** Null = follow the phone's GPS. */
    var selectedPlaceId: String?
        get() = sp.getString(KEY_SELECTED_PLACE, null)
        set(value) = sp.edit().putString(KEY_SELECTED_PLACE, value).apply()

    fun loadPlaces(): List<SavedPlace> {
        val json = sp.getString(KEY_PLACES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedPlace(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    point = GeoPoint(o.getDouble("lat"), o.getDouble("lon"), o.optDouble("elev", 0.0)),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun savePlaces(places: List<SavedPlace>) {
        val arr = JSONArray()
        for (p in places) {
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("lat", p.point.latitude)
                    .put("lon", p.point.longitude)
                    .put("elev", p.point.elevation),
            )
        }
        sp.edit().putString(KEY_PLACES, arr.toString()).apply()
    }

    companion object {
        fun newPlaceId(): String = UUID.randomUUID().toString()

        private const val KEY_SHOW_MOON = "show_moon"
        private const val KEY_HOUR_LABELS = "hour_labels"
        private const val KEY_GOLDEN = "golden_hour"
        private const val KEY_MAP_FOLLOWS = "map_follows_compass"
        private const val KEY_EINK = "eink_mode"
        private const val KEY_MODE = "mode"
        private const val KEY_SELECTED_PLACE = "selected_place"
        private const val KEY_PLACES = "places"
    }
}
