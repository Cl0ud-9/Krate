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

// the Patched-style catalog entries (Videoapp, and any future sibling app built the same way) are
// hosted as published releases on one shared *private* artifacts repo, never the public manager
// repo itself - this token is what lets the download engine authenticate to fetch those. A
// read-only fine-grained token scoped to just that repo is all it ever needs: unlike a draft
// release (which GitHub only exposes to push-access accounts), a private repo's published release
// only needs read access - see SETUP.md section 4
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
    // spelled out up front rather than left for the user to discover from a bare HTTP error after
    // saving - a token scoped to the wrong repo, or created before being added as a collaborator
    // on the artifacts repo, still fails with a 403/404
    Text(
        text =
            "Needs a fine-grained token scoped to the private artifacts repo you were invited to, " +
                "with \"Contents: Read-only\" access.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
