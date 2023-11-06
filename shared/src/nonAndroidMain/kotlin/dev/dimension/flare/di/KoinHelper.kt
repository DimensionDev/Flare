package dev.dimension.flare.di

import org.koin.core.context.startKoin

object KoinHelper {
    fun start() {
        startKoin {
            modules(appModule())
        }
    }
}