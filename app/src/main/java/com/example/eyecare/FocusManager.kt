package com.example.eyecare

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FocusManager {
    const val PREF_FOCUS_MODE = "focus_mode_enabled"
    const val PREF_DAILY_GOAL = "focus_daily_goal_minutes"
    const val PREF_SESSIONS_TODAY = "focus_sessions_today"
    const val PREF_FOCUS_MINUTES_PREFIX = "focus_minutes_"
    const val PREF_LONG_BREAK_MINUTES = "focus_long_break_minutes"

    const val DEFAULT_GOAL_MINUTES = 120
    const val MIN_GOAL_MINUTES = 15
    const val MAX_GOAL_MINUTES = 600
    const val DEFAULT_LONG_BREAK_MINUTES = 5
    const val MIN_LONG_BREAK_MINUTES = 1
    const val MAX_LONG_BREAK_MINUTES = 30
    const val SESSIONS_BEFORE_LONG_BREAK = 4

    private fun prefs(context: Context) =
        context.getSharedPreferences(TimerManager.PREFS_NAME, Context.MODE_PRIVATE)

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_FOCUS_MODE, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_FOCUS_MODE, enabled).apply()
    }

    fun getGoalMinutes(context: Context): Int =
        prefs(context).getInt(PREF_DAILY_GOAL, DEFAULT_GOAL_MINUTES)
            .coerceIn(MIN_GOAL_MINUTES, MAX_GOAL_MINUTES)

    fun setGoalMinutes(context: Context, minutes: Int) {
        prefs(context).edit()
            .putInt(PREF_DAILY_GOAL, minutes.coerceIn(MIN_GOAL_MINUTES, MAX_GOAL_MINUTES))
            .apply()
    }

    fun getLongBreakMinutes(context: Context): Int =
        prefs(context).getInt(PREF_LONG_BREAK_MINUTES, DEFAULT_LONG_BREAK_MINUTES)
            .coerceIn(MIN_LONG_BREAK_MINUTES, MAX_LONG_BREAK_MINUTES)

    fun setLongBreakMinutes(context: Context, minutes: Int) {
        prefs(context).edit()
            .putInt(PREF_LONG_BREAK_MINUTES, minutes.coerceIn(MIN_LONG_BREAK_MINUTES, MAX_LONG_BREAK_MINUTES))
            .apply()
    }

    fun getTodayMinutes(context: Context): Int =
        prefs(context).getInt(PREF_FOCUS_MINUTES_PREFIX + todayKey(), 0)

    fun getTodaySessions(context: Context): Int =
        prefs(context).getInt(PREF_SESSIONS_TODAY + "_" + todayKey(), 0)

    fun recordCompletedSession(context: Context, minutes: Int) {
        if (!isEnabled(context)) return
        val p = prefs(context)
        val key = PREF_FOCUS_MINUTES_PREFIX + todayKey()
        val sessionKey = PREF_SESSIONS_TODAY + "_" + todayKey()
        p.edit()
            .putInt(key, p.getInt(key, 0) + minutes.coerceAtLeast(0))
            .putInt(sessionKey, p.getInt(sessionKey, 0) + 1)
            .apply()
    }

    fun shouldUseLongBreak(context: Context): Boolean =
        isEnabled(context) && getTodaySessions(context) > 0 &&
            getTodaySessions(context) % SESSIONS_BEFORE_LONG_BREAK == 0
}
