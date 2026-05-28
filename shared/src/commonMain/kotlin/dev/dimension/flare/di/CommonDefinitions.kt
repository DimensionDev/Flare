package dev.dimension.flare.di

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.ui.presenter.compose.SendDraftUseCase
import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.Single

@Single
internal fun coroutineScope(): CoroutineScope = CoroutineScope(PlatformDispatchers.IO)

@Single
internal fun sendDraftUseCase(
    draftRepository: DraftRepository,
    accountRepository: AccountRepository,
    draftMediaStore: DraftMediaStore,
): SendDraftUseCase =
    SendDraftUseCase(
        draftRepository = draftRepository,
        accountRepository = accountRepository,
        draftMediaStore = draftMediaStore,
    )
