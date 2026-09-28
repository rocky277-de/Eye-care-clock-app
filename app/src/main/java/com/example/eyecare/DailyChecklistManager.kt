package com.example.eyecare

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DailyTask(
    val id: String,
    val title: String,
    val completed: Boolean
)

object DailyChecklistManager {
    private const val PREF_PREFIX = "daily_checklist_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(TimerManager.PREFS_NAME, Context.MODE_PRIVATE)

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun storageKey(daysAgo: Int = 0): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
        val key = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
        return PREF_PREFIX + key
    }

    fun getTodayTasks(context: Context): List<DailyTask> =
        readTasks(context, storageKey())

    fun addTask(context: Context, title: String): Boolean {
        val clean = title.trim()
        if (clean.isEmpty() || clean.length > 120) return false

        val tasks = getTodayTasks(context).toMutableList()
        tasks.add(DailyTask(UUID.randomUUID().toString(), clean, false))
        saveTasks(context, tasks)
        return true
    }

    fun setCompleted(context: Context, id: String, completed: Boolean) {
        val tasks = getTodayTasks(context).map {
            if (it.id == id) it.copy(completed = completed) else it
        }
        saveTasks(context, tasks)
    }

    fun deleteTask(context: Context, id: String) {
        saveTasks(context, getTodayTasks(context).filterNot { it.id == id })
    }

    fun clearCompleted(context: Context) {
        saveTasks(context, getTodayTasks(context).filterNot { it.completed })
    }

    fun clearToday(context: Context) {
        prefs(context).edit().remove(storageKey()).apply()
    }

    fun completedCount(context: Context): Int =
        getTodayTasks(context).count { it.completed }

    private fun saveTasks(context: Context, tasks: List<DailyTask>) {
        val array = JSONArray()
        tasks.forEach { task ->
            array.put(JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("completed", task.completed)
            })
        }
        prefs(context).edit().putString(storageKey(), array.toString()).apply()
    }

    private fun readTasks(context: Context, key: String): List<DailyTask> {
        val raw = prefs(context).getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val title = item.optString("title").trim()
                    if (title.isNotEmpty()) {
                        add(
                            DailyTask(
                                item.optString("id", UUID.randomUUID().toString()),
                                title,
                                item.optBoolean("completed", false)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
