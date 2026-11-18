package com.mlbb.assistant.presentation.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact launcher bubble for opening the roam guide from the minimized overlay. */
@Composable
fun RoamGuideBubble(onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF161B22))
            .border(2.dp, Color(0xFFE3B341), CircleShape)
            .clickable(onClick = onTap)
            .semantics { contentDescription = "Open roam guide" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "R",
            color = Color(0xFFE3B341),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
