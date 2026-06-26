package dev.dimension.flare.di

import dev.dimension.flare.data.repository.AccountTabSyncCoordinator
import dev.dimension.flare.data.repository.DraftSendingRecoveryCoordinator
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public fun ensureSharedStartupCoordinators() {
    SharedStartupCoordinators.ensureStarted()
}

private object SharedStartupCoordinators {
    private var started = false

    fun ensureStarted() {
        if (started) return
        koinGet<AccountTabSyncCoordinator>()
        koinGet<DraftSendingRecoveryCoordinator>()
        started = true
    }
}
