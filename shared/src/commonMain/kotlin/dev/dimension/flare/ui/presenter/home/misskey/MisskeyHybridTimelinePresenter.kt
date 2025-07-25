package dev.dimension.flare.ui.presenter.home.misskey

import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class MisskeyHybridTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    override val loader: Flow<BaseTimelineLoader> by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).map { service ->
            require(service is MisskeyDataSource)
            service.hybridTimelineLoader()
        }
    }
}
