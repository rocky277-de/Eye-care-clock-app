package com.example.eyecare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (!TimerManager.isRunning(context)) return
        if (TimerManager.isWorkPhase(context)) {
            // Reboot downtime is never counted as focus time.
            TimerManager.recoverFocusAfterBoot(context)
            val remaining = TimerManager.getRemainingMs(context)
            TimerManager.startTimer(context, if (remaining > 0L) remaining else 1000L)
        }
    }
}
