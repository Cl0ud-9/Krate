package dev.cl0ud9.krate.ui.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val SHOWN_BYTES = 8

// "3F:A2:9C:41:..." for a signer's fingerprint: capitals, a pair at a time, the start of it with an ellipsis, since a
// whole one is 32 pairs; null for anything that isn't a hex fingerprint, so nothing odd is ever shown as one
internal fun fingerprintStart(hex: String?): String? {
    val clean = hex?.filterNot { it == ':' || it.isWhitespace() }?.uppercase().orEmpty()
    if (clean.length < SHOWN_BYTES * 2 || clean.length % 2 != 0 || !clean.all { it in "0123456789ABCDEF" }) return null
    return clean.take(SHOWN_BYTES * 2).chunked(2).joinToString(":") + "..."
}

internal fun fingerprintFull(hex: String): String =
    hex
        .filterNot { it == ':' || it.isWhitespace() }
        .uppercase()
        .chunked(2)
        .joinToString(":")

// who signed the copy on this phone, as one action rather than a long string of hex that most people never need: a tap
// copies the whole fingerprint, for anyone who wants to compare it with the developer's own. Nothing is shown for a
// signer Android didn't report, or one that isn't a hex fingerprint
@Composable
internal fun SignerLine(signerHex: String?) {
    if (fingerprintStart(signerHex) == null) return
    val full = fingerprintFull(signerHex.orEmpty())
    val context = LocalContext.current
    TextButton(
        onClick = { context.copyFingerprint(full) },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
    ) {
        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Copy signing fingerprint", style = MaterialTheme.typography.labelLarge)
    }
}

private fun Context.copyFingerprint(full: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Signing fingerprint", full))
    // Android 13 and later confirm a copy on their own
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, "Fingerprint copied", Toast.LENGTH_SHORT).show()
    }
}
