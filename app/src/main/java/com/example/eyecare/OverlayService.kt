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

/** Foreground service for the movable, manually-started rest overlay. */
class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var countdownTimer: CountDownTimer? = null
    private var restSeconds = TimerManager.DEFAULT_REST_SECONDS
    private var restStarted = false
    private var completed = false
    private var params: WindowManager.LayoutParams? = null

    companion object {
        private const val CHANNEL_ID = "eyecare_overlay_channel"
        private const val NOTIFICATION_ID = 2001
        private const val PREF_OVERLAY_X = "overlay_x"
        private const val PREF_OVERLAY_Y = "overlay_y"
        const val ACTION_BREAK_FINISHED = "com.example.eyecare.BREAK_FINISHED"
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Eye Care Reminders", NotificationManager.IMPORTANCE_LOW)
            )
        }
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Eye break time")
            .setContentText("Rest timer is waiting to start")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun showOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_floating_overlay, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val p = getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE)
            x = p.getInt(PREF_OVERLAY_X, 0)
            y = p.getInt(PREF_OVERLAY_Y, 180)
        }

        windowManager?.addView(overlayView, params)

        val countdownText = overlayView?.findViewById<TextView>(R.id.overlayCountdown)
        val dragHandle = overlayView?.findViewById<View>(R.id.overlayDragHandle)
        val decrease = overlayView?.findViewById<View>(R.id.decreaseRestButton)
        val increase = overlayView?.findViewById<View>(R.id.increaseRestButton)
        val startRest = overlayView?.findViewById<View>(R.id.startRestButton)
        val skipRest = overlayView?.findViewById<View>(R.id.overlayDismissButton)

        restSeconds = TimerManager.getRestSeconds(this)
        countdownText?.text = restSeconds.toString()

        decrease?.setOnClickListener {
            if (!restStarted) {
                restSeconds = (restSeconds - 5).coerceAtLeast(TimerManager.MIN_REST_SECONDS)
                countdownText?.text = restSeconds.toString()
                saveRestDuration()
            }
        }
        increase?.setOnClickListener {
            if (!restStarted) {
                restSeconds = (restSeconds + 5).coerceAtMost(TimerManager.MAX_REST_SECONDS)
                countdownText?.text = restSeconds.toString()
                saveRestDuration()
            }
        }
        startRest?.setOnClickListener {
            if (!restStarted) startRest(countdownText)
        }
        skipRest?.setOnClickListener { finishBreak() }

        dragHandle?.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val p = params ?: return false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = p.x
                        startY = p.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        p.x = startX + (event.rawX - downX).toInt()
                        p.y = startY + (event.rawY - downY).toInt()
                        windowManager?.updateViewLayout(overlayView, p)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        savePosition()
                        true
                    }
                    else -> false
                }
            }
        })
    }

    private fun startRest(countdownText: TextView?) {
        restStarted = true
        countdownText?.text = restSeconds.toString()
        countdownTimer = object : CountDownTimer(restSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText?.text = ((millisUntilFinished + 999L) / 1000L).toString()
            }
            override fun onFinish() {
                finishBreak()
            }
        }.start()
    }

    private fun saveRestDuration() {
        TimerManager.saveSettings(this, TimerManager.getWorkMinutes(this), restSeconds)
    }

    private fun savePosition() {
        val p = params ?: return
        getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE).edit()
            .putInt(PREF_OVERLAY_X, p.x)
            .putInt(PREF_OVERLAY_Y, p.y)
            .apply()
    }

    private fun finishBreak() {
        if (completed) return
        completed = true
        countdownTimer?.cancel()
        val prefs = getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE)
        val count = prefs.getInt("breaks_completed", 0) + 1
        prefs.edit().putInt("breaks_completed", count).apply()
        TimerManager.rescheduleNext(this)
        sendBroadcast(Intent(ACTION_BREAK_FINISHED).setPackage(packageName))
        stopSelf()
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: IllegalArgumentException) { }
        }
        super.onDestroy()
    }
}
