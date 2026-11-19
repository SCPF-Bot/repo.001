package com.mlbb.ui

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.mlbb.ui.widgets.JetOverlay
import com.mlbb.ui.widgets.RoamGuideWidget

class WidgetOverlayService : Service() {

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("MLBB Plus running")
            .setContentText("Tap Stop in the app to hide it")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)

        JetOverlay.initialize(application as Application) {
            overlayContent {
                RoamGuideWidget(onBack = { JetOverlay.hide() })
            }
        }
        JetOverlay.show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        JetOverlay.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MLBB Plus",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        var isRunning = false
            private set
        private const val CHANNEL_ID = "mlbb_plus"
    }
}
