package com.example.eyecare

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules the next work interval for the 20-20-20 reminder. */
object TimerManager {
    private const val REQUEST_CODE = 1001
    const val PREFS_NAME = "eyecare_prefs"
    const val PREF_RUNNING = "timer_running"
    const val PREF_WORK_MINUTES = "work_minutes"
    const val PREF_REST_SECONDS = "rest_seconds"
    const val PREF_NEXT_TRIGGER_AT = "next_trigger_at"
    const val PREF_TIMER_STARTED_AT = "timer_started_at"
    const val PREF_TIMER_DURATION_MS = "timer_duration_ms"
    const val PREF_REMAINING_MS = "remaining_ms"
    const val PREF_PHASE = "timer_phase"
    const val PREF_FOCUS_MODE_ENABLED = "focus_mode_enabled"
    const val PREF_FOCUS_ACTIVE = "focus_active"
    const val PREF_FOCUS_START_AT = "focus_start_at"
    const val PREF_FOCUS_GOAL_MINUTES = "focus_goal_minutes"
    const val DEFAULT_FOCUS_GOAL_MINUTES = 120
    const val MIN_FOCUS_GOAL_MINUTES = 15
    const val MAX_FOCUS_GOAL_MINUTES = 480

    const val PHASE_WORK = "work"
    const val PHASE_BREAK = "break"

    const val DEFAULT_WORK_MINUTES = 20
    const val DEFAULT_REST_SECONDS = 20
    const val MIN_WORK_MINUTES = 1
    const val MAX_WORK_MINUTES = 120
    const val MIN_REST_SECONDS = 5
    const val MAX_REST_SECONDS = 300

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun safeLong(value: Long, fallback: Long = 0L): Long =
        if (value >= 0L) value else fallback

    fun getWorkMinutes(context: Context): Int =
        prefs(context).getInt(PREF_WORK_MINUTES, DEFAULT_WORK_MINUTES)
            .coerceIn(MIN_WORK_MINUTES, MAX_WORK_MINUTES)

    fun getRestSeconds(context: Context): Int =
        prefs(context).getInt(PREF_REST_SECONDS, DEFAULT_REST_SECONDS)
            .coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS)

    fun saveSettings(context: Context, workMinutes: Int, restSeconds: Int) {
        prefs(context).edit()
            .putInt(PREF_WORK_MINUTES, workMinutes.coerceIn(MIN_WORK_MINUTES, MAX_WORK_MINUTES))
            .putInt(PREF_REST_SECONDS, restSeconds.coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS))
            .apply()
    }

    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun startTimer(context: Context, remainingMs: Long? = null) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requested = safeLong(remainingMs ?: getRemainingMs(context))
        val maxDuration = getWorkMinutes(context) * 60_000L
        val duration = requested.coerceIn(1L, maxDuration).let {
            if (remainingMs == null && !isRunning(context)) maxDuration else it
        }
        val triggerAt = System.currentTimeMillis() + duration
        val pendingIntent = getPendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        if (isFocusModeEnabled(context)) startFocus(context)
        prefs(context).edit()
            .putBoolean(PREF_RUNNING, true)
            .putString(PREF_PHASE, PHASE_WORK)
            .putLong(PREF_NEXT_TRIGGER_AT, triggerAt)
            .putLong(PREF_TIMER_STARTED_AT, System.currentTimeMillis())
            .putLong(PREF_TIMER_DURATION_MS, duration)
            .remove(PREF_REMAINING_MS)
            .apply()
    }

    fun pauseTimer(context: Context, remainingMs: Long) {
        pauseFocus(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getPendingIntent(context))
        prefs(context).edit().putBoolean(PREF_RUNNING, false)
            .putLong(PREF_REMAINING_MS, remainingMs.coerceAtLeast(0L))
            .remove(PREF_NEXT_TRIGGER_AT)
            .remove(PREF_TIMER_STARTED_AT)
            .remove(PREF_TIMER_DURATION_MS)
            .apply()
    }

    fun stopTimer(context: Context) {
        stopFocus(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getPendingIntent(context))
        prefs(context).edit().putBoolean(PREF_RUNNING, false)
            .putLong(PREF_REMAINING_MS, getWorkMinutes(context) * 60_000L)
            .remove(PREF_NEXT_TRIGGER_AT)
            .remove(PREF_TIMER_STARTED_AT)
            .remove(PREF_TIMER_DURATION_MS)
            .putString(PREF_PHASE, PHASE_WORK)
            .apply()
    }

    fun markBreakReady(context: Context) {
        // Focus time belongs to the work interval, not the eye/rest break.
        pauseFocus(context)
        prefs(context).edit()
            .putString(PREF_PHASE, PHASE_BREAK)
            .remove(PREF_NEXT_TRIGGER_AT)
            .remove(PREF_REMAINING_MS)
            .remove(PREF_TIMER_STARTED_AT)
            .remove(PREF_TIMER_DURATION_MS)
            .apply()
    }

    fun isRunning(context: Context): Boolean = prefs(context).getBoolean(PREF_RUNNING, false)

    fun isWorkPhase(context: Context): Boolean =
        prefs(context).getString(PREF_PHASE, PHASE_WORK) == PHASE_WORK

    fun getRemainingMs(context: Context): Long {
        val p = prefs(context)
        val saved = safeLong(p.getLong(PREF_REMAINING_MS, 0L))
        if (saved > 0L) return saved
        val trigger = safeLong(p.getLong(PREF_NEXT_TRIGGER_AT, 0L))
        if (trigger > 0L) return (trigger - System.currentTimeMillis()).coerceAtLeast(0L)

        val startedAt = safeLong(p.getLong(PREF_TIMER_STARTED_AT, 0L))
        val duration = safeLong(p.getLong(PREF_TIMER_DURATION_MS, 0L))
        if (startedAt > 0L && duration > 0L) {
            return (startedAt + duration - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        return 0L
    }

    private fun setRunning(context: Context, running: Boolean) {
        prefs(context).edit().putBoolean(PREF_RUNNING, running).apply()
    }

    fun rescheduleNext(context: Context) {
        if (!isRunning(context)) return
        startTimer(context, getWorkMinutes(context) * 60_000L)
    }

    fun isFocusModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PREF_FOCUS_MODE_ENABLED, false)

    fun setFocusModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_FOCUS_MODE_ENABLED, enabled).apply()
        if (!enabled) {
            pauseFocus(context)
        } else if (isRunning(context) && isWorkPhase(context)) {
            startFocus(context)
        }
    }

    fun isFocusActive(context: Context): Boolean =
        prefs(context).getBoolean(PREF_FOCUS_ACTIVE, false)

    fun getFocusGoalMinutes(context: Context): Int =
        prefs(context).getInt(PREF_FOCUS_GOAL_MINUTES, DEFAULT_FOCUS_GOAL_MINUTES)
            .coerceIn(MIN_FOCUS_GOAL_MINUTES, MAX_FOCUS_GOAL_MINUTES)

    fun saveFocusGoalMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(
            PREF_FOCUS_GOAL_MINUTES,
            minutes.coerceIn(MIN_FOCUS_GOAL_MINUTES, MAX_FOCUS_GOAL_MINUTES)
        ).apply()
    }

    fun startFocus(context: Context) {
        if (!isFocusModeEnabled(context) || isFocusActive(context)) return
        prefs(context).edit()
            .putBoolean(PREF_FOCUS_ACTIVE, true)
            .putLong(PREF_FOCUS_START_AT, System.currentTimeMillis())
            .apply()
    }

    fun pauseFocus(context: Context) {
        if (!isFocusActive(context)) return
        accumulateFocus(context)
        prefs(context).edit()
            .putBoolean(PREF_FOCUS_ACTIVE, false)
            .remove(PREF_FOCUS_START_AT)
            .apply()
    }

    fun stopFocus(context: Context) {
        if (!isFocusActive(context)) return
        accumulateFocus(context)
        prefs(context).edit()
            .putBoolean(PREF_FOCUS_ACTIVE, false)
            .remove(PREF_FOCUS_START_AT)
            .apply()
    }

    private fun accumulateFocus(context: Context) {
        val p = prefs(context)
        val start = safeLong(p.getLong(PREF_FOCUS_START_AT, 0L))
        if (start <= 0L) return

        val now = System.currentTimeMillis()
        if (now <= start) return

        // A focus interval can cross midnight. Split elapsed time by local date
        // so yesterday's minutes never leak into today's statistics.
        val editor = p.edit()
        var cursor = start
        val maxElapsed = getWorkMinutes(context) * 60_000L
        val end = minOf(now, start + maxElapsed)

        while (cursor < end) {
            val date = java.util.Calendar.getInstance().apply { timeInMillis = cursor }
            val nextDay = (date.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val segmentEnd = minOf(end, nextDay)
            val segment = (segmentEnd - cursor).coerceAtLeast(0L)
            val key = focusTotalKey(cursor)
            val existing = safeLong(p.getLong(key, 0L))
            editor.putLong(key, existing + segment)
            cursor = segmentEnd
        }
        editor.apply()
    }

    private fun focusTotalKey(atMillis: Long = System.currentTimeMillis()): String {
        val date = java.text.SimpleDateFormat(
            "yyyy-MM-dd",
            java.util.Locale.US
        ).format(java.util.Date(atMillis))
        return "focus_total_ms_$date"
    }

    fun getFocusMsForDay(context: Context, daysAgo: Int): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        val key = focusTotalKey(calendar.timeInMillis)
        return safeLong(prefs(context).getLong(key, 0L))
    }

    fun getCurrentFocusElapsedMs(context: Context): Long {
        if (!isFocusActive(context)) return 0L
        val start = safeLong(prefs(context).getLong(PREF_FOCUS_START_AT, 0L))
        if (start <= 0L) return 0L
        return (System.currentTimeMillis() - start).coerceAtLeast(0L)
            .coerceAtMost(getWorkMinutes(context) * 60_000L)
    }

    fun getTodayFocusMs(context: Context): Long {
        val p = prefs(context)
        var total = safeLong(p.getLong(focusTotalKey(), 0L))
        if (isFocusActive(context)) {
            val start = safeLong(p.getLong(PREF_FOCUS_START_AT, 0L))
            val now = System.currentTimeMillis()
            if (start > 0L && now > start) {
                // Count only the portion of an active interval that belongs to today.
                // This keeps the dashboard correct if the phone remains focused across midnight.
                val todayStart = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val segmentStart = maxOf(start, todayStart)
                val elapsedToday = (now - segmentStart).coerceAtLeast(0L)
                total += elapsedToday.coerceAtMost(getWorkMinutes(context) * 60_000L)
            }
        }
        return total
    }

    fun getTodayFocusMinutes(context: Context): Int =
        (getTodayFocusMs(context) / 60_000L).toInt().coerceAtLeast(0)

    fun recoverFocusAfterBoot(context: Context) {
        val p = prefs(context)
        if (!isFocusActive(context)) return

        // Time while the device was rebooting must not be counted as focus time.
        // Resume a fresh focus interval from the moment boot recovery runs.
        p.edit()
            .putBoolean(PREF_FOCUS_ACTIVE, false)
            .remove(PREF_FOCUS_START_AT)
            .apply()
        if (isFocusModeEnabled(context) && isRunning(context) && isWorkPhase(context)) {
            startFocus(context)
        }
    }

    fun resetTimerAndStatistics(context: Context) {
        val p = prefs(context)
        val preserved = mapOf(
            PREF_WORK_MINUTES to getWorkMinutes(context),
            PREF_REST_SECONDS to getRestSeconds(context),
            PREF_FOCUS_MODE_ENABLED to isFocusModeEnabled(context),
            PREF_FOCUS_GOAL_MINUTES to getFocusGoalMinutes(context)
        )
        val blockedApps = p.getStringSet("focus_blocked_apps", emptySet())?.toSet() ?: emptySet()
        val shortsEnabled = p.getBoolean("block_youtube_shorts", false)
        val keepPosition = p.getBoolean("keep_overlay_position", true)

        p.edit().clear()
            .putInt(PREF_WORK_MINUTES, preserved[PREF_WORK_MINUTES] as Int)
            .putInt(PREF_REST_SECONDS, preserved[PREF_REST_SECONDS] as Int)
            .putBoolean(PREF_FOCUS_MODE_ENABLED, preserved[PREF_FOCUS_MODE_ENABLED] as Boolean)
            .putInt(PREF_FOCUS_GOAL_MINUTES, preserved[PREF_FOCUS_GOAL_MINUTES] as Int)
            .putStringSet("focus_blocked_apps", blockedApps)
            .putBoolean("block_youtube_shorts", shortsEnabled)
            .putBoolean("keep_overlay_position", keepPosition)
            .putBoolean(PREF_RUNNING, false)
            .putString(PREF_PHASE, PHASE_WORK)
            .putLong(PREF_REMAINING_MS, getWorkMinutes(context) * 60_000L)
            .apply()
    }

}
