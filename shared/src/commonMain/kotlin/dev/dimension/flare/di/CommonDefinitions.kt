package dev.dimension.flare.di

import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.DraftMediaStore
import dev.dimension.flare.data.repository.DraftRepository
import dev.dimension.flare.ui.presenter.compose.SendDraftUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.annotation.Single

@Single
internal fun coroutineScope(): CoroutineScope = CoroutineScope(Dispatchers.IO)

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
