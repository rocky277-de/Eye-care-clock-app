package com.example.eyecare

import android.content.Context

object BlockedAppsManager {
    private const val PREF_KEY = "focus_blocked_apps"

    fun getBlockedPackages(context: Context): Set<String> {
        return context.getSharedPreferences(TimerManager.PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(PREF_KEY, emptySet())?.toSet() ?: emptySet()
    }

    fun setBlockedPackages(context: Context, packages: Set<String>) {
        context.getSharedPreferences(TimerManager.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PREF_KEY, packages)
            .apply()
    }

    fun isBlocked(context: Context, packageName: String): Boolean =
        TimerManager.isFocusActive(context) && getBlockedPackages(context).contains(packageName)
}
