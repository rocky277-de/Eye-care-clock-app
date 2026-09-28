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
        val duration = (remainingMs ?: getRemainingMs(context)).let { if (it > 0L) it else getWorkMinutes(context) * 60_000L }
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
        prefs(context).edit().putBoolean(PREF_RUNNING, true).putString(PREF_PHASE, PHASE_WORK).putLong(PREF_NEXT_TRIGGER_AT, triggerAt).remove(PREF_REMAINING_MS).apply()
    }

    fun pauseTimer(context: Context, remainingMs: Long) {
        pauseFocus(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getPendingIntent(context))
        prefs(context).edit().putBoolean(PREF_RUNNING, false)
            .putLong(PREF_REMAINING_MS, remainingMs.coerceAtLeast(0L))
            .remove(PREF_NEXT_TRIGGER_AT).apply()
    }

    fun stopTimer(context: Context) {
        stopFocus(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getPendingIntent(context))
        prefs(context).edit().putBoolean(PREF_RUNNING, false)
            .putLong(PREF_REMAINING_MS, getWorkMinutes(context) * 60_000L)
            .remove(PREF_NEXT_TRIGGER_AT).putString(PREF_PHASE, PHASE_WORK).apply()
    }

    fun markBreakReady(context: Context) {
        // Focus time belongs to the work interval, not the eye/rest break.
        pauseFocus(context)
        prefs(context).edit().putString(PREF_PHASE, PHASE_BREAK)
            .remove(PREF_NEXT_TRIGGER_AT).remove(PREF_REMAINING_MS).apply()
    }

    fun isRunning(context: Context): Boolean = prefs(context).getBoolean(PREF_RUNNING, false)

    fun isWorkPhase(context: Context): Boolean =
        prefs(context).getString(PREF_PHASE, PHASE_WORK) == PHASE_WORK

    fun getRemainingMs(context: Context): Long {
        val p = prefs(context)
        val saved = p.getLong(PREF_REMAINING_MS, 0L)
        if (saved > 0L) return saved
        val trigger = p.getLong(PREF_NEXT_TRIGGER_AT, 0L)
        return if (trigger > 0L) (trigger - System.currentTimeMillis()).coerceAtLeast(0L) else 0L
    }

    private fun setRunning(context: Context, running: Boolean) {
        prefs(context).edit().putBoolean(PREF_RUNNING, running).apply()
    }

    fun rescheduleNext(context: Context) {
        if (isRunning(context)) startTimer(context, getWorkMinutes(context) * 60_000L)
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
        val start = p.getLong(PREF_FOCUS_START_AT, 0L)
        if (start <= 0L) return
        val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(0L)
        val key = focusTotalKey()
        p.edit().putLong(key, p.getLong(key, 0L) + elapsed).apply()
    }

    private fun focusTotalKey(): String {
        val calendar = java.util.Calendar.getInstance()
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calendar.time)
        return "focus_total_ms_$date"
    }

    fun getTodayFocusMs(context: Context): Long {
        val p = prefs(context)
        var total = p.getLong(focusTotalKey(), 0L)
        if (isFocusActive(context)) {
            val start = p.getLong(PREF_FOCUS_START_AT, 0L)
            if (start > 0L) total += (System.currentTimeMillis() - start).coerceAtLeast(0L)
        }
        return total
    }

    fun getTodayFocusMinutes(context: Context): Int =
        (getTodayFocusMs(context) / 60_000L).toInt()

}
