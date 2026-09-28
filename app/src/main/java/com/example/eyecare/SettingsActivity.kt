package com.example.eyecare

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.Build
import android.provider.Settings
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        workPicker = findViewById(R.id.settingsWorkPicker)
        restPicker = findViewById(R.id.settingsRestPicker)
        focusGoalPicker = findViewById(R.id.focusGoalPicker)
        longBreakPicker = findViewById(R.id.longBreakPicker)

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

        findViewById<Switch>(R.id.focusModeSwitch).apply {
            isChecked = FocusManager.isEnabled(this@SettingsActivity)
            setOnCheckedChangeListener { _, checked ->
                FocusManager.setEnabled(this@SettingsActivity, checked)
            }
        }

        findViewById<Button>(R.id.saveSettingsButton).setOnClickListener {
            TimerManager.saveSettings(this, workPicker.value, restPicker.value)
            FocusManager.setGoalMinutes(this, focusGoalPicker.value)
            FocusManager.setLongBreakMinutes(this, longBreakPicker.value)
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
            TimerManager.resetTimerAndStatistics(this)
            Toast.makeText(this, "Timer and statistics reset", Toast.LENGTH_SHORT).show()
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
            showAccessibilityDisclosure()
        }

        findViewById<Button>(R.id.selectBlockedAppsButton).setOnClickListener {
            showBlockedAppsPicker()
        }

        updateBlockedAppsSummary()
        updateBatteryOptimizationStatus()
        updateExactAlarmStatus()
        updateNotificationStatus()

        findViewById<Button>(R.id.batteryOptimizationButton).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        findViewById<Button>(R.id.exactAlarmButton).setOnClickListener {
            openExactAlarmSettings()
        }

        findViewById<Button>(R.id.notificationSettingsButton).setOnClickListener {
            openNotificationSettings()
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


    private fun showAccessibilityDisclosure() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.accessibility_disclosure_title))
            .setMessage(getString(R.string.accessibility_disclosure_message))
            .setNegativeButton(getString(R.string.accessibility_disclosure_cancel), null)
            .setPositiveButton(getString(R.string.accessibility_disclosure_accept)) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }

    private fun updateExactAlarmStatus() {
        val status = findViewById<android.widget.TextView>(R.id.exactAlarmStatus)
        val allowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else true
        status.text = if (allowed) {
            "Exact alarms: allowed"
        } else {
            "Exact alarms: not allowed — Android may delay reminders"
        }
    }

    private fun openExactAlarmSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            } else {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun updateNotificationStatus() {
        val enabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .areNotificationsEnabled()
        } else true
        findViewById<android.widget.TextView>(R.id.notificationStatus).text =
            if (enabled) "Notifications: enabled" else
                "Notifications: disabled — break updates may be hidden"
    }

    private fun openNotificationSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun updateBatteryOptimizationStatus() {
        val manager = getSystemService(POWER_SERVICE) as PowerManager
        val exempt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                manager.isIgnoringBatteryOptimizations(packageName)
            } catch (_: SecurityException) {
                false
            }
        } else true
        findViewById<android.widget.TextView>(R.id.batteryOptimizationStatus).text =
            if (exempt) "Background timer protection: allowed" else
                "Background timer protection: battery optimization is active"
    }

    private fun updateBlockedAppsSummary() {
        val count = BlockedAppsManager.getBlockedPackages(this).size
        findViewById<android.widget.TextView>(R.id.blockedAppsSummary).text =
            if (count == 0) "No apps selected" else "$count app(s) selected"
    }

    private fun showBlockedAppsPicker() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL
        )
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val names = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        val packages = apps.map { it.packageName }
        val selected = BlockedAppsManager.getBlockedPackages(this)

        val checked = BooleanArray(packages.size) { selected.contains(packages[it]) }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Blocked Apps during Focus")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                BlockedAppsManager.setBlockedPackages(
                    this,
                    packages.indices.filter { checked[it] }.map { packages[it] }.toSet()
                )
                updateBlockedAppsSummary()
                Toast.makeText(this, "Blocked apps saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::workPicker.isInitialized) {
            updateBlockedAppsSummary()
            updateBatteryOptimizationStatus()
            updateExactAlarmStatus()
            updateNotificationStatus()
        }
    }
}

