package dev.dimension.flare.di

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.account.AccountRepository
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineCatalog
import dev.dimension.flare.data.datasource.rss.RssTimelineSpecs
import dev.dimension.flare.data.draft.SendDraftUseCase
import dev.dimension.flare.data.model.tab.TimelinePersistenceMapper
import dev.dimension.flare.data.model.tab.TimelinePresenterFactory
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.network.rss.Readability
import dev.dimension.flare.data.repository.AccountTabSyncCoordinator
import dev.dimension.flare.model.SocialPlatformRegistry
import dev.dimension.flare.model.defaultSocialPlatformRegistry
import dev.dimension.flare.ui.presenter.compose.ComposeUseCase
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val commonModule =
    module {
        single<SocialPlatformRegistry> { defaultSocialPlatformRegistry }
        single {
            TimelineCatalog(
                get<SocialPlatformRegistry>().specs.flatMap { it.timelineSpecs } + RssTimelineSpecs.timelineSpecs,
            )
        }
        single(createdAtStart = true) { AccountTabSyncCoordinator(get(), get(), get(), get()) }
        single { CoroutineScope(PlatformDispatchers.IO) }
        single {
            val accountRepository = get<AccountRepository>()
            SendDraftUseCase(
                draftRepository = get(),
                draftMediaStore = get(),
                findAccount = accountRepository::find,
                composeDraft = { account, data, progress ->
                    (accountRepository.getOrCreateDataSource(account) as? AuthenticatedMicroblogDataSource)
                        ?.compose(data = data, progress = progress)
                },
            )
        }
        singleOf(::ComposeUseCase)
        singleOf(::Readability)
        single { TimelinePresenterFactory(get()) }
        single { TimelinePersistenceMapper(get()) }
        single { TimelineResolver(get(), get()) }
    }
