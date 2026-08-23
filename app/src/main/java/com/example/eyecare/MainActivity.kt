package com.example.eyecare

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun requestOverlayPermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please allow 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    inner class AndroidBridge {

        @JavascriptInterface
        fun startTimer() {
            runOnUiThread {
                if (requestOverlayPermissionIfNeeded()) {
                    TimerManager.startTimer(this@MainActivity)
                    Toast.makeText(this@MainActivity, "20-20-20 reminder started", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun stopTimer() {
            runOnUiThread {
                TimerManager.stopTimer(this@MainActivity)
                Toast.makeText(this@MainActivity, "Reminder stopped", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
