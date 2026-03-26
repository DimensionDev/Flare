package dev.dimension.flare.di

import dev.dimension.flare.data.database.provideAppDatabase
import dev.dimension.flare.data.database.provideCacheDatabase
import dev.dimension.flare.data.datasource.nostr.DatabaseNostrCache
import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.network.rss.Readability
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.AccountTabSyncCoordinator
import dev.dimension.flare.data.repository.ApplicationRepository
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.DraftSendingRecoveryCoordinator
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.SearchHistoryRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.ui.presenter.compose.ComposeUseCase
import dev.dimension.flare.ui.presenter.compose.RestoreDraftUseCase
import dev.dimension.flare.ui.presenter.compose.SaveDraftUseCase
import dev.dimension.flare.ui.presenter.compose.SendDraftUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val commonModule =
    module {
        singleOf(::AccountRepository)
        single(createdAtStart = true) { AccountTabSyncCoordinator(get(), get(), get()) }
        singleOf(::provideAppDatabase)
        singleOf(::provideCacheDatabase)
        single<NostrCache> { DatabaseNostrCache(get()) }
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
        single { CoroutineScope(Dispatchers.IO) }
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
        singleOf(::SettingsRepository)
        singleOf(::Readability)
        singleOf(::OpenAIService)
    }
