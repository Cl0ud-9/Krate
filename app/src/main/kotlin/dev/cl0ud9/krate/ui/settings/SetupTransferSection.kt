package dev.cl0ud9.krate.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.setup.KrateSetup
import dev.cl0ud9.krate.domain.setup.MAX_SETUP_BYTES
import dev.cl0ud9.krate.domain.setup.SetupReading
import dev.cl0ud9.krate.domain.setup.encodeSetup
import dev.cl0ud9.krate.domain.setup.readSetup
import dev.cl0ud9.krate.platform.AppContainer
import dev.cl0ud9.krate.platform.appContainer
import dev.cl0ud9.krate.platform.setup.SetupPreview
import dev.cl0ud9.krate.ui.components.ButtonRow
import dev.cl0ud9.krate.ui.components.KrateDialog
import dev.cl0ud9.krate.ui.components.KrateDialogHeader
import dev.cl0ud9.krate.ui.components.RevealValue
import dev.cl0ud9.krate.ui.components.Swap
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SETUP_FILE_NAME = "krate-setup.json"

// Krate's own setup in a file: the apps someone tracks themselves, which ones are kept on manual updates, and the
// look and download choices. Saved from here and loaded here, so a new phone starts where the old one stood
@Composable
internal fun SetupTransferSection() {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val scope = rememberCoroutineScope()
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var waiting by rememberSaveable { mutableStateOf<String?>(null) }
    val save =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) scope.launch { message = saveSetup(context, container, uri) }
        }
    val load =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val text = withContext(Dispatchers.IO) { readFile(context, uri) }
                    when (val reading = text?.let(::readSetup) ?: SetupReading.NotASetup) {
                        is SetupReading.Ready -> waiting = text
                        else -> message = refusalText(reading)
                    }
                }
            }
        }
    SetupCard(
        message = message,
        onLoad = { load.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onSave = { save.launch(SETUP_FILE_NAME) },
    )
    waiting?.let { text ->
        val ready = remember(text) { readSetup(text) as? SetupReading.Ready }
        if (ready == null) {
            waiting = null
        } else {
            LoadSetupDialog(
                ready = ready,
                onCancel = { waiting = null },
                onLoad = {
                    waiting = null
                    scope.launch { message = loadSetup(container, ready, scope) }
                },
            )
        }
    }
}

@Composable
private fun SetupCard(
    message: String?,
    onLoad: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded16),
        shape = ShapeCache.rounded16,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Krate's own setup", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text =
                    "Save the apps you track, the ones you keep on manual updates and how Krate looks to a file, " +
                        "and load it on another phone. No passwords or tokens go in it, and none of your apps' own " +
                        "settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // the result drops in under the words, and a new one replaces it in place
            RevealValue(message, gap = 12.dp) {
                Swap(target = it, label = "setup-message") { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            ButtonRow {
                FilledTonalButton(onClick = onLoad) { Text("Load a setup") }
                Button(onClick = onSave) { Text("Save to a file") }
            }
        }
    }
}

@Composable
private fun LoadSetupDialog(
    ready: SetupReading.Ready,
    onCancel: () -> Unit,
    onLoad: () -> Unit,
) {
    val container = LocalContext.current.appContainer()
    // worked out against what is here now, so the dialog says what loading will really do
    val preview by produceState<SetupPreview?>(null, ready) { value = container.setupTransfer.preview(ready.setup) }
    val shown = preview ?: return
    KrateDialog(
        header =
            KrateDialogHeader(
                tag = "Load a setup",
                icon = rememberVectorPainter(Icons.Filled.SettingsBackupRestore),
                title = "Load this setup?",
                body =
                    loadSummary(
                        shown.toAdd.map { it.displayName },
                        ready.setup.manualUpdates.size,
                        shown.alreadyHere,
                        ready.skipped,
                    ),
            ),
        onDismissRequest = onCancel,
        footer = {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onLoad) { Text("Load it") }
        },
    )
}

private suspend fun saveSetup(
    context: Context,
    container: AppContainer,
    uri: Uri,
): String {
    val saved =
        withContext(Dispatchers.IO) {
            writeSetup(context, uri, container.setupTransfer.current(System.currentTimeMillis()))
        }
    return if (saved) "Saved. Keep the file somewhere you can reach from the new phone." else "Couldn't save the file."
}

// puts the setup back, then has the apps just followed fetch their releases and icons now, not at the next check
private suspend fun loadSetup(
    container: AppContainer,
    ready: SetupReading.Ready,
    scope: CoroutineScope,
): String {
    val done = container.setupTransfer.apply(ready.setup)
    scope.launch { runCatching { container.trackedAppsRepository.refresh() } }
    return appliedText(done, ready.skipped)
}

private fun refusalText(reading: SetupReading): String =
    when (reading) {
        is SetupReading.FromANewerKrate -> "That file comes from a newer Krate. Update this one and try again."
        else -> "That doesn't look like a file saved by Krate."
    }

private fun writeSetup(
    context: Context,
    uri: Uri,
    setup: KrateSetup,
): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encodeSetup(setup).toByteArray()) } != null
    }.getOrDefault(false)

// the file's text, or null for one that can't be read or that's too large to be a setup; read no further than the
// limit, so a file of any size costs no more than that
private fun readFile(
    context: Context,
    uri: Uri,
): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(READ_CHUNK)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_SETUP_BYTES) return@use null
                out.write(buffer, 0, read)
            }
            String(out.toByteArray())
        }
    }.getOrNull()

private const val READ_CHUNK = 8 * 1024
