package dev.cl0ud9.manager.platform

import android.content.Context
import dev.cl0ud9.manager.ManagerApplication

// small helper so screens can reach the container without a DI framework
fun Context.appContainer(): AppContainer = (applicationContext as ManagerApplication).container
