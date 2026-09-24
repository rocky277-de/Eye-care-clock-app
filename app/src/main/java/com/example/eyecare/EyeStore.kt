package com.example.eyecare

import android.content.Context
import android.content.SharedPreferences

/** Single source of truth for settings + timer state (shared by UI, alarm, service). */
object EyeStore {
    const val DEFAULT_WORK_MIN = 20
    const val DEFAULT_BREAK_SEC = 20

    private fun p(c: Context) = c.getSharedPreferences("eyecare_prefs", Context.MODE_PRIVATE)

    fun workMin(c: Context) = p(c).getInt("work_min", DEFAULT_WORK_MIN)
    fun breakSec(c: Context) = p(c).getInt("break_sec", DEFAULT_BREAK_SEC)
    fun running(c: Context) = p(c).getBoolean("running", false)
    fun nextAt(c: Context) = p(c).getLong("next_at", 0L)
    fun breakUntil(c: Context) = p(c).getLong("break_until", 0L)
    fun remaining(c: Context) = p(c).getLong("remaining", 0L)
    fun breaks(c: Context) = p(c).getInt("breaks_completed", 0)

    fun edit(c: Context, block: SharedPreferences.Editor.() -> Unit) {
        p(c).edit().also(block).apply()
    }
}
