package com.example.eyecare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!TimerManager.isRunning(context)) return
        if (TimerManager.isWorkPhase(context)) {
            val remaining = TimerManager.getRemainingMs(context)
            TimerManager.startTimer(context, if (remaining > 0L) remaining else 1000L)
        }
    }
}
