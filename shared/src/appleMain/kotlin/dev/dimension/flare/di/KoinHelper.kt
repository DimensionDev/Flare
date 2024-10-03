package dev.dimension.flare.di

import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ApplicationRepository
import dev.dimension.flare.ui.presenter.compose.ComposeUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.dsl.binds
import org.koin.dsl.module

object KoinHelper : KoinComponent {
    fun start(inAppNotification: InAppNotification) {
        startKoin {
            modules(appModule())
            modules(
                module {
                    single {
                        inAppNotification
                    } binds arrayOf(InAppNotification::class)
                },
            )
        }
    }

    val accountRepository: AccountRepository by inject()
    val applicationRepository: ApplicationRepository by inject()

    val composeUseCase: ComposeUseCase by inject()
}
