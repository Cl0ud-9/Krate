package dev.cl0ud9.krate.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.InviteStatus
import dev.cl0ud9.krate.ui.util.steadyHeight
import dev.cl0ud9.krate.voice.Moment
import dev.cl0ud9.krate.voice.rememberKrateLeadIn

// a read-only token for the private artifacts repo unlocks the invite-only catalog; the row says what it actually got
@Composable
internal fun GitHubAccessRow(
    hasToken: Boolean,
    inviteStatus: InviteStatus,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
    shape: Shape,
) {
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = painterResource(R.drawable.ic_key_rounded),
                title = "GitHub access",
                subtitle = accessSubtitle(hasToken, inviteStatus),
                colors = SettingsTint.INDIGO.colors(),
            ),
        shape = shape,
    ) {
        GitHubAccessRowContent(
            hasToken = hasToken,
            inviteStatus = inviteStatus,
            onSaveToken = onSaveToken,
            onClearToken = onClearToken,
        )
    }
}

private fun accessSubtitle(
    hasToken: Boolean,
    status: InviteStatus,
): String =
    when {
        !hasToken -> "Got an invite? Add your token to unlock a few extra apps."
        status is InviteStatus.Open -> "Invite-only apps are unlocked."
        status is InviteStatus.Rejected -> "The saved token isn't working."
        else -> "A token is saved."
    }

// the one line under Settings' GitHub access row: what the saved token actually got, with a wink when playful
internal fun gitHubAccessSummary(
    hasToken: Boolean,
    status: InviteStatus,
    playful: Boolean,
): String {
    if (!hasToken) return "Unlocks invite-only apps"
    return when (status) {
        is InviteStatus.Open -> openSummary(status.appCount, playful)
        InviteStatus.Rejected ->
            if (playful) "That key stopped fitting. Ask for a new one" else "The saved token isn't working"
        InviteStatus.Unreachable ->
            if (playful) "Token saved. GitHub isn't answering yet" else "Token saved, GitHub unreachable"
        InviteStatus.Checking -> "Checking your token..."
        else -> if (playful) "Invite saved. Checking it on the next refresh" else "Token saved"
    }
}

private fun openSummary(
    appCount: Int,
    playful: Boolean,
): String {
    val apps = if (appCount == 1) "1 invite-only app" else "$appCount invite-only apps"
    return when {
        appCount == 0 && playful -> "You're in. Nothing secret on the shelf yet"
        appCount == 0 -> "Token works, no invite-only apps yet"
        playful -> "You're in. $apps unlocked"
        else -> "$apps unlocked"
    }
}

@Composable
private fun GitHubAccessRowContent(
    hasToken: Boolean,
    inviteStatus: InviteStatus,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
) {
    if (hasToken) {
        InviteStatusLine(inviteStatus)
        OutlinedButton(onClick = onClearToken, modifier = Modifier.fillMaxWidth()) {
            Text("Remove saved token")
        }
        return
    }
    var tokenInput by remember { mutableStateOf("") }
    // what the token is and where it goes, in plain words for someone who was simply sent one
    Text(
        text =
            "Paste the GitHub token that came with your invite. It stays on this phone and is only ever sent to " +
                "GitHub.",
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
        modifier = Modifier.fillMaxWidth().steadyHeight(),
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

@Composable
private fun InviteStatusLine(status: InviteStatus) {
    val colors = MaterialTheme.colorScheme
    val icon =
        when (status) {
            is InviteStatus.Open -> R.drawable.ic_check_circle_rounded
            InviteStatus.Rejected -> R.drawable.ic_error_rounded
            InviteStatus.Unreachable -> R.drawable.ic_cloud_off_rounded
            else -> R.drawable.ic_key_rounded
        }
    KrateUpdateStatusRow(
        icon = painterResource(icon),
        badgeColor =
            when (status) {
                is InviteStatus.Open -> colors.tertiaryContainer
                InviteStatus.Rejected -> colors.errorContainer
                else -> colors.surfaceContainerHighest
            },
        contentColor =
            when (status) {
                is InviteStatus.Open -> colors.onTertiaryContainer
                InviteStatus.Rejected -> colors.onErrorContainer
                else -> colors.onSurfaceVariant
            },
        text = inviteStatusText(status),
    )
}

@Composable
private fun inviteStatusText(status: InviteStatus): String =
    when (status) {
        is InviteStatus.Open ->
            if (status.appCount == 0) {
                "The token works. There are no invite-only apps right now."
            } else {
                val apps =
                    if (status.appCount == 1) "1 invite-only app is" else "${status.appCount} invite-only apps are"
                rememberKrateLeadIn(Moment.INVITE_UNLOCKED, "$apps now in your catalog.")
            }
        InviteStatus.Rejected ->
            rememberKrateLeadIn(
                Moment.TOKEN_REJECTED,
                "GitHub didn't accept it. It may have expired or been switched off, so ask whoever invited you " +
                    "for a new one.",
            )
        InviteStatus.Unreachable ->
            rememberKrateLeadIn(Moment.INVITE_UNREACHABLE, "Krate will try again on the next refresh.")
        InviteStatus.Checking -> "Checking the token..."
        else -> "Saved. Krate checks it on the next refresh."
    }
