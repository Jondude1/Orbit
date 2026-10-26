package com.jondude1.orbit.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek

data class OrbitSettings(
    val reviewHour: Int = 19,
    val reviewMinute: Int = 30,
    /** ISO day numbers, Monday = 1 … Sunday = 7. */
    val reviewDays: Set<Int> = (1..7).toSet(),
    val perReview: Int = 5,
    val notifications: Boolean = true,
    /** Gentle nudge when more than this many things are in motion. */
    val focusLimit: Int = 3,
) {
    val days: Set<DayOfWeek>
        get() = reviewDays.map { DayOfWeek.of(it) }.toSet().ifEmpty { DayOfWeek.values().toSet() }
}

object Settings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(OrbitSettings())
    val state: StateFlow<OrbitSettings> = _state

    val current: OrbitSettings get() = _state.value

    fun init(context: Context) {
        prefs = context.getSharedPreferences("orbit_settings", Context.MODE_PRIVATE)
        _state.value = OrbitSettings(
            reviewHour = prefs.getInt("reviewHour", 19),
            reviewMinute = prefs.getInt("reviewMinute", 30),
            reviewDays = prefs.getStringSet("reviewDays", null)
                ?.mapNotNull { it.toIntOrNull() }?.toSet()
                ?.ifEmpty { null } ?: (1..7).toSet(),
            perReview = prefs.getInt("perReview", 5),
            notifications = prefs.getBoolean("notifications", true),
            focusLimit = prefs.getInt("focusLimit", 3),
        )
    }

    fun update(block: (OrbitSettings) -> OrbitSettings) {
        val s = block(_state.value)
        _state.value = s
        prefs.edit()
            .putInt("reviewHour", s.reviewHour)
            .putInt("reviewMinute", s.reviewMinute)
            .putStringSet("reviewDays", s.reviewDays.map { it.toString() }.toSet())
            .putInt("perReview", s.perReview)
            .putBoolean("notifications", s.notifications)
            .putInt("focusLimit", s.focusLimit)
            .apply()
    }

    // Small key/value helpers for non-settings bookkeeping (update checks etc.)
    fun getLong(key: String, def: Long = 0L): Long = prefs.getLong(key, def)
    fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    fun getBool(key: String, def: Boolean = false): Boolean = prefs.getBoolean(key, def)
    fun putBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun getString(key: String, def: String = ""): String = prefs.getString(key, def) ?: def
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}
