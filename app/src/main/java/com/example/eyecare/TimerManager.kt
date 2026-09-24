package com.example.eyecare

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Schedules the single repeating work -> break cycle using AlarmManager. */
object TimerManager {

    private fun alarmPi(c: Context) = PendingIntent.getBroadcast(
        c, 1001, Intent(c, TimerReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun canExact(c: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am(c).canScheduleExactAlarms()

    private fun schedule(c: Context, triggerAt: Long) {
        if (canExact(c)) {
            am(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarmPi(c))
        } else {
            // No permission needed, exact, and allowed to start the overlay service from background.
            val show = PendingIntent.getActivity(
                c, 1002, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            am(c).setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), alarmPi(c))
        }
        EyeStore.edit(c) { putBoolean("running", true); putLong("next_at", triggerAt) }
    }

    fun start(c: Context, delayMs: Long) = schedule(c, System.currentTimeMillis() + delayMs)

    fun stop(c: Context) {
        am(c).cancel(alarmPi(c))
        EyeStore.edit(c) { putBoolean("running", false) }
    }

    /** Alarm fired: record the break and schedule the next one AFTER the break ends. */
    fun onFire(c: Context) {
        val now = System.currentTimeMillis()
        val breakMs = EyeStore.breakSec(c) * 1000L
        val count = EyeStore.breaks(c) + 1
        EyeStore.edit(c) { putLong("break_until", now + breakMs); putInt("breaks_completed", count) }
        schedule(c, now + breakMs + EyeStore.workMin(c) * 60_000L)
    }
}
