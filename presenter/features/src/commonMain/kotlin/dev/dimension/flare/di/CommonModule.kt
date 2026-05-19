package dev.dimension.flare.di

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.provideAppDatabase
import dev.dimension.flare.data.database.provideCacheDatabase
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.network.ai.AiCompletionService
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.network.rss.Readability
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.AccountTabSyncCoordinator
import dev.dimension.flare.data.repository.ApplicationRepository
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.DraftSendingRecoveryCoordinator
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.LegacyTabSettingsV2Migrator
import dev.dimension.flare.data.repository.SearchHistoryRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.data.repository.TabSettingsV2Migrator
import dev.dimension.flare.data.translation.OnlinePreTranslationService
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.model.SocialPlatformRegistry
import dev.dimension.flare.model.defaultSocialPlatformRegistry
import dev.dimension.flare.ui.presenter.compose.ComposeUseCase
import dev.dimension.flare.ui.presenter.compose.RestoreDraftUseCase
import dev.dimension.flare.ui.presenter.compose.SaveDraftUseCase
import dev.dimension.flare.ui.presenter.compose.SendDraftUseCase
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val commonModule =
    module {
        single<SocialPlatformRegistry> { defaultSocialPlatformRegistry }
        singleOf(::AccountRepository)
        single(createdAtStart = true) { AccountTabSyncCoordinator(get(), get(), get(), get()) }
        single { provideAppDatabase(get()) }
        single { provideCacheDatabase(get()) }
        singleOf(::ApplicationRepository)
        single {
            DraftMediaStore(get())
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
        singleOf(::RestoreDraftUseCase)
        single {
            SendDraftUseCase(
                draftRepository = get(),
                accountRepository = get(),
                draftMediaStore = get(),
            )
        }
        singleOf(::ComposeUseCase)
        singleOf(::SearchHistoryRepository)
        single<TabSettingsV2Migrator> { LegacyTabSettingsV2Migrator }
        single { SettingsRepository(get(), get(), get()) }
        singleOf(::Readability)
        singleOf(::OpenAIService)
        singleOf(::AiCompletionService)
        single<PreTranslationService> { OnlinePreTranslationService(get(), get(), get(), get()) }
        single { TimelineResolver(get()) }
    }
