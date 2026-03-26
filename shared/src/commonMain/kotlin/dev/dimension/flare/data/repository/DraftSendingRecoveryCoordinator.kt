package dev.dimension.flare.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DraftSendingRecoveryCoordinator(
    private val draftRepository: DraftRepository,
    coroutineScope: CoroutineScope,
) {
    init {
        coroutineScope.launch {
            draftRepository.markSendingAsFailed()
        }
    }
}
