package dev.dimension.flare.common

interface InAppNotification {
    fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    )

    fun onSuccess(message: Message)

    fun onError(
        message: Message,
        throwable: Throwable,
    )
}

enum class Message {
    Compose,
    LoginExpired,
}
