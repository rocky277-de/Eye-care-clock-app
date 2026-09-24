package com.example.eyecare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the reminder after a phone reboot if it was running. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && EyeStore.running(context)) {
            TimerManager.start(context, EyeStore.workMin(context) * 60_000L)
        }
    }
}
