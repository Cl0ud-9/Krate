package dev.cl0ud9.manager.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

// only a couple of catalog entries (currently just Videoapp Patched) are hosted as a private
// release asset rather than a public one - this token is what lets the download engine authenticate
// to fetch those. A read-only "Contents" scope on the manager repo alone is all it ever needs
@Composable
internal fun GitHubAccessRow(
    hasToken: Boolean,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
    shape: Shape,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = Icons.Filled.Key,
                title = "GitHub access",
                subtitle =
                    if (hasToken) {
                        "A token is saved - needed for a few private catalog entries."
                    } else {
                        "Add a token to unlock catalog entries hosted privately."
                    },
                colors =
                    SettingsRowColors(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
            ),
        shape = shape,
    ) {
        GitHubAccessRowContent(hasToken = hasToken, onSaveToken = onSaveToken, onClearToken = onClearToken)
    }
}

@Composable
private fun GitHubAccessRowContent(
    hasToken: Boolean,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
) {
    if (hasToken) {
        OutlinedButton(onClick = onClearToken, modifier = Modifier.fillMaxWidth()) {
            Text("Remove saved token")
        }
        return
    }
    var tokenInput by remember { mutableStateOf("") }
    OutlinedTextField(
        value = tokenInput,
        onValueChange = { tokenInput = it },
        label = { Text("Personal access token") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            onSaveToken(tokenInput)
            tokenInput = ""
        },
        enabled = tokenInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save token")
    }
}
