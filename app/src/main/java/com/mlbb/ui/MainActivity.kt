package com.mlbb.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// GitHub dark
private val GithubBg = Color(0xFF0D1117)
private val GithubCard = Color(0xFF161B22)
private val GithubBorder = Color(0xFF30363D)
private val GithubText = Color(0xFFE6EDF3)
private val GithubSecondary = Color(0xFF7D8590)
private val GithubBlue = Color(0xFF2F81F7)
private val GithubBlueBright = Color(0xFF58A6FF)
private val GithubGreen = Color(0xFF3FB950)
private val GithubOrange = Color(0xFFD29922)
private val GithubRed = Color(0xFFF85149)

class MainActivity : ComponentActivity() {

    private var canDrawOverlay by mutableStateOf(false)
    private var isRunning by mutableStateOf(false)
    private var autoStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshState()

        // 4. Auto-close: if permission is already granted and overlay is NOT running,
        // start it and close the app right away. No need to tap Start.
        if (canDrawOverlay && !isRunning && !autoStarted) {
            autoStarted = true
            startRoam()
            finish()
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = GithubBg,
                    surface = GithubCard,
                    surfaceVariant = Color(0xFF21262D),
                    onBackground = GithubText,
                    onSurface = GithubText,
                    primary = GithubBlue,
                    onPrimary = Color.White,
                    outline = GithubBorder
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GithubBg)
                        // 1. Fix overlap: respect status bar + navigation bar
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(GithubBlue, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("M", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "MLBB Plus",
                                    color = GithubText,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 32.sp
                                )
                                Text(
                                    "Overlay tools for MLBB",
                                    color = GithubSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Roam Guide card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = GithubCard),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GithubBorder, RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(if (canDrawOverlay) GithubGreen else GithubOrange, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isRunning) "Roam Guide • Running" else "Roam Guide",
                                        color = GithubText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF21262D), RoundedCornerShape(100.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            if (canDrawOverlay) "READY" else "NEEDS PERMISSION",
                                            color = if (canDrawOverlay) GithubGreen else GithubOrange,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    "Jungle pathing, rotation and gank timers, right on top of your game.",
                                    color = GithubSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                                Text(
                                    if (canDrawOverlay) "Overlay permission: granted" else "Overlay permission: needed",
                                    color = GithubSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Actions
                        if (!canDrawOverlay) {
                            Button(
                                onClick = { requestOverlayPermission() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GithubBlue),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text("Allow overlay permission", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "You only need to allow this once. After that, opening the app will auto-start and auto-close.",
                                color = GithubSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        } else if (isRunning) {
                            // If you open the app while it is running, show Stop instead of starting again.
                            // This is the only way to stop it now that it auto-closes on launch.
                            OutlinedButton(
                                onClick = {
                                    stopRoam()
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GithubRed),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text("Stop Roam Guide", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "Overlay is running. Tap Stop to hide it.",
                                color = GithubSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            // Fallback if auto-start was skipped for some reason
                            Button(
                                onClick = {
                                    startRoam()
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GithubBlue),
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text("Start Roam Guide", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "v1.0.0 • Tap the app icon to start • Re-open the app to stop",
                            color = Color(0xFF484F58),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        // If user just granted permission in Settings and came back, auto-start + auto-close.
        if (canDrawOverlay && !isRunning && !autoStarted) {
            autoStarted = true
            startRoam()
            finish()
        }
    }

    private fun refreshState() {
        canDrawOverlay = Settings.canDrawOverlays(this)
        isRunning = WidgetOverlayService.isRunning
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
        startForegroundService(Intent(this, WidgetOverlayService::class.java))
    }

    private fun stopRoam() {
        stopService(Intent(this, WidgetOverlayService::class.java))
    }
}
