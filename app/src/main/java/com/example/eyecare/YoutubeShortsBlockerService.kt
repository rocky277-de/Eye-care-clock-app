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

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != YOUTUBE_PACKAGE) return
        if (!isBlockerEnabled()) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (containsShortsLabel(event.source) ||
                    event.text.any { it.toString().equals("Shorts", ignoreCase = true) }
                ) {
                    showBlocker()
                }
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Avoid blocking the normal YouTube home screen just because its
                // bottom navigation contains the word "Shorts".
                if (isLikelyShortsScreen(getRootInActiveWindow())) {
                    showBlocker()
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

    private fun showBlocker() {
        val now = System.currentTimeMillis()
        if (blockerView != null || now - lastBlockAt < 1200L) return
        lastBlockAt = now

        val view = LayoutInflater.from(this)
            .inflate(R.layout.layout_shorts_blocker, null)

        view.findViewById<TextView>(R.id.shortsBlockerMessage).text =
            "YouTube Shorts is blocked\n\nUse YouTube for long-form videos only."

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
        } catch (_: Exception) {
            blockerView = null
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

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeBlocker()
        super.onDestroy()
    }
}
