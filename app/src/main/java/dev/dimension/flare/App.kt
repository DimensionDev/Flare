package dev.dimension.flare

import android.app.Application
import com.moriatsushi.koject.Koject
import com.moriatsushi.koject.android.application
import com.moriatsushi.koject.start


class App: Application() {
    override fun onCreate() {
        super.onCreate()
        Koject.start {
            application(this@App)
        }
    }
}