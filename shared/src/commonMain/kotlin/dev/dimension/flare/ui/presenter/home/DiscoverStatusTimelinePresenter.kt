package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class DiscoverStatusTimelinePresenter(
    private val accountType: AccountType,
) : TimelinePresenter(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    override val loader by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).map { service ->
            service.discoverStatuses()
        }
    }
}
