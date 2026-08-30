package dev.cl0ud9.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.cl0ud9.manager.ui.navigation.ManagerNavHost
import dev.cl0ud9.manager.ui.theme.ManagerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ManagerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ManagerNavHost()
                }
            }
        }
    }
}
