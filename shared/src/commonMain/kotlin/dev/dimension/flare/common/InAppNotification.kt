package dev.dimension.flare.common

public interface InAppNotification {
    public fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    )

    public fun onSuccess(message: Message)

    public fun onError(
        message: Message,
        throwable: Throwable,
    )
}

public enum class Message {
    Compose,
    LoginExpired,
}
