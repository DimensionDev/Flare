package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.data.account.AccountRepository
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.inject

internal class AccountTimelinePresenter(
    private val accountType: AccountType,
    private val loaderFactory: (service: MicroblogDataSource) -> RemoteLoader<UiTimelineV2>,
) : TimelinePresenter() {
    constructor(
        accountKey: MicroBlogKey,
        loaderFactory: (service: MicroblogDataSource) -> RemoteLoader<UiTimelineV2>,
    ) : this(AccountType.Specific(accountKey), loaderFactory)

    private val accountRepository: AccountRepository by inject()

    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).map(loaderFactory)
    }
}

internal class StandaloneTimelineContext(
    val appDatabase: AppDatabase,
    val cacheDatabase: CacheDatabase,
)

internal class StandaloneTimelinePresenter(
    private val loaderFactory: (context: StandaloneTimelineContext) -> Flow<RemoteLoader<UiTimelineV2>>,
) : TimelinePresenter() {
    private val appDatabase: AppDatabase by inject()
    private val cacheDatabase: CacheDatabase by inject()

    override val loader: Flow<RemoteLoader<UiTimelineV2>> by lazy {
        loaderFactory(
            StandaloneTimelineContext(
                appDatabase = appDatabase,
                cacheDatabase = cacheDatabase,
            ),
        )
    }
}
