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
        TimerManager.isFocusModeEnabled(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        TimerManager.setFocusModeEnabled(context, enabled)
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

    fun getMinutesForDay(context: Context, daysAgo: Int): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        return (prefs(context).getLong(PREF_FOCUS_MINUTES_PREFIX + key, 0L) / 60_000L).toInt()
    }

    fun getSevenDayMinutes(context: Context): Int =
        (0..6).sumOf { getMinutesForDay(context, it) }

    fun getSevenDaySessions(context: Context): Int {
        val calendar = java.util.Calendar.getInstance()
        return (0..6).sumOf { daysAgo ->
            val c = java.util.Calendar.getInstance()
            c.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
            val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
            prefs(context).getInt(PREF_SESSIONS_TODAY + "_" + key, 0)
        }
    }


    fun getMinutesForRange(context: Context, startDaysAgo: Int, endDaysAgo: Int): Int =
        (startDaysAgo..endDaysAgo).sumOf { getMinutesForDay(context, it) }

    fun getSessionsForDay(context: Context, daysAgo: Int): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        return prefs(context).getInt(PREF_SESSIONS_TODAY + "_" + key, 0)
    }

    fun getSessionCountForRange(context: Context, startDaysAgo: Int, endDaysAgo: Int): Int =
        (startDaysAgo..endDaysAgo).sumOf { getSessionsForDay(context, it) }

    fun getThirtyDayMinutes(context: Context): Int = getMinutesForRange(context, 0, 29)

    fun getThirtyDaySessions(context: Context): Int = getSessionCountForRange(context, 0, 29)

    fun getBestFocusDay(context: Context, days: Int = 30): Pair<Int, Int> {
        val end = (days - 1).coerceAtLeast(0)
        var bestDay = 0
        var bestMinutes = -1
        for (day in 0..end) {
            val minutes = getMinutesForDay(context, day)
            if (minutes > bestMinutes) {
                bestMinutes = minutes
                bestDay = day
            }
        }
        return bestDay to bestMinutes.coerceAtLeast(0)
    }

    fun getAverageSessionMinutes(context: Context, days: Int = 7): Int {
        val end = (days - 1).coerceAtLeast(0)
        val minutes = getMinutesForRange(context, 0, end)
        val sessions = getSessionCountForRange(context, 0, end)
        return if (sessions > 0) minutes / sessions else 0
    }

    fun getGoalCompletionDays(context: Context, days: Int = 7): Int {
        val end = (days - 1).coerceAtLeast(0)
        val goal = getGoalMinutes(context)
        return (0..end).count { getMinutesForDay(context, it) >= goal }
    }

    fun getLongestFocusStreak(context: Context): Int {
        var streak = 0
        var best = 0
        for (day in 0..6) {
            if (getMinutesForDay(context, day) > 0) {
                streak++
                best = maxOf(best, streak)
            } else {
                streak = 0
            }
        }
        return best
    }

    fun getAverageDailyMinutes(context: Context): Int =
        getSevenDayMinutes(context) / 7

    fun getBreakCompleted(context: Context, daysAgo: Int = 0): Int =
        getDailyBreakStat(context, "stats_completed_", daysAgo)

    fun getBreakSkipped(context: Context, daysAgo: Int = 0): Int =
        getDailyBreakStat(context, "stats_skipped_", daysAgo)

    fun getBreakMissed(context: Context, daysAgo: Int = 0): Int =
        getDailyBreakStat(context, "stats_missed_", daysAgo)

    fun getBreakTotal(context: Context, days: Int = 7): Int =
        (0 until days.coerceAtLeast(1)).sumOf { getBreakCompleted(context, it) + getBreakSkipped(context, it) }

    fun getBreakCompletionRate(context: Context, days: Int = 7): Int {
        val completed = (0 until days.coerceAtLeast(1)).sumOf { getBreakCompleted(context, it) }
        val total = getBreakTotal(context, days)
        return if (total > 0) ((completed * 100f) / total).toInt() else 0
    }

    fun getBreakSkipRate(context: Context, days: Int = 7): Int {
        val skipped = (0 until days.coerceAtLeast(1)).sumOf { getBreakSkipped(context, it) }
        val total = getBreakTotal(context, days)
        return if (total > 0) ((skipped * 100f) / total).toInt() else 0
    }

    fun getBreakCompletionDays(context: Context, days: Int = 7): Int {
        return (0 until days.coerceAtLeast(1)).count { getBreakCompleted(context, it) > 0 }
    }

    fun shouldRecommendBreak(context: Context): Boolean =
        isEnabled(context) && getTodaySessions(context) > 0 &&
            TimerManager.isRunning(context) && TimerManager.isWorkPhase(context)

    private fun getDailyBreakStat(context: Context, prefix: String, daysAgo: Int): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        return prefs(context).getInt(prefix + key, 0)
    }

    // Phase 10C — achievement helpers
    fun getCurrentFocusStreak(context: Context): Int {
        var streak = 0
        for (day in 0..29) {
            if (getMinutesForDay(context, day) > 0) streak++ else break
        }
        return streak
    }

    fun getLongestFocusStreak30Days(context: Context): Int {
        var current = 0
        var best = 0
        for (day in 0..29) {
            if (getMinutesForDay(context, day) > 0) {
                current++
                best = maxOf(best, current)
            } else current = 0
        }
        return best
    }

    fun getAchievementCount(context: Context): Int {
        val streak = getCurrentFocusStreak(context)
        val minutes = getThirtyDayMinutes(context)
        val sessions = getThirtyDaySessions(context)
        val goalDays = getGoalCompletionDays(context, 30)
        var count = 0
        if (streak >= 3) count++
        if (streak >= 7) count++
        if (streak >= 14) count++
        if (minutes >= 300) count++
        if (minutes >= 600) count++
        if (sessions >= 10) count++
        if (sessions >= 25) count++
        if (goalDays >= 3) count++
        if (goalDays >= 7) count++
        return count
    }

    fun getNextAchievement(context: Context): String {
        val streak = getCurrentFocusStreak(context)
        val minutes = getThirtyDayMinutes(context)
        val sessions = getThirtyDaySessions(context)
        val goalDays = getGoalCompletionDays(context, 30)
        return when {
            streak < 3 -> "3-day focus streak"
            streak < 7 -> "7-day focus streak"
            streak < 14 -> "14-day focus streak"
            minutes < 300 -> "300 focus minutes (30d)"
            minutes < 600 -> "600 focus minutes (30d)"
            sessions < 10 -> "10 focus sessions (30d)"
            sessions < 25 -> "25 focus sessions (30d)"
            goalDays < 3 -> "3 goal-completion days (30d)"
            goalDays < 7 -> "7 goal-completion days (30d)"
            else -> "All current achievements unlocked"
        }
    }
    fun shouldUseLongBreak(context: Context): Boolean =
        isEnabled(context) && getTodaySessions(context) > 0 &&
            getTodaySessions(context) % SESSIONS_BEFORE_LONG_BREAK == 0
}
