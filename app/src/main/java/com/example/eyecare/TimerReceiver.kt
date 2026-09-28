package com.example.eyecare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Fires when the configured work interval ends and opens the paused rest overlay. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!TimerManager.isRunning(context)) return

        // Only the active work-phase alarm may open a break. This prevents
        // duplicate/stale broadcasts from recording duplicate sessions.
        if (!TimerManager.isWorkPhase(context)) return

        // Ignore stale/racing alarms from an earlier timer deadline.
        if (!TimerManager.isTriggerDue(context)) return

        if (FocusManager.isEnabled(context)) {
            FocusManager.recordCompletedSession(context, TimerManager.getWorkMinutes(context))
        }

        // End the work-focus session before showing the eye-break overlay.
        TimerManager.markBreakReady(context)

        val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true

        if (canDrawOverlays) {
            val serviceIntent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
