package dev.cl0ud9.krate.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.ui.components.KrateSwitch
import dev.cl0ud9.krate.ui.theme.ShapeCache

// what a setting row shows: its icon, title and a line on what it does
internal class SettingItem(
    val icon: Painter,
    val title: String,
    val subtitle: String,
)

// a setting with a current value shown as a chip; tapping it opens the choices
@Composable
internal fun SettingValueRow(
    item: SettingItem,
    value: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    SettingRowFrame(item = item, shape = shape, onClick = onClick) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            AnimatedContent(targetState = value, transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }, label = "setting-value") { shown ->
                Text(
                    text = shown,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// an on/off setting; the whole row toggles, not just the switch
@Composable
internal fun SettingSwitchRow(
    item: SettingItem,
    checked: Boolean,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRowFrame(
        item = item,
        shape = shape,
        onClick = { onCheckedChange(!checked) },
        trailing = { KrateSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun SettingRowFrame(
    item: SettingItem,
    shape: Shape,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                extra()
            }
            trailing?.invoke()
        }
    }
}

// one pickable choice in an option sheet
internal class SettingOption<T>(
    val value: T,
    val icon: Painter,
    val label: String,
)

// a bottom sheet of choices; the current one is filled in, picking one applies it and closes the sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SettingOptionSheet(
    title: String,
    options: List<SettingOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            options.forEach { option ->
                OptionRow(option = option, isSelected = option.value == selected, onClick = {
                    onSelect(option.value)
                    onDismiss()
                })
            }
        }
    }
}

@Composable
private fun <T> OptionRow(
    option: SettingOption<T>,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (isSelected) colors.primaryContainer else colors.surfaceContainerHigh,
        label = "option",
    )
    val content by animateColorAsState(
        if (isSelected) colors.onPrimaryContainer else colors.onSurface,
        label = "optionContent",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = ShapeCache.smooth24,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(option.icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(text = option.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            SelectionMark(selected = isSelected, color = content)
        }
    }
}
