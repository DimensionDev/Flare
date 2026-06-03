package dev.dimension.flare.di

import dev.dimension.flare.data.repository.AccountTabSyncCoordinator
import dev.dimension.flare.data.repository.DraftSendingRecoveryCoordinator
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun ensureSharedStartupCoordinators() {
    SharedStartupCoordinators.ensureStarted()
}

private object SharedStartupCoordinators : KoinComponent {
    private var started = false

    fun ensureStarted() {
        if (started) return
        get<AccountTabSyncCoordinator>()
        get<DraftSendingRecoveryCoordinator>()
        started = true
    }
}
