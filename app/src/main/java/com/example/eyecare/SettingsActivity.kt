package com.example.eyecare

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.Button
import android.widget.NumberPicker
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var workPicker: NumberPicker
    private lateinit var restPicker: NumberPicker
    private lateinit var focusGoalPicker: NumberPicker
    private lateinit var longBreakPicker: NumberPicker
    private lateinit var focusGoalPicker: NumberPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        workPicker = findViewById(R.id.settingsWorkPicker)
        restPicker = findViewById(R.id.settingsRestPicker)
        focusGoalPicker = findViewById(R.id.focusGoalPicker)
        longBreakPicker = findViewById(R.id.longBreakPicker)
        focusGoalPicker = findViewById(R.id.focusGoalPicker)

        workPicker.minValue = TimerManager.MIN_WORK_MINUTES
        workPicker.maxValue = TimerManager.MAX_WORK_MINUTES
        workPicker.value = TimerManager.getWorkMinutes(this)

        restPicker.minValue = TimerManager.MIN_REST_SECONDS
        restPicker.maxValue = TimerManager.MAX_REST_SECONDS
        restPicker.value = TimerManager.getRestSeconds(this)

        focusGoalPicker.minValue = FocusManager.MIN_GOAL_MINUTES
        focusGoalPicker.maxValue = FocusManager.MAX_GOAL_MINUTES
        focusGoalPicker.value = FocusManager.getGoalMinutes(this)
        focusGoalPicker.wrapSelectorWheel = false

        longBreakPicker.minValue = FocusManager.MIN_LONG_BREAK_MINUTES
        longBreakPicker.maxValue = FocusManager.MAX_LONG_BREAK_MINUTES
        longBreakPicker.value = FocusManager.getLongBreakMinutes(this)
        longBreakPicker.wrapSelectorWheel = false

        focusGoalPicker.minValue = TimerManager.MIN_FOCUS_GOAL_MINUTES
        focusGoalPicker.maxValue = TimerManager.MAX_FOCUS_GOAL_MINUTES
        focusGoalPicker.value = TimerManager.getFocusGoalMinutes(this)

        findViewById<Switch>(R.id.focusModeSwitch).apply {
            isChecked = FocusManager.isEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked -> FocusManager.setEnabled(this@SettingsActivity, checked) }
        }

        findViewById<Button>(R.id.saveSettingsButton).setOnClickListener {
            TimerManager.saveSettings(this, workPicker.value, restPicker.value)
            FocusManager.setGoalMinutes(this, focusGoalPicker.value)
            FocusManager.setLongBreakMinutes(this, longBreakPicker.value)
            TimerManager.saveFocusGoalMinutes(this, focusGoalPicker.value)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.preset2020Button).setOnClickListener {
            workPicker.value = 20
            restPicker.value = 20
            focusGoalPicker.value = FocusManager.DEFAULT_GOAL_MINUTES
            longBreakPicker.value = FocusManager.DEFAULT_LONG_BREAK_MINUTES
        }

        findViewById<Button>(R.id.resetStatsButton).setOnClickListener {
            getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE).edit()
                .clear()
                .apply()
            Toast.makeText(this, "Timer settings and statistics reset", Toast.LENGTH_SHORT).show()
        }

        findViewById<Switch>(R.id.blockYoutubeShortsSwitch).apply {
            isChecked = getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE)
                .getBoolean("block_youtube_shorts", false)
            setOnCheckedChangeListener { _, checked ->
                getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean("block_youtube_shorts", checked)
                    .apply()
            }
        }

        findViewById<Button>(R.id.enableShortsBlockerButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
