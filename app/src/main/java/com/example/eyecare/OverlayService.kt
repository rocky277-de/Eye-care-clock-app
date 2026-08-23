// Inside OverlayService.kt

override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    timerManager = TimerManager(this)
    
    // FIX: Start Foreground to prevent background crash
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel("OverlayService", "Overlay Running", android.app.NotificationManager.IMPORTANCE_LOW)
        getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        val notification = androidx.core.app.NotificationCompat.Builder(this, "OverlayService")
            .setContentTitle("Eye Break Active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(2021, notification)
    }

    showOverlay()
}

