package com.example.eyecare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

/** Foreground service: shows a draggable break overlay for the user-set break duration. */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var countdownTimer: CountDownTimer? = null

    companion object {
        private const val CHANNEL_ID = "eyecare_overlay_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val m = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            m.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Eye Care Reminders", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Eye break time")
            .setContentText("Look 20 feet away for ${EyeStore.breakSec(this)} seconds")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun showOverlay() {
        val breakSec = EyeStore.breakSec(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = LayoutInflater.from(this).inflate(R.layout.layout_floating_overlay, null)
        overlayView = view

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        windowManager?.addView(view, params)

        view.findViewById<TextView>(R.id.overlayMessage).text = "Look 20 feet away for $breakSec seconds"
        val countdownText = view.findViewById<TextView>(R.id.overlayCountdown)
        countdownText.text = breakSec.toString()
        view.findViewById<View>(R.id.overlayDismissButton).setOnClickListener { stopSelf() }

        // Drag to move
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(v, params)
                }
            }
            true
        }

        countdownTimer = object : CountDownTimer(breakSec * 1000L, 1000L) {
            override fun onTick(ms: Long) { countdownText.text = ((ms + 999) / 1000).toString() }
            override fun onFinish() { stopSelf() }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (e: IllegalArgumentException) { }
        }
    }
}
