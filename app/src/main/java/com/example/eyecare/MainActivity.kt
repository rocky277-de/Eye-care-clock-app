package com.example.eyecare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var countdownText: TextView
    private lateinit var statusText: TextView
    private lateinit var sessionText: TextView
    private lateinit var startPauseButton: Button
    private lateinit var resetButton: Button
    private lateinit var defaultsButton: Button
    private lateinit var workInput: EditText
    private lateinit var breakInput: EditText

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        countdownText = findViewById(R.id.countdownText)
        statusText = findViewById(R.id.statusText)
        sessionText = findViewById(R.id.sessionText)
        startPauseButton = findViewById(R.id.startPauseButton)
        resetButton = findViewById(R.id.resetButton)
        defaultsButton = findViewById(R.id.defaultsButton)
        workInput = findViewById(R.id.workInput)
        breakInput = findViewById(R.id.breakInput)

        workInput.setText(EyeStore.workMin(this).toString())
        breakInput.setText(EyeStore.breakSec(this).toString())

        startPauseButton.setOnClickListener { if (EyeStore.running(this)) pause() else start() }
        resetButton.setOnClickListener { reset() }
        defaultsButton.setOnClickListener {
            workInput.setText(EyeStore.DEFAULT_WORK_MIN.toString())
            breakInput.setText(EyeStore.DEFAULT_BREAK_SEC.toString())
        }
    }

    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(ticker) }

    private fun start() {
        val work = workInput.text.toString().toIntOrNull()
        val brk = breakInput.text.toString().toIntOrNull()
        if (work == null || work !in 1..180 || brk == null || brk !in 5..300) {
            Toast.makeText(this, "Work: 1-180 min, Break: 5-300 sec", Toast.LENGTH_LONG).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        // Resume paused time only if the work duration was not changed.
        val delay = if (EyeStore.remaining(this) > 0 && EyeStore.workMin(this) == work)
            EyeStore.remaining(this) else work * 60_000L
        EyeStore.edit(this) {
            putInt("work_min", work); putInt("break_sec", brk)
            putLong("remaining", 0L); putLong("break_until", 0L)
        }
        TimerManager.start(this, delay)
        render()
    }

    private fun pause() {
        val now = System.currentTimeMillis()
        val breakLeft = (EyeStore.breakUntil(this) - now).coerceAtLeast(0)
        val workLeft = (EyeStore.nextAt(this) - now - breakLeft).coerceAtLeast(0)
        TimerManager.stop(this)
        EyeStore.edit(this) { putLong("remaining", workLeft) }
        render()
    }

    private fun reset() {
        TimerManager.stop(this)
        EyeStore.edit(this) { putLong("remaining", 0L); putLong("break_until", 0L) }
        render()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val running = EyeStore.running(this)
        val breakLeft = EyeStore.breakUntil(this) - now
        val rem = EyeStore.remaining(this)
        val typed = workInput.text.toString().toIntOrNull()?.coerceIn(1, 180) ?: EyeStore.workMin(this)

        val (ms, status) = when {
            running && breakLeft > 0 -> breakLeft to "Break time - look 20 feet away"
            running -> (EyeStore.nextAt(this) - now) to "Reminder running"
            rem > 0 -> rem to "Reminder paused"
            else -> typed * 60_000L to "Ready"
        }
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        countdownText.text = String.format("%02d:%02d", s / 60, s % 60)
        statusText.text = status
        sessionText.text = "Breaks completed: ${EyeStore.breaks(this)}"
        startPauseButton.text = if (running) "Pause" else "Start"
        workInput.isEnabled = !running
        breakInput.isEnabled = !running
        defaultsButton.isEnabled = !running
    }
}
