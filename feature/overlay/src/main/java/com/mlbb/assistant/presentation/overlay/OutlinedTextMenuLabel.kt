package com.mlbb.assistant.presentation.overlay

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** Styled overload for simple menu labels that don't need a custom text color. */
@Composable
internal fun OutlinedText(
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = Color(0xFFE6EDF3),
        fontSize = 9.sp,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}
