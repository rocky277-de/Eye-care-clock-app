package com.example.eyecare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var countdownText: TextView
    private lateinit var statusText: TextView
    private lateinit var sessionText: TextView
    private lateinit var startPauseButton: Button
    private lateinit var resetButton: Button
    private lateinit var workPicker: NumberPicker
    private lateinit var restPicker: NumberPicker
    private lateinit var prefs: SharedPreferences

    private var countDownTimer: CountDownTimer? = null
    private var timeLeftMs = 20 * 60 * 1000L
    private var isRunning = false
    private var breaksCompleted = 0
    private var statsText: TextView? = null

    companion object {
        private const val PREF_BREAKS = "breaks_completed"
    }

    private val breakFinishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == OverlayService.ACTION_BREAK_FINISHED) {
                breaksCompleted = prefs.getInt(PREF_BREAKS, 0)
                updateSessionText()
                updateStats()
                if (TimerManager.isRunning(this@MainActivity)) {
                    isRunning = true
                    statusText.text = "Reminder running"
                    startPauseButton.text = "Pause"
                    timeLeftMs = TimerManager.getWorkMinutes(this@MainActivity) * 60_000L
                    startLocalCountdown()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        countdownText = findViewById(R.id.countdownText)
        statusText = findViewById(R.id.statusText)
        sessionText = findViewById(R.id.sessionText)
        statsText = findViewById(R.id.statsText)
        startPauseButton = findViewById(R.id.startPauseButton)
        resetButton = findViewById(R.id.resetButton)
        workPicker = findViewById(R.id.workMinutesPicker)
        restPicker = findViewById(R.id.restSecondsPicker)

        prefs = getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE)
        configurePickers()
        breaksCompleted = prefs.getInt(PREF_BREAKS, 0)
        updateSessionText()
        updateStats()
        restoreTimerState()

        startPauseButton.setOnClickListener {
            if (isRunning) pauseTimer() else startTimer()
        }
        resetButton.setOnClickListener { resetTimer() }
    }

    private fun configurePickers() {
        workPicker.minValue = TimerManager.MIN_WORK_MINUTES
        workPicker.maxValue = TimerManager.MAX_WORK_MINUTES
        workPicker.value = TimerManager.getWorkMinutes(this)

        restPicker.minValue = TimerManager.MIN_REST_SECONDS
        restPicker.maxValue = TimerManager.MAX_REST_SECONDS
        restPicker.value = TimerManager.getRestSeconds(this)

        val save = {
            TimerManager.saveSettings(this, workPicker.value, restPicker.value)
            if (!isRunning) resetLocalCountdown()
            Toast.makeText(this, "Timer settings saved", Toast.LENGTH_SHORT).show()
        }
        workPicker.setOnValueChangedListener { _, _, _ -> save() }
        restPicker.setOnValueChangedListener { _, _, _ -> save() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 5001)
        }
    }

    private fun requestOverlayPermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Please allow 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun startTimer() {
        requestNotificationPermissionIfNeeded()
        if (!requestOverlayPermissionIfNeeded()) return
        TimerManager.saveSettings(this, workPicker.value, restPicker.value)
        val saved = TimerManager.getRemainingMs(this)
        timeLeftMs = if (!TimerManager.isRunning(this) && saved in 1 until workPicker.value * 60_000L) saved else workPicker.value * 60_000L
        isRunning = true
        statusText.text = "Reminder running"
        startPauseButton.text = "Pause"
        startLocalCountdown()
        TimerManager.startTimer(this)
    }

    private fun startLocalCountdown() {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(timeLeftMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMs = millisUntilFinished
                updateCountdownDisplay()
            }
            override fun onFinish() {
                timeLeftMs = TimerManager.getWorkMinutes(this@MainActivity) * 60_000L
                updateCountdownDisplay()
                statusText.text = "Break overlay ready"
            }
        }.start()
    }

    private fun pauseTimer() {
        isRunning = false
        countDownTimer?.cancel()
        statusText.text = "Reminder paused"
        startPauseButton.text = "Start"
        TimerManager.pauseTimer(this, timeLeftMs)
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        isRunning = false
        timeLeftMs = TimerManager.getWorkMinutes(this) * 60_000L
        statusText.text = "Reminder paused"
        startPauseButton.text = "Start"
        updateCountdownDisplay()
        TimerManager.stopTimer(this)
    }

    private fun restoreTimerState() {
        if (!TimerManager.isRunning(this)) {
            isRunning = false
            timeLeftMs = TimerManager.getRemainingMs(this).takeIf { it > 0L } ?: TimerManager.getWorkMinutes(this) * 60_000L
            statusText.text = "Reminder paused"
            startPauseButton.text = "Start"
            updateCountdownDisplay()
            return
        }
        if (!TimerManager.isWorkPhase(this)) {
            isRunning = true
            statusText.text = "Break overlay ready"
            startPauseButton.text = "Pause"
            timeLeftMs = 0L
            updateCountdownDisplay()
            return
        }
        timeLeftMs = TimerManager.getRemainingMs(this).coerceAtLeast(1000L)
        isRunning = true
        statusText.text = "Reminder running"
        startPauseButton.text = "Pause"
        startLocalCountdown()
    }

    private fun resetLocalCountdown() {
        timeLeftMs = TimerManager.getWorkMinutes(this) * 60_000L
        updateCountdownDisplay()
    }

    private fun updateCountdownDisplay() {
        val totalSeconds = timeLeftMs / 1000
        countdownText.text = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun updateSessionText() {
        sessionText.text = "Breaks completed: $breaksCompleted"
    }

    private fun dateKey(daysAgo: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    private fun completedForDay(daysAgo: Int): Int =
        prefs.getInt("stats_completed_${dateKey(daysAgo)}", 0)

    private fun skippedForDay(daysAgo: Int): Int =
        prefs.getInt("stats_skipped_${dateKey(daysAgo)}", 0)

    private fun updateStats() {
        if (!::prefs.isInitialized || statsText == null) return
        val todayCompleted = completedForDay(0)
        val todaySkipped = skippedForDay(0)
        val totalCompleted = (0..6).sumOf { completedForDay(it) }
        val totalSkipped = (0..6).sumOf { skippedForDay(it) }

        var streak = 0
        for (day in 0..6) {
            if (completedForDay(day) > 0) streak++ else break
        }

        val labels = arrayOf("Today", "Yesterday", "2d ago", "3d ago", "4d ago", "5d ago", "6d ago")
        val history = (0..6).joinToString("  •  ") { day ->
            "${labels[day]}: ${completedForDay(day)}✓/${skippedForDay(day)}×"
        }

        val dayWord = if (streak == 1) "day" else "days"
        statsText?.text = "TODAY\nCompleted: $todayCompleted   Skipped: $todaySkipped\n\n7-DAY TOTAL\nCompleted: $totalCompleted   Skipped: $totalSkipped\nStreak: $streak $dayWord\n\n$history"
    }


    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(OverlayService.ACTION_BREAK_FINISHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(breakFinishedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(breakFinishedReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized && !isRunning) restoreTimerState()
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(breakFinishedReceiver) } catch (_: IllegalArgumentException) { }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }
}
