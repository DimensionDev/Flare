package dev.dimension.flare

import android.app.Application
import dev.dimension.flare.di.androidModule
import dev.dimension.flare.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(appModule() + androidModule)
        }
    }
}
