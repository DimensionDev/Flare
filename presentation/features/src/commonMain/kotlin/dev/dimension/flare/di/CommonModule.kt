package dev.dimension.flare.di

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.account.AccountProfileProvider
import dev.dimension.flare.data.account.AccountProfileProviderImpl
import dev.dimension.flare.data.account.AccountRepository
import dev.dimension.flare.data.account.ApplicationRepository
import dev.dimension.flare.data.account.CredentialProvider
import dev.dimension.flare.data.ai.AiCompletionService
import dev.dimension.flare.data.ai.OpenAIService
import dev.dimension.flare.data.database.cache.model.TranslationSettingsProvider
import dev.dimension.flare.data.database.provideAppDatabase
import dev.dimension.flare.data.database.provideCacheDatabase
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineCatalog
import dev.dimension.flare.data.datasource.rss.RssTimelineSpecs
import dev.dimension.flare.data.draft.DraftMediaStore
import dev.dimension.flare.data.draft.DraftRepository
import dev.dimension.flare.data.draft.DraftSendingRecoveryCoordinator
import dev.dimension.flare.data.draft.RestoreDraftUseCase
import dev.dimension.flare.data.draft.SaveDraftUseCase
import dev.dimension.flare.data.draft.SendDraftUseCase
import dev.dimension.flare.data.local.LocalFilterRepository
import dev.dimension.flare.data.local.SearchHistoryRepository
import dev.dimension.flare.data.model.tab.TimelinePersistenceMapper
import dev.dimension.flare.data.model.tab.TimelinePresenterFactory
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.network.rss.Readability
import dev.dimension.flare.data.repository.AccountTabSyncCoordinator
import dev.dimension.flare.data.settings.TranslationSettingsProviderImpl
import dev.dimension.flare.data.translation.OnlinePreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
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
        singleOf(::AccountRepository)
        single<AccountProfileProvider> { AccountProfileProviderImpl(get()) }
        single<CredentialProvider> { get<AccountRepository>() }
        single(createdAtStart = true) { AccountTabSyncCoordinator(get(), get(), get(), get()) }
        single { provideAppDatabase(get()) }
        single { provideCacheDatabase(get()) }
        singleOf(::ApplicationRepository)
        single {
            DraftMediaStore(fileStorage = get())
        }
        single {
            DraftRepository(
                database = get(),
                draftMediaStore = get(),
            )
        }
        single(createdAtStart = true) { DraftSendingRecoveryCoordinator(get(), get()) }
        singleOf(::LocalFilterRepository)
        single { CoroutineScope(PlatformDispatchers.IO) }
        singleOf(::SaveDraftUseCase)
        single {
            val accountRepository = get<AccountRepository>()
            RestoreDraftUseCase(
                draftRepository = get(),
                findAccount = accountRepository::find,
            )
        }
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
        singleOf(::SearchHistoryRepository)
        singleOf(::Readability)
        singleOf(::OpenAIService)
        singleOf(::AiCompletionService)
        single<PreTranslationService> { OnlinePreTranslationService(get(), get(), get(), get()) }
        single<TranslationSettingsProvider> { TranslationSettingsProviderImpl(get()) }
        single { TimelinePresenterFactory(get()) }
        single { TimelinePersistenceMapper(get()) }
        single { TimelineResolver(get(), get()) }
    }
