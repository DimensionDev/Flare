package dev.dimension.flare.di

import dev.dimension.flare.data.database.provideAppDatabase
import dev.dimension.flare.data.database.provideCacheDatabase
import dev.dimension.flare.data.network.ai.OpenAIService
import dev.dimension.flare.data.network.rss.Readability
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ApplicationRepository
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.data.repository.SearchHistoryRepository
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
        singleOf(::provideAppDatabase)
        singleOf(::provideCacheDatabase)
        singleOf(::ApplicationRepository)
        singleOf(::DraftRepository)
        singleOf(::DraftMediaStore)
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
        singleOf(::Readability)
        singleOf(::OpenAIService)
    }
