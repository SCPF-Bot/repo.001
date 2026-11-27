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
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mlbb.plus.R
import com.mlbb.plus.tts.VoiceGuideManager
import com.mlbb.plus.ui.model.GameData
import com.mlbb.plus.ui.model.RoleTypes
import com.mlbb.plus.ui.theme.MLBBPlusTheme
import com.mlbb.plus.ui.widgets.JetOverlay
import com.mlbb.plus.ui.widgets.RoamGuideWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the floating guide overlay and the Voice Guide coach.
 *
 * Lifecycle: onCreate starts foreground immediately (required on API 31+ for a
 * service started from the background). onStartCommand (re)targets the role and
 * shows the overlay. onDestroy stops speech, releases the native voice engine,
 * hides the overlay and removes the notification.
 */
class WidgetOverlayService : Service() {

    private var overlayInitialized = false

    /**
     * Voice Guide coach. Created with the service so the overlay (which is built
     * once) always captures a live instance, and released in [onDestroy].
     *
     * It does nothing at all until the player enables Voice Guide in Settings:
     * no engine is loaded, no audio focus is requested, no model is downloaded,
     * and no voice row is composed. That is what keeps the overlay byte-for-byte
     * identical for everyone who never turns the feature on.
     */
    private var voice: VoiceGuideManager? = null

    private var notificationScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        // Service can start without MainActivity - load JSONs here too
        GameData.load(this)
        if (currentRoleType !in RoleTypes.typesFor(currentRole)) {
            updateRole(currentRole, RoleTypes.defaultTypeFor(currentRole))
        }
        createChannel()
        voice = VoiceGuideManager(this)
        observeVoiceForNotification()
        // Must reach startForeground promptly, so it happens here rather than after the overlay is built.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
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

        // Voice Guide notification actions. They never start or stop the overlay
        // itself: "Stop voice" only silences the coach.
        when (intent?.action) {
            ACTION_VOICE_TOGGLE -> {
                voice?.toggleFromNotification()
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification(currentRole, currentRoleType))
                return START_STICKY
            }

            ACTION_VOICE_STOP -> {
                voice?.stopFromNotification()
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification(currentRole, currentRoleType))
                return START_STICKY
            }

            else -> Unit
        }

        // The intent is null when the system restarts a START_STICKY service: fall back to the last known role.
        val requestedRole = intent?.getStringExtra(EXTRA_ROLE) ?: currentRole
        val role = requestedRole.takeIf { it in RoleTypes.roles } ?: "Roam"
        val requestedType = intent?.getStringExtra(EXTRA_ROLE_TYPE) ?: currentRoleType
        val validTypes = RoleTypes.typesFor(role)
        val roleType = requestedType.takeIf { it in validTypes }
            ?: RoleTypes.defaultTypeFor(role)
        updateRole(role, roleType)
        markRunning(true)

        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(role, roleType))

        if (!overlayInitialized) {
            JetOverlay.initialize(application) {
                overlayContent {
                    MLBBPlusTheme {
                        RoamGuideWidget(
                            role = currentRole,
                            roleType = currentRoleType,
                            onBack = { stopSelf() },
                            voice = voice
                        )
                    }
                }
            }
            overlayInitialized = true
        }
        JetOverlay.show()
        return START_STICKY
    }

    override fun onDestroy() {
        markRunning(false)
        // Stop speech, hand audio focus back to the game, release the native engine
        // (Kokoro holds ~200 MB) and drop the listeners.
        notificationScope?.cancel()
        notificationScope = null
        voice?.release()
        voice = null
        JetOverlay.hide()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Keeps the notification honest while the coach talks: the Play/Pause label
     * follows the manager's phase. Cheap, because phase changes are rare and
     * [distinctUntilChanged] collapses recomposition churn.
     */
    private fun observeVoiceForNotification() {
        val manager = voice ?: return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        notificationScope = scope
        scope.launch {
            snapshotFlow { manager.state.phase to manager.state.enabled }
                .distinctUntilChanged()
                .collect {
                    getSystemService(NotificationManager::class.java)
                        ?.notify(NOTIFICATION_ID, buildNotification(currentRole, currentRoleType))
                }
        }
    }

    private fun buildNotification(role: String, roleType: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WidgetOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val voiceState = voice?.state
        val voiceOn = voiceState?.enabled == true
        val speaking = voiceState?.phase == VoiceGuideManager.Phase.SPEAKING
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_running_title))
            .setContentText(
                if (voiceOn && speaking) {
                    getString(R.string.notif_text_speaking, roleType, role)
                } else {
                    getString(R.string.notif_text_idle, roleType, role)
                }
            )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .setOngoing(true)

        // Voice actions only exist while the feature is on, so the notification
        // is unchanged for everyone else.
        if (voiceOn) {
            val toggleIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, WidgetOverlayService::class.java).setAction(ACTION_VOICE_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val voiceStopIntent = PendingIntent.getService(
                this,
                3,
                Intent(this, WidgetOverlayService::class.java).setAction(ACTION_VOICE_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder
                .addAction(
                    android.R.drawable.ic_media_play,
                    getString(if (speaking) R.string.notif_action_pause_voice else R.string.notif_action_play_voice),
                    toggleIntent
                )
                .addAction(R.drawable.ic_voice_stop, getString(R.string.notif_action_stop_voice), voiceStopIntent)
        }

        return builder
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_action_stop), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_ROLE = "ROLE"
        const val EXTRA_ROLE_TYPE = "ROLE_TYPE"
        const val ACTION_STOP = "com.mlbb.plus.STOP_OVERLAY"

        /** Voice Guide: Play/Pause and Stop-voice notification actions. */
        const val ACTION_VOICE_TOGGLE = "com.mlbb.plus.VOICE_TOGGLE"
        const val ACTION_VOICE_STOP = "com.mlbb.plus.VOICE_STOP"

        var isRunning by mutableStateOf(false)
            internal set

        var currentRole by mutableStateOf("Roam")
            internal set
        var currentRoleType by mutableStateOf("")
            internal set

        // Explicit mutation helpers: the public surface stays read-only.
        internal fun updateRole(role: String, roleType: String) {
            currentRole = role
            currentRoleType = roleType
        }
        internal fun markRunning(value: Boolean) {
            isRunning = value
        }

        private const val CHANNEL_ID = "mlbb_plus"

        /** Single foreground notification id, shared by every notify() call. */
        private const val NOTIFICATION_ID = 1
    }
}
