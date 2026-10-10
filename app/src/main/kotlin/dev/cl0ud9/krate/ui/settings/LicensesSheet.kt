package dev.cl0ud9.krate.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.R
import dev.cl0ud9.krate.ui.components.KrateSheet
import dev.cl0ud9.krate.ui.navigation.glassRim
import dev.cl0ud9.krate.ui.theme.ShapeCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CHEVRON_EXPANDED_DEGREES = 90f

// what Krate is under, and what it's built with under theirs; a card opens to its full license text in place
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicensesSheet(onDismiss: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    KrateSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Licences",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            LICENSES.forEach { entry ->
                LicenseCard(
                    entry = entry,
                    expanded = expanded == entry.name,
                    onToggle = { expanded = if (expanded == entry.name) null else entry.name },
                )
            }
        }
    }
}

@Composable
private fun LicenseCard(
    entry: LicenseEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val chevron by animateFloatAsState(if (expanded) CHEVRON_EXPANDED_DEGREES else 0f, label = "licenseChevron")
    Surface(
        modifier = Modifier.fillMaxWidth().glassRim(ShapeCache.rounded20),
        shape = ShapeCache.rounded20,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onToggle)
                        .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = entry.license,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = entry.use,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right_rounded),
                    contentDescription = if (expanded) "Hide licence" else "Show licence",
                    modifier = Modifier.rotate(chevron),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                LicenseText(entry)
            }
        }
    }
}

@Composable
private fun LicenseText(entry: LicenseEntry) {
    val context = LocalContext.current
    val text by produceState<String?>(initialValue = null, entry) {
        value = withContext(Dispatchers.IO) { runCatching { readLicenseText(context, entry) }.getOrNull() }
    }
    Column {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SelectionContainer {
            Text(
                text = text.orEmpty(),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
