package dev.cl0ud9.krate.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.backup.AppBackups
import dev.cl0ud9.krate.platform.backup.AutoBackupRunner
import dev.cl0ud9.krate.platform.backup.AutoBackupState
import dev.cl0ud9.krate.platform.backup.SavedBackup
import dev.cl0ud9.krate.platform.setup.RESTRICTED_SETTINGS_HELP
import dev.cl0ud9.krate.platform.setup.appInfoIntent
import dev.cl0ud9.krate.ui.components.AppIconAvatar
import dev.cl0ud9.krate.ui.components.KrateDialog
import dev.cl0ud9.krate.ui.components.KrateDialogHeader
import dev.cl0ud9.krate.ui.components.KrateSwitch
import dev.cl0ud9.krate.ui.details.autoBackupStateFor
import dev.cl0ud9.krate.ui.details.relativeTime
import dev.cl0ud9.krate.ui.details.rememberAutoBackupOn
import dev.cl0ud9.krate.ui.details.runningText
import dev.cl0ud9.krate.ui.details.startSafely
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.navigation.rememberGlassRimBorder
import dev.cl0ud9.krate.ui.theme.ShapeCache

private val APP_ROW_MIN_HEIGHT = 72.dp
private val APP_ICON = 40.dp

// automatic backups in one place, built from the same rows as the rest of Settings: the switch, what it does, and
// each app that supports it with when it was last saved
@Composable
fun BackupsPage(
    scrollState: ScrollState,
    topContentPadding: Dp,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val catalog by remember(container) {
        container.catalogRepository.observeApps()
    }.collectAsStateWithLifecycle(emptyList())
    val on = rememberAutoBackupOn()
    val supported = catalog.filter { it.guide?.autoBackup != null }
    SettingsPage(scrollState, topContentPadding) {
        AutoBackupSwitchRow(on = on)
        if (!on) RestrictedHelpRow()
        SettingsSectionLabel("Apps")
        if (supported.isEmpty()) {
            Text(
                text =
                    "None of your apps can be backed up this way yet. Apps that save their settings as text show " +
                        "up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        supported.forEachIndexed { index, app ->
            val reader = container.installedPackageReader
            val installed = remember(app.packageName) { reader.installedVersion(app.packageName) != null }
            BackupAppRow(app, installed, on, settingsGroupShape(index, supported.size))
        }
    }
}

// on: Krate switches its helper off itself. Off: Android only lets the person switch it on, in its own settings
@Composable
private fun AutoBackupSwitchRow(on: Boolean) {
    val context = LocalContext.current
    val toggle = {
        if (on) AutoBackupRunner.turnOff() else startSafely(context, AutoBackupRunner.settingsIntent(context), null)
    }
    SettingsRow(
        header =
            SettingsRowHeader(
                icon = rememberVectorPainter(Icons.Filled.SettingsBackupRestore),
                title = "Automatic backups",
                subtitle =
                    if (on) {
                        "Saves an app's settings before a reinstall from scratch, and restores them after."
                    } else {
                        "Krate opens the app's own backup screen for you. Needs accessibility access, used only " +
                            "while a backup runs."
                    },
                colors = SettingsTint.TEAL.colors(),
            ),
        shape = settingsGroupShape(0, 1),
        onClick = { toggle() },
        trailing = { KrateSwitch(checked = on, onCheckedChange = { toggle() }) },
    )
}

// the way past Android greying out the switch, folded away until asked for
@Composable
private fun RestrictedHelpRow() {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        TextButton(onClick = { open = !open }) { Text(if (open) "Hide help" else "Switch greyed out?") }
        if (open) {
            Text(
                text = RESTRICTED_SETTINGS_HELP,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            TextButton(onClick = { startSafely(context, appInfoIntent(context.packageName), null) }) {
                Text("Open Krate's App info")
            }
        }
    }
}

@Composable
private fun BackupAppRow(
    app: AppProfile,
    installed: Boolean,
    on: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val context = LocalContext.current
    val state = autoBackupStateFor(app.packageName)
    var deletions by remember { mutableIntStateOf(0) }
    val saved = remember(state, deletions) { AppBackups.read(context, app.packageName) }
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = APP_ROW_MIN_HEIGHT).glassRim(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconAvatar(app = app, size = APP_ICON)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = backupStatus(state, saved, installed, on),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val spec = app.guide?.autoBackup?.takeIf { on && installed }
            val idle = state !is AutoBackupState.Running
            if (spec != null && idle) {
                FilledTonalButton(
                    onClick = { AutoBackupRunner.save(app.packageName, spec) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Back up") }
            }
            if (saved != null && idle) BackupMenu(app, saved) { deletions++ }
            if (saved == null || !idle) Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

// what's saved and what can be done with it. Restoring, by a tap or after a reinstall, goes through the same helper
// as backing up, so with it off a saved backup can only be copied
private fun backupStatus(
    state: AutoBackupState?,
    saved: SavedBackup?,
    installed: Boolean,
    on: Boolean,
): String =
    when {
        state is AutoBackupState.Running -> runningText(state.mode)
        state is AutoBackupState.Failed -> state.reason
        state is AutoBackupState.Done && state.nothingToSave -> "Nothing to save yet"
        saved != null && !on ->
            "Backed up ${relativeTime(saved.savedAtMillis)}. Turn on automatic backups to restore it, or copy it."
        saved != null -> "Backed up ${relativeTime(saved.savedAtMillis)}"
        !installed -> "Not installed"
        else -> "Not backed up yet"
    }

// a saved backup's other uses: copy it to paste into the app's own Import screen, or delete it
@Composable
private fun BackupMenu(
    app: AppProfile,
    saved: SavedBackup,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More for ${app.displayName}'s backup")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, border = rememberGlassRimBorder()) {
            DropdownMenuItem(
                text = { Text("Copy backup") },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    open = false
                    copyBackup(context, app, saved)
                },
            )
            DropdownMenuItem(
                text = { Text("Delete backup") },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    open = false
                    confirmDelete = true
                },
            )
        }
    }
    if (confirmDelete) {
        DeleteBackupDialog(
            app = app,
            onConfirm = {
                AppBackups.delete(context, app.packageName)
                confirmDelete = false
                onDeleted()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DeleteBackupDialog(
    app: AppProfile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KrateDialog(
        header =
            KrateDialogHeader(
                tag = "Delete backup",
                icon = painterResource(R.drawable.ic_delete_sweep_rounded),
                title = "Delete ${app.displayName}'s backup?",
                body =
                    "Krate forgets the settings it saved for ${app.displayName}. The app and the settings it has " +
                        "now aren't touched. This can't be undone.",
            ),
        onDismissRequest = onDismiss,
        footer = {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Keep it") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onConfirm, shape = ShapeCache.rounded16) { Text("Delete") }
        },
    )
}

// settings exports can hold account details, so Android is told to keep them out of its clipboard preview
private fun copyBackup(
    context: Context,
    app: AppProfile,
    saved: SavedBackup,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("${app.displayName} settings", saved.text)
    clip.description.extras = PersistableBundle().apply { putBoolean(EXTRA_IS_SENSITIVE, true) }
    clipboard.setPrimaryClip(clip)
    // Android 13 and later confirm a copy on their own
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copied ${app.displayName}'s settings", Toast.LENGTH_SHORT).show()
    }
}

private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
