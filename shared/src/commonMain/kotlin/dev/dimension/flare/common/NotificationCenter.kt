package dev.dimension.flare.common

interface NotificationCenter {
    fun notify(data: NotificationData)
}

sealed interface NotificationData

sealed interface ComposeData : NotificationData {
    data class Progress(
        val current: Int,
        val max: Int,
    ) : ComposeData

    data object Success : ComposeData

    data class Error(
        val throwable: Throwable,
    ) : ComposeData
}
