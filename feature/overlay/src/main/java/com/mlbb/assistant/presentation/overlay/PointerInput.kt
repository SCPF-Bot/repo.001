package com.mlbb.assistant.presentation.overlay

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput as composePointerInput

/** Exposes Compose's pointer input modifier to this package without changing widget behavior. */
internal fun Modifier.pointerInput(
    key: Any?,
    block: suspend PointerInputScope.() -> Unit
): Modifier = this.composePointerInput(key, block)
