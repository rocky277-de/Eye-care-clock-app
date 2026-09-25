package com.example.eyecare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Foreground service for a movable, manually-started rest overlay. */
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
        private const val ACTION_START_REST = "com.example.eyecare.START_REST"
        private const val ACTION_SKIP_REST = "com.example.eyecare.SKIP_REST"
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_REST -> {
                if (!restStarted && overlayView != null) {
                    val countdown = overlayView?.findViewById<TextView>(R.id.overlayCountdown)
                    val label = overlayView?.findViewById<TextView>(R.id.restDurationLabel)
                    val decrease = overlayView?.findViewById<Button>(R.id.decreaseRestButton)
                    val increase = overlayView?.findViewById<Button>(R.id.increaseRestButton)
                    val start = overlayView?.findViewById<Button>(R.id.startRestButton)
                    startRest(countdown, label, decrease, increase, start)
                }
            }
            ACTION_SKIP_REST -> finishBreak(skipped = true)
        }
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Eye Care Reminders",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Eye break time")
            .setContentText("Rest timer is waiting to start")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(
                0,
                "Start Rest",
                serviceAction(ACTION_START_REST, 3001)
            )
            .addAction(
                0,
                "Skip",
                serviceAction(ACTION_SKIP_REST, 3002)
            )
            .build()
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, OverlayService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun updateNotification(title: String, text: String, includeActions: Boolean) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (includeActions) {
            builder.addAction(0, "Start Rest", serviceAction(ACTION_START_REST, 3001))
                .addAction(0, "Skip", serviceAction(ACTION_SKIP_REST, 3002))
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, builder.build())
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

        val displayMetrics = resources.displayMetrics
        val savedX = getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE)
            .getInt(PREF_OVERLAY_X, 0)
        val savedY = getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE)
            .getInt(PREF_OVERLAY_Y, 180)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX.coerceIn(0, displayMetrics.widthPixels - 80)
            y = savedY.coerceIn(0, displayMetrics.heightPixels - 120)
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (_: WindowManager.BadTokenException) {
            stopSelf()
            return
        }

        val countdownText = overlayView?.findViewById<TextView>(R.id.overlayCountdown)
        val durationLabel = overlayView?.findViewById<TextView>(R.id.restDurationLabel)
        val decrease = overlayView?.findViewById<Button>(R.id.decreaseRestButton)
        val increase = overlayView?.findViewById<Button>(R.id.increaseRestButton)
        val startRest = overlayView?.findViewById<Button>(R.id.startRestButton)
        val skipRest = overlayView?.findViewById<Button>(R.id.overlayDismissButton)
        val dragRoot = overlayView?.findViewById<View>(R.id.overlayRoot)

        restSeconds = TimerManager.getRestSeconds(this)
        updateDurationUi(countdownText, durationLabel)

        decrease?.setOnClickListener {
            if (!restStarted) {
                restSeconds = (restSeconds - 5).coerceAtLeast(TimerManager.MIN_REST_SECONDS)
                updateDurationUi(countdownText, durationLabel)
                saveRestDuration()
            }
        }
        increase?.setOnClickListener {
            if (!restStarted) {
                restSeconds = (restSeconds + 5).coerceAtMost(TimerManager.MAX_REST_SECONDS)
                updateDurationUi(countdownText, durationLabel)
                saveRestDuration()
            }
        }
        startRest?.setOnClickListener {
            if (!restStarted) startRest(countdownText, durationLabel, decrease, increase, startRest)
        }
        skipRest?.setOnClickListener { finishBreak(skipped = true) }

        installDragListener(dragRoot)
    }

    private fun updateDurationUi(countdownText: TextView?, durationLabel: TextView?) {
        countdownText?.text = restSeconds.toString()
        durationLabel?.text = "$restSeconds sec"
    }

    private fun installDragListener(dragView: View?) {
        dragView?.setOnTouchListener(object : View.OnTouchListener {
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
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val metrics = resources.displayMetrics
                        p.x = (startX + event.rawX - downX).toInt()
                            .coerceIn(0, (metrics.widthPixels - 100).coerceAtLeast(0))
                        p.y = (startY + event.rawY - downY).toInt()
                            .coerceIn(0, (metrics.heightPixels - 140).coerceAtLeast(0))
                        try {
                            windowManager?.updateViewLayout(overlayView, p)
                        } catch (_: IllegalArgumentException) {
                            return false
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        savePosition()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun startRest(
        countdownText: TextView?,
        durationLabel: TextView?,
        decrease: Button?,
        increase: Button?,
        startButton: Button?
    ) {
        restStarted = true
        updateNotification("Rest in progress", "Eye break: $restSeconds seconds remaining", false)
        decrease?.isEnabled = false
        increase?.isEnabled = false
        startButton?.isEnabled = false
        startButton?.text = "Resting..."
        durationLabel?.text = "Rest in progress"
        countdownText?.text = restSeconds.toString()

        countdownTimer = object : CountDownTimer(restSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = ((millisUntilFinished + 999L) / 1000L)
                countdownText?.text = seconds.toString()
                updateNotification("Rest in progress", "Eye break: $seconds seconds remaining", false)
            }

            override fun onFinish() {
                countdownText?.text = "0"
                finishBreak(skipped = false)
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

    private fun finishBreak(skipped: Boolean) {
        if (completed) return
        completed = true
        countdownTimer?.cancel()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)

        val prefs = getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val completedKey = "stats_completed_$today"
        val skippedKey = "stats_skipped_$today"
        val editor = prefs.edit()
        if (skipped) {
            editor.putInt(skippedKey, prefs.getInt(skippedKey, 0) + 1)
        } else {
            editor.putInt(completedKey, prefs.getInt(completedKey, 0) + 1)
            editor.putInt("breaks_completed", prefs.getInt("breaks_completed", 0) + 1)
        }
        editor.apply()

        TimerManager.rescheduleNext(this)
        sendBroadcast(
            Intent(ACTION_BREAK_FINISHED)
                .setPackage(packageName)
                .putExtra("skipped", skipped)
        )
        stopSelf()
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        super.onDestroy()
    }
}
