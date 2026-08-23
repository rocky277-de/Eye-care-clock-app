package com.example.eyecare

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var countdownText: TextView
    private lateinit var statusText: TextView
    private lateinit var sessionText: TextView
    private lateinit var startPauseButton: Button
    private lateinit var resetButton: Button
    private lateinit var prefs: SharedPreferences

    private var countDownTimer: CountDownTimer? = null
    private var timeLeftMs: Long = INTERVAL_MS
    private var isRunning = false
    private var breaksCompleted = 0

    companion object {
        private const val INTERVAL_MS = 20 * 60 * 1000L // 20 minutes
        private const val PREFS_NAME = "eyecare_prefs"
        private const val PREF_BREAKS = "breaks_completed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        countdownText = findViewById(R.id.countdownText)
        statusText = findViewById(R.id.statusText)
        sessionText = findViewById(R.id.sessionText)
        startPauseButton = findViewById(R.id.startPauseButton)
        resetButton = findViewById(R.id.resetButton)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        breaksCompleted = prefs.getInt(PREF_BREAKS, 0)
        updateSessionText()
        updateCountdownDisplay()

        startPauseButton.setOnClickListener {
            if (isRunning) pauseTimer() else startTimer()
        }

        resetButton.setOnClickListener {
            resetTimer()
        }
    }

    private fun requestOverlayPermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please allow 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun startTimer() {
        if (!requestOverlayPermissionIfNeeded()) return

        isRunning = true
        statusText.text = "Reminder running"
        startPauseButton.text = "Pause"

        countDownTimer = object : CountDownTimer(timeLeftMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftMs = millisUntilFinished
                updateCountdownDisplay()
            }

            override fun onFinish() {
                breaksCompleted++
                prefs.edit().putInt(PREF_BREAKS, breaksCompleted).apply()
                updateSessionText()

                val serviceIntent = Intent(this@MainActivity, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                timeLeftMs = INTERVAL_MS
                updateCountdownDisplay()
                startTimer()
            }
        }.start()

        TimerManager.startTimer(this)
    }

    private fun pauseTimer() {
        isRunning = false
        countDownTimer?.cancel()
        statusText.text = "Reminder paused"
        startPauseButton.text = "Start"
        TimerManager.stopTimer(this)
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        isRunning = false
        timeLeftMs = INTERVAL_MS
        statusText.text = "Reminder paused"
        startPauseButton.text = "Start"
        updateCountdownDisplay()
        TimerManager.stopTimer(this)
    }

    private fun updateCountdownDisplay() {
        val totalSeconds = timeLeftMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        countdownText.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateSessionText() {
        sessionText.text = "Breaks completed: $breaksCompleted"
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
