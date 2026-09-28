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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Switch
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
    private lateinit var stopButton: Button
    private lateinit var focusModeSwitch: Switch
    private lateinit var focusStatsText: TextView
    private lateinit var focusHistoryContainer: LinearLayout
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
        stopButton = findViewById(R.id.stopButton)
        focusModeSwitch = findViewById(R.id.focusModeSwitch)
        focusStatsText = findViewById(R.id.focusStatsText)
        focusHistoryContainer = findViewById(R.id.focusHistoryContainer)
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        workPicker = findViewById(R.id.workMinutesPicker)
        restPicker = findViewById(R.id.restSecondsPicker)

        prefs = getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE)
        configurePickers()
        breaksCompleted = prefs.getInt(PREF_BREAKS, 0)
        updateSessionText()
        updateStats()
        updateFocusUi()
        restoreTimerState()

        startPauseButton.setOnClickListener {
            if (isRunning) pauseTimer() else startTimer()
        }
        resetButton.setOnClickListener { resetTimer() }
        stopButton.setOnClickListener { stopTimerCompletely() }

        focusModeSwitch.isChecked = TimerManager.isFocusModeEnabled(this)
        focusModeSwitch.setOnCheckedChangeListener { _, enabled ->
            TimerManager.setFocusModeEnabled(this, enabled)
            updateFocusUi()
        }
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
        updateFocusUi()
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
        updateFocusUi()
    }

    private fun stopTimerCompletely() {
        countDownTimer?.cancel()
        countDownTimer = null
        isRunning = false
        timeLeftMs = TimerManager.getWorkMinutes(this) * 60_000L
        statusText.text = "Reminder stopped"
        startPauseButton.text = "Start"
        updateCountdownDisplay()
        TimerManager.stopTimer(this)
        stopService(Intent(this, OverlayService::class.java))
        updateFocusUi()
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        isRunning = false
        timeLeftMs = TimerManager.getWorkMinutes(this) * 60_000L
        statusText.text = "Reminder paused"
        startPauseButton.text = "Start"
        updateCountdownDisplay()
        TimerManager.stopTimer(this)
        updateFocusUi()
    }

    private fun updateFocusUi() {
        if (!::focusStatsText.isInitialized) return

        val minutes = TimerManager.getTodayFocusMinutes(this)
        val goal = TimerManager.getFocusGoalMinutes(this)
        val percent = ((minutes.toFloat() / goal.toFloat()) * 100f).toInt().coerceIn(0, 100)
        val blockedToday = BlockedAppsManager.getTodayAttempts(this)
        val blockedWeek = BlockedAppsManager.getSevenDayAttempts(this)
        val blockedMonth = BlockedAppsManager.getThirtyDayAttempts(this)
        val weekMinutes = FocusManager.getSevenDayMinutes(this)
        val weekSessions = FocusManager.getSevenDaySessions(this)
        val monthMinutes = FocusManager.getThirtyDayMinutes(this)
        val monthSessions = FocusManager.getThirtyDaySessions(this)
        val avgDaily = FocusManager.getAverageDailyMinutes(this)
        val avgSession = FocusManager.getAverageSessionMinutes(this)
        val longestStreak = FocusManager.getLongestFocusStreak(this)
        val goalDays = FocusManager.getGoalCompletionDays(this, 7)
        val bestDay = FocusManager.getBestFocusDay(this, 30)
        val previousWeekMinutes = FocusManager.getMinutesForRange(this, 7, 13)
        val trendPercent = if (previousWeekMinutes > 0) ((weekMinutes - previousWeekMinutes).toFloat() / previousWeekMinutes.toFloat()) * 100f else if (weekMinutes > 0) 100f else 0f
        val trendLabel = when {
            weekMinutes == 0 && previousWeekMinutes == 0 -> "No data"
            trendPercent > 0.5f -> "Up " + String.format(Locale.US, "%.0f", trendPercent) + "%"
            trendPercent < -0.5f -> "Down " + String.format(Locale.US, "%.0f", kotlin.math.abs(trendPercent)) + "%"
            else -> "Stable"
        }
        val distractionRate = if (weekMinutes > 0) blockedWeek * 60f / weekMinutes else 0f
        val mostDistracting = BlockedAppsManager.getMostDistractingApp(this, 7)
        val distractingLabel = mostDistracting?.let {
            val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it.first, 0)).toString() } catch (_: Exception) { it.first }
            name + " (" + it.second + " attempts)"
        } ?: "None"
        val bestDayLabel = when (bestDay.first) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> bestDay.first.toString() + "d ago"
        }
        focusStatsText.text = "FOCUS TODAY\n" +
            minutes.toString() + " min / " + goal + " min goal\nProgress: " + percent + "%\n" +
            "Sessions: " + FocusManager.getTodaySessions(this) + "\n\n" +
            "7-DAY INSIGHTS\n" +
            weekMinutes + " min • " + weekSessions + " sessions\n" +
            "Average: " + avgDaily + " min/day\n" +
            "Avg session: " + avgSession + " min\n" +
            "Goal reached: " + goalDays + "/7 days\n" +
            "Weekly trend: " + trendLabel + "\n" +
            "Longest active streak: " + longestStreak + " day(s)\n\n" +
            "30-DAY OVERVIEW\n" +
            monthMinutes + " min • " + monthSessions + " sessions\n" +
            "Best day: " + bestDayLabel + " (" + bestDay.second + " min)\n" +
            "Blocked attempts: " + blockedMonth + "\n\n" +
            "DISTRACTION INSIGHTS\n" +
            "Top blocked app: " + distractingLabel + "\n" +
            "Rate: " + String.format(Locale.US, "%.1f", distractionRate) + " attempts/hour\n" +
            "Today: " + blockedToday + " • 7-day: " + blockedWeek + "\n\n" +
            "SMART BREAKS\n" +
            "Completed today: " + FocusManager.getBreakCompleted(this, 0) + "\n" +
            "7-day total: " + FocusManager.getBreakTotal(this, 7) + "\n" +
            "Completion rate: " + FocusManager.getBreakCompletionRate(this, 7) + "%\n" +
            "Skip rate: " + FocusManager.getBreakSkipRate(this, 7) + "%\n" +
            "Days with completed breaks: " + FocusManager.getBreakCompletionDays(this, 7) + "/7\n" +
            "Missed before start: " + (0..6).sumOf { FocusManager.getBreakMissed(this, it) } + "\n" +
            "Recommendation: " + (if (FocusManager.shouldRecommendBreak(this)) "Take your next break when the timer ends." else "No break needed right now.") + "\n\n" +
            "ACHIEVEMENTS\n" +
            "Unlocked: " + FocusManager.getAchievementCount(this) + "\n" +
            "Current streak: " + FocusManager.getCurrentFocusStreak(this) + " day(s)\n" +
            "Best 30-day streak: " + FocusManager.getLongestFocusStreak30Days(this) + " day(s)\n" +
            "Next: " + FocusManager.getNextAchievement(this)

        updateFocusHistory(goal)
    }
    private fun updateFocusHistory(goal: Int) {
        if (!::focusHistoryContainer.isInitialized) return
        focusHistoryContainer.removeAllViews()
        val labels = arrayOf("Today", "Yesterday", "2d ago", "3d ago", "4d ago", "5d ago", "6d ago")
        val values = (0..6).map { FocusManager.getMinutesForDay(this, it) }
        val maxValue = maxOf(goal, values.maxOrNull() ?: 0, 1)
        values.forEachIndexed { index, value ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 4, 0, 4)
            }
            val label = TextView(this).apply {
                text = labels[index]
                setTextColor(android.graphics.Color.LTGRAY)
                textSize = 12f
            }
            row.addView(label, LinearLayout.LayoutParams(82, LinearLayout.LayoutParams.WRAP_CONTENT))
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = maxValue
                progress = value.coerceAtMost(maxValue)
            }
            row.addView(bar, LinearLayout.LayoutParams(0, 18, 1f).apply { setMargins(8, 0, 8, 0) })
            val valueText = TextView(this).apply {
                text = "\${value}m"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 12f
                gravity = android.view.Gravity.END
            }
            row.addView(valueText, LinearLayout.LayoutParams(42, LinearLayout.LayoutParams.WRAP_CONTENT))
            focusHistoryContainer.addView(row)
        }
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
        if (::prefs.isInitialized) updateFocusUi()
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
