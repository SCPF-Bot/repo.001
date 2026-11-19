package com.mlbb.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mlbb.plus.ui.model.GameData
import com.mlbb.ui.widgets.JetOverlay
import com.mlbb.ui.widgets.RoamGuideWidget

// ============================================================
// mlbb/ui/WidgetOverlayService.kt
// GitHub path: app/src/main/java/com/mlbb/ui/WidgetOverlayService.kt
// ------------------------------------------------------------
// UPDATED FOR NEW ASSETS:
//   Reads ROLE + ROLE_TYPE from MainActivity (e.g. Roam + Tank)
//   Calls GameData.load() so overlay also has heroes.json / items.json
//   even if app was killed and service restarted.
//   Widget portraits use: assets/images/heroes/{id}.webp
// ============================================================

class WidgetOverlayService : Service() {

    private var overlayInitialized = false

    override fun onCreate() {
        super.onCreate()
        // Service can start without MainActivity — load JSONs here too
        GameData.load(this)
        createChannel()
        startForeground(1, buildNotification(currentRole, currentRoleType))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Role comes from Home dropdowns in MainActivity
        val role = intent?.getStringExtra(EXTRA_ROLE) ?: currentRole
        val roleType = intent?.getStringExtra(EXTRA_ROLE_TYPE) ?: currentRoleType
        currentRole = role
        currentRoleType = roleType
        isRunning = true

        getSystemService(NotificationManager::class.java)
            .notify(1, buildNotification(role, roleType))

        if (!overlayInitialized) {
            JetOverlay.initialize(application) {
                overlayContent {
                    RoamGuideWidget(
                        role = currentRole,
                        roleType = currentRoleType,
                        onBack = { JetOverlay.hide() }
                    )
                }
            }
            overlayInitialized = true
        }
        JetOverlay.show()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        JetOverlay.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(role: String, roleType: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("MLBB Plus running")
            .setContentText("$roleType $role • Tap Stop in the app to hide it")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "MLBB Plus", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_ROLE = "ROLE"
        const val EXTRA_ROLE_TYPE = "ROLE_TYPE"
        const val ACTION_STOP = "com.mlbb.plus.STOP_OVERLAY"

        var isRunning = false
            private set

        var currentRole by mutableStateOf("Roam")
            private set
        var currentRoleType by mutableStateOf("Tank")
            private set

        private const val CHANNEL_ID = "mlbb_plus"
    }
}
