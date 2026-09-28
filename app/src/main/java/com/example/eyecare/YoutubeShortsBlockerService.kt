package com.example.eyecare

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView

class YoutubeShortsBlockerService : AccessibilityService() {

    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        private const val PREF_BLOCK_SHORTS = "block_youtube_shorts"
    }

    private var windowManager: WindowManager? = null
    private var blockerView: View? = null
    private var lastBlockAt = 0L
    private var lastBlockedPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        lastBlockAt = 0L
        lastBlockedPackage = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // Focus Mode app blocking applies only while a work session is active.
        if (BlockedAppsManager.isBlocked(this, packageName)) {
            showBlocker(packageName)
            return
        }

        // If Focus Mode ended or the selected app is no longer blocked,
        // remove any stale blocker overlay immediately.
        if (blockerView != null && lastBlockedPackage == packageName &&
            !BlockedAppsManager.isBlocked(this, packageName)) {
            removeBlocker()
        }

        if (packageName != YOUTUBE_PACKAGE || !isBlockerEnabled()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (containsShortsLabel(event.source) ||
                    event.text.any { it.toString().equals("Shorts", ignoreCase = true) }
                ) {
                    showBlocker(YOUTUBE_PACKAGE)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Avoid blocking the normal YouTube home screen just because its
                // bottom navigation contains the word "Shorts".
                if (isLikelyShortsScreen(getRootInActiveWindow())) {
                    showBlocker(YOUTUBE_PACKAGE)
                }
            }
        }
    }

    private fun isBlockerEnabled(): Boolean {
        val prefs = getSharedPreferences(TimerManager.PREFS_NAME, MODE_PRIVATE)
        val manual = prefs.getBoolean(PREF_BLOCK_SHORTS, false)
        val focus = prefs.getBoolean(TimerManager.PREF_FOCUS_MODE_ENABLED, false) &&
            prefs.getBoolean(TimerManager.PREF_FOCUS_ACTIVE, false)
        return manual || focus
    }

    private fun containsShortsLabel(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if (text.equals("Shorts", true) || desc.equals("Shorts", true)) return true

        for (i in 0 until node.childCount) {
            if (containsShortsLabel(node.getChild(i))) return true
        }
        return false
    }

    private fun isLikelyShortsScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false

        val labels = ArrayList<String>()
        collectLabels(root, labels)

        val hasShorts = labels.any { it.equals("Shorts", true) }
        if (!hasShorts) return false

        // Shorts viewer commonly exposes several action labels together.
        val actionCount = listOf("Like", "Dislike", "Share", "Comments", "Comment")
            .count { wanted -> labels.any { it.equals(wanted, true) } }

        return actionCount >= 2
    }

    private fun collectLabels(node: AccessibilityNodeInfo?, labels: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { labels.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { labels.add(it) }

        for (i in 0 until node.childCount) {
            collectLabels(node.getChild(i), labels)
        }
    }

    private fun showBlocker(packageName: String) {
        val now = System.currentTimeMillis()
        if (blockerView != null || now - lastBlockAt < 1200L) return
        lastBlockAt = now
        lastBlockedPackage = packageName

        val view = LayoutInflater.from(this)
            .inflate(R.layout.layout_shorts_blocker, null)

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            "This app"
        }

        view.findViewById<TextView>(R.id.shortsBlockerMessage).text =
            if (packageName == YOUTUBE_PACKAGE) {
                "YouTube Shorts is blocked\n\nUse YouTube for long-form videos only."
            } else {
                "$appName is blocked during Focus Mode."
            }

        view.findViewById<Button>(R.id.leaveShortsButton).setOnClickListener {
            performGlobalAction(GLOBAL_ACTION_BACK)
            removeBlocker()
        }

        view.findViewById<Button>(R.id.closeBlockerButton).setOnClickListener {
            removeBlocker()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager?.addView(view, params)
            blockerView = view
            if (BlockedAppsManager.isBlocked(this, packageName)) {
                BlockedAppsManager.recordBlockedAttempt(this, packageName)
            }
        } catch (_: Exception) {
            blockerView = null
        lastBlockedPackage = null
        }
    }

    private fun removeBlocker() {
        blockerView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        blockerView = null
    }

    override fun onInterrupt() {
        // Accessibility can be interrupted by Android; clear transient UI
        // state so a later event can recreate the blocker cleanly.
        removeBlocker()
    }

    override fun onDestroy() {
        removeBlocker()
        super.onDestroy()
    }
}
