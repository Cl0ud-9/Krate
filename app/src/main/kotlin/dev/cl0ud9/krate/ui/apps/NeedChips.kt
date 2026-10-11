package dev.cl0ud9.krate.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cl0ud9.krate.domain.model.AppProfile
import dev.cl0ud9.krate.domain.suggest.appsForNeed
import dev.cl0ud9.krate.domain.suggest.needsFor

// what someone might be after, one chip each ("Music", "Storage"), with "All" to go back; picking one narrows the list
// to the apps that help with it. Only drawn when the shelf has enough on it to need sorting
@Composable
internal fun NeedChips(
    needs: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") })
        }
        items(needs, key = { it }) { need ->
            FilterChip(selected = selected == need, onClick = { onSelect(need) }, label = { Text(need) })
        }
    }
}

// the chips on offer and what they leave in the list; a need that has gone from the shelf (the catalog changed) simply
// stops filtering
internal class NeedFilter(
    val needs: List<String>,
    val selected: String?,
    val shown: List<AppProfile>,
    val onSelect: (String?) -> Unit,
)

@Composable
internal fun rememberNeedFilter(apps: List<AppProfile>): NeedFilter {
    val needs = remember(apps) { needsFor(apps) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = picked?.takeIf { it in needs }
    val shown = remember(apps, selected) { appsForNeed(apps, selected) }
    return NeedFilter(needs, selected, shown) { picked = it }
}

@Composable
internal fun NeedChips(
    filter: NeedFilter,
    modifier: Modifier = Modifier,
) = NeedChips(needs = filter.needs, selected = filter.selected, onSelect = filter.onSelect, modifier = modifier)
