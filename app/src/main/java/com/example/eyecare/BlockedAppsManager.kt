package com.example.eyecare

import android.content.Context

object BlockedAppsManager {
    private const val PREF_KEY = "focus_blocked_apps"
    private const val PREF_ATTEMPTS_PREFIX = "focus_block_attempts_"
    private const val PREF_APP_ATTEMPTS_PREFIX = "focus_block_app_attempts_"

    fun getBlockedPackages(context: Context): Set<String> {
        return context.getSharedPreferences(TimerManager.PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(PREF_KEY, emptySet())?.toSet() ?: emptySet()
    }

    fun setBlockedPackages(context: Context, packages: Set<String>) {
        context.getSharedPreferences(TimerManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PREF_KEY, packages.filter { it != context.packageName }.toSet())
            .apply()
    }

    fun isBlocked(context: Context, packageName: String): Boolean =
        packageName != context.packageName &&
            TimerManager.isFocusActive(context) &&
            getBlockedPackages(context).contains(packageName)

    fun recordBlockedAttempt(context: Context, packageName: String? = null) {
        val key = PREF_ATTEMPTS_PREFIX + todayKey()
        val prefs = context.getSharedPreferences(TimerManager.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putInt(key, prefs.getInt(key, 0) + 1)
        if (!packageName.isNullOrBlank()) {
            val appKey = PREF_APP_ATTEMPTS_PREFIX + todayKey() + "_" + packageName
            editor.putInt(appKey, prefs.getInt(appKey, 0) + 1)
        }
        editor.apply()
    }

    fun getTodayAttempts(context: Context): Int =
        context.getSharedPreferences(TimerManager.PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_ATTEMPTS_PREFIX + todayKey(), 0)

    fun getAttempts(context: Context, daysAgo: Int): Int =
        context.getSharedPreferences(TimerManager.PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_ATTEMPTS_PREFIX + dayKey(daysAgo), 0)

    fun getSevenDayAttempts(context: Context): Int =
        (0..6).sumOf { getAttempts(context, it) }

    fun getThirtyDayAttempts(context: Context): Int =
        (0..29).sumOf { getAttempts(context, it) }

    fun getAppAttempts(context: Context, packageName: String, days: Int = 7): Int {
        val prefs = context.getSharedPreferences(TimerManager.PREFS_NAME, Context.MODE_PRIVATE)
        val end = (days - 1).coerceAtLeast(0)
        return (0..end).sumOf { day ->
            prefs.getInt(PREF_APP_ATTEMPTS_PREFIX + dayKey(day) + "_" + packageName, 0)
        }
    }

    fun getMostDistractingApp(context: Context, days: Int = 7): Pair<String, Int>? {
        return getBlockedPackages(context)
            .map { it to getAppAttempts(context, it, days) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
    }

    private fun todayKey(): String = dayKey(0)

    private fun dayKey(daysAgo: Int): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calendar.time)
    }
}
