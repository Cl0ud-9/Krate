package dev.cl0ud9.manager.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.cl0ud9.manager.ui.apps.AppsScreen
import dev.cl0ud9.manager.ui.home.HomeScreen
import dev.cl0ud9.manager.ui.settings.SettingsScreen
import dev.cl0ud9.manager.ui.updates.UpdatesScreen

@Composable
fun ManagerNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                ManagerDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        // placeholder dot, swap for real iconography later
                        icon = {
                            Box(
                                modifier =
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ManagerDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ManagerDestination.HOME.route) { HomeScreen() }
            composable(ManagerDestination.APPS.route) { AppsScreen() }
            composable(ManagerDestination.UPDATES.route) { UpdatesScreen() }
            composable(ManagerDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
