package dev.dimension.flare.common

import org.koin.core.annotation.Single

@Single(binds = [InAppNotification::class])
internal class NoopInAppNotification : InAppNotification {
    override fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    ) {
    }

    override fun onSuccess(message: Message) {
    }

    override fun onError(
        message: Message,
        throwable: Throwable,
    ) {
    }
}
