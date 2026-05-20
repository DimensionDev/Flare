package dev.dimension.flare.di

import dev.dimension.flare.data.draft.DraftMediaStore
import dev.dimension.flare.data.draft.DraftRepository
import dev.dimension.flare.data.draft.DraftSendingRecoveryCoordinator
import dev.dimension.flare.data.draft.SaveDraftUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

public val draftDataModule: Module =
    module {
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
        singleOf(::SaveDraftUseCase)
    }
