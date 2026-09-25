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
        prefs(context).edit().putBoolean(PREF_RUNNING, true).putString(PREF_PHASE, PHASE_WORK).putLong(PREF_NEXT_TRIGGER_AT, triggerAt).remove(PREF_REMAINING_MS).apply()
    }

    fun pauseTimer(context: Context, remainingMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getPendingIntent(context))
        prefs(context).edit().putBoolean(PREF_RUNNING, false)
            .putLong(PREF_REMAINING_MS, remainingMs.coerceAtLeast(0L))
            .remove(PREF_NEXT_TRIGGER_AT).apply()
    }

    fun stopTimer(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getPendingIntent(context))
        prefs(context).edit().putBoolean(PREF_RUNNING, false)
            .putLong(PREF_REMAINING_MS, getWorkMinutes(context) * 60_000L)
            .remove(PREF_NEXT_TRIGGER_AT).putString(PREF_PHASE, PHASE_WORK).apply()
    }

    fun markBreakReady(context: Context) {
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
}
