package com.example.eyecare

import android.os.Bundle
import android.widget.Button
import android.widget.NumberPicker
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var workPicker: NumberPicker
    private lateinit var restPicker: NumberPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        workPicker = findViewById(R.id.settingsWorkPicker)
        restPicker = findViewById(R.id.settingsRestPicker)

        workPicker.minValue = TimerManager.MIN_WORK_MINUTES
        workPicker.maxValue = TimerManager.MAX_WORK_MINUTES
        workPicker.value = TimerManager.getWorkMinutes(this)

        restPicker.minValue = TimerManager.MIN_REST_SECONDS
        restPicker.maxValue = TimerManager.MAX_REST_SECONDS
        restPicker.value = TimerManager.getRestSeconds(this)

        findViewById<Button>(R.id.saveSettingsButton).setOnClickListener {
            TimerManager.saveSettings(this, workPicker.value, restPicker.value)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.preset2020Button).setOnClickListener {
            workPicker.value = 20
            restPicker.value = 20
        }

        findViewById<Button>(R.id.resetStatsButton).setOnClickListener {
            getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE).edit()
                .clear()
                .apply()
            Toast.makeText(this, "Timer settings and statistics reset", Toast.LENGTH_SHORT).show()
        }

        findViewById<Switch>(R.id.keepOverlayPositionSwitch).apply {
            isChecked = getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE)
                .getBoolean("keep_overlay_position", true)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean("keep_overlay_position", checked)
                    .apply()
            }
        }
    }
}
