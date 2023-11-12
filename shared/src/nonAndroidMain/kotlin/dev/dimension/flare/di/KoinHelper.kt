package dev.dimension.flare.di

import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ApplicationRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin

object KoinHelper: KoinComponent {
    fun start() {
        startKoin {
            modules(appModule())
        }
    }

    val accountRepository: AccountRepository by inject()
    val applicationRepository: ApplicationRepository by inject()
}