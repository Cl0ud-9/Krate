package dev.cl0ud9.manager.ui.updates

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// pending updates with individual actions and Update All - section 30
@Composable
fun UpdatesScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Updates", style = MaterialTheme.typography.headlineMedium)
    }
}
