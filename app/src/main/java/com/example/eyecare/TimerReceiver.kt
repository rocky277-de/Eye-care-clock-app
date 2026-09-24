package com.example.eyecare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!EyeStore.running(context)) return
        TimerManager.onFire(context)
        if (!Settings.canDrawOverlays(context)) return
        try {
            val i = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        } catch (e: Exception) {
            // Background start blocked by the OS; next cycle is already scheduled.
        }
    }
}
