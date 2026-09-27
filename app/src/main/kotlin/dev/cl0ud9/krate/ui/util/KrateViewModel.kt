package dev.cl0ud9.krate.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.cl0ud9.krate.platform.AppContainer
import dev.cl0ud9.krate.platform.appContainer

// cuts the viewModelFactory boilerplate for screens built without a DI framework
@Composable
inline fun <reified VM : ViewModel> krateViewModel(crossinline create: (AppContainer) -> VM): VM {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer() }
    val factory = remember { viewModelFactory { initializer { create(container) } } }
    return viewModel(factory = factory)
}
