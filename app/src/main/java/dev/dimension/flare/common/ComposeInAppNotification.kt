package dev.dimension.flare.common

import androidx.annotation.StringRes
import dev.dimension.flare.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal sealed interface Notification {
    data class Progress(
        val progress: Int,
        val total: Int,
    ) : Notification {
        val percentage: Float
            get() = progress.toFloat() / total
    }

    data class StringNotification(
        @param:StringRes
        @field:StringRes
        val messageId: Int,
        val success: Boolean,
    ) : Notification
}

internal class ComposeInAppNotification : InAppNotification {
    private val _source = MutableStateFlow(Event<Notification>(null, initialHandled = true))
    val source
        get() = _source.asStateFlow()

    override fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    ) {
        _source.value = Event(Notification.Progress(progress, total))
    }

    override fun onSuccess(message: Message) {
        _source.value = Event(Notification.StringNotification(message.title, success = true))
    }

    override fun onError(
        message: Message,
        throwable: Throwable,
    ) {
        _source.value = Event(Notification.StringNotification(message.title, success = false))
    }
}

private val Message.title
    get() =
        when (this) {
            Message.Compose -> R.string.compose_notification_title
            Message.LoginExpired -> R.string.notification_login_expired
        }
