package com.mlbb.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var canDrawOverlay by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermission()

        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("MLBB Roam Guide", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (canDrawOverlay) "Overlay permission: granted"
                        else "Overlay permission: needed"
                    )

                    Button(
                        onClick = { requestOverlayPermission() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !canDrawOverlay
                    ) {
                        Text("Allow overlay permission")
                    }

                    Button(
                        onClick = { startRoam() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canDrawOverlay
                    ) {
                        Text("Start Roam Guide")
                    }

                    Button(
                        onClick = { stopRoam() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop Roam Guide")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermission()
    }

    private fun refreshPermission() {
        canDrawOverlay = Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startRoam() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        startForegroundService(Intent(this, RoamOverlayService::class.java))
    }

    private fun stopRoam() {
        stopService(Intent(this, RoamOverlayService::class.java))
    }
}
