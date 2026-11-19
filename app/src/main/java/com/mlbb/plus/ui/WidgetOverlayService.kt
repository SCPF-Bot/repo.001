package com.mlbb.plus.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.widgets.JetOverlay
import com.mlbb.plus.ui.widgets.RoamGuideWidget

// ============================================================
// mlbb/ui/WidgetOverlayService.kt  ->  FIXED
// GitHub path: app/src/main/java/com/mlbb/plus/ui/WidgetOverlayService.kt
// ------------------------------------------------------------
// FIXES:
//  [1] Package unified: com.mlbb.ui -> com.mlbb.plus.ui
//      (matches GameData / MainActivity / namespace).
//  [2] Foreground service for targetSdk 36 / Android 14+:
//      Old: startForeground(1, notification) with plain
//      Notification.Builder. On API 29+ / 34+ this throws
//      SecurityException / ForegroundServiceDidNotStartInTime
//      without a declared foregroundServiceType + permission.
//      Fix: ServiceCompat.startForeground() with
//      FOREGROUND_SERVICE_TYPE_SPECIAL_USE on API 29+, plus
//      NotificationCompat with setForegroundServiceBehavior(
//      FOREGROUND_SERVICE_IMMEDIATE).
//      MANIFEST STILL REQUIRED (see bottom comment):
//        <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
//        <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
//        <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
//        <service android:name=".ui.WidgetOverlayService"
//            android:foregroundServiceType="specialUse" ... />
//  [3] Notification: added content Intent (tap -> MainActivity),
//      Stop action (PendingIntent to ACTION_STOP), setOngoing(true),
//      FLAG_IMMUTABLE (required API 31+), NotificationCompat.
//      Before: notification had no way to stop the service and
//      used a bare Builder without ongoing / actions.
//  [4] companion setters: private set -> internal set + explicit
//      updateRole() / setRunning() helpers. Old private-set in a
//      companion is fragile / confusing - the instance methods
//      assign companion state. Helpers make the mutation explicit
//      and keep the public API read-only.
//  [5] onStartCommand: handles null intent (system restarts a
//      START_STICKY service with null intent). Old code already
//      used ?: currentRole, now documented + null-safe notify().
//  [6] onDestroy: stopForeground(STOP_FOREGROUND_REMOVE) so the
//      notification is actually removed. Old code only hid overlay.
//  [7] NotificationManager.notify() made null-safe (?.) -
//      getSystemService() can return null on some OEMs.
// ============================================================

class WidgetOverlayService : Service() {

    private var overlayInitialized = false

    override fun onCreate() {
        super.onCreate()
        // Service can start without MainActivity - load JSONs here too
        GameData.load(this)
        createChannel()
        // FIX [2]: must call startForeground immediately in onCreate.
        ServiceCompat.startForeground(
            this,
            1,
            buildNotification(currentRole, currentRoleType),
            // SPECIAL_USE only exists on API 34+; passing it on 29-33 is invalid.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // FIX [5]: intent is null when the system restarts a
        // START_STICKY service - fall back to last known role.
        val role = intent?.getStringExtra(EXTRA_ROLE) ?: currentRole
        val roleType = intent?.getStringExtra(EXTRA_ROLE_TYPE) ?: currentRoleType
        updateRole(role, roleType)
        setRunning(true)

        // FIX [7]: null-safe notify.
        getSystemService(NotificationManager::class.java)
            ?.notify(1, buildNotification(role, roleType))

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
        setRunning(false)
        JetOverlay.hide()
        // FIX [6]: remove the foreground notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(role: String, roleType: String): Notification {
        // FIX [3]: tap notification -> open app.
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // FIX [3]: Stop action.
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WidgetOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MLBB Plus running")
            .setContentText("$roleType $role \u2022 Tap to open app, or Stop to hide it")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "MLBB Plus", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_ROLE = "ROLE"
        const val EXTRA_ROLE_TYPE = "ROLE_TYPE"
        const val ACTION_STOP = "com.mlbb.plus.STOP_OVERLAY"

        var isRunning = false
            internal set

        var currentRole by mutableStateOf("Roam")
            internal set
        var currentRoleType by mutableStateOf("Tank")
            internal set

        // FIX [4]: explicit mutation helpers.
        internal fun updateRole(role: String, roleType: String) {
            currentRole = role
            currentRoleType = roleType
        }
        internal fun setRunning(value: Boolean) {
            isRunning = value
        }

        private const val CHANNEL_ID = "mlbb_plus"
    }
}

// MANIFEST (AndroidManifest.xml) - REQUIRED, not in .kts:
//   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
//   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
//   <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
//   <application ...>
//     <service
//       android:name=".ui.WidgetOverlayService"
//       android:exported="false"
//       android:foregroundServiceType="specialUse" />
//   </application>
//   NOTE: if your service class is in com.mlbb.plus.ui, the
//   manifest name above resolves via namespace com.mlbb.plus.
//   If you keep the old package com.mlbb.ui, use the fully
//   qualified name instead.
