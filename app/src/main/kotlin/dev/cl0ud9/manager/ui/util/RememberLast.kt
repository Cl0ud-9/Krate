package dev.cl0ud9.manager.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// the last non-null value seen, so content animating away keeps showing what it showed instead of going blank
@Composable
fun <T : Any> rememberLastNonNull(value: T?): T? {
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    @Suppress("UNCHECKED_CAST")
    return last[0] as T?
}
