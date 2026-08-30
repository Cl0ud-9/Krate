package dev.cl0ud9.manager

import android.app.Application
import dev.cl0ud9.manager.platform.AppContainer

class ManagerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
