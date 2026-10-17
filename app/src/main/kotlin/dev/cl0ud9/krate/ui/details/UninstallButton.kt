package dev.cl0ud9.krate.ui.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// what removes the app, handed down from the page so the actions that sit deep in the card can offer it
internal val LocalUninstallAction = staticCompositionLocalOf<(() -> Unit)?> { null }

private const val OUTLINE_ALPHA = 0.5f

// the way out, beside the other actions: outlined in the error colour so it reads as a different kind of step than
// Open or Reinstall without shouting. Nothing is shown where the page offers no uninstall
@Composable
internal fun UninstallButton() {
    val onUninstall = LocalUninstallAction.current ?: return
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onUninstall,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
        border = BorderStroke(1.dp, error.copy(alpha = OUTLINE_ALPHA)),
    ) {
        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Uninstall")
    }
}
