package dev.cl0ud9.krate.platform

import android.content.Context
import dev.cl0ud9.krate.KrateApplication

// small helper so screens can reach the container without a DI framework
fun Context.appContainer(): AppContainer = (applicationContext as KrateApplication).container
