package dev.dimension.flare.common

import androidx.annotation.StringRes
import dev.dimension.flare.R
import dev.dimension.flare.data.repository.LoginExpiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

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
        val args: List<Any> = emptyList(),
    ) : Notification
}

@Single(binds = [InAppNotification::class])
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
        _source.value = Event(Notification.StringNotification(message.successTitle, success = true))
    }

    fun message(
        @StringRes messageId: Int,
    ) {
        _source.value = Event(Notification.StringNotification(messageId, success = true))
    }

    override fun onError(
        message: Message,
        throwable: Throwable,
    ) {
        _source.value =
            Event(
                Notification.StringNotification(
                    message.errorTitle,
                    success = false,
                    args =
                        listOfNotNull(
                            if (throwable is LoginExpiredException) {
                                throwable.accountKey
                            } else {
                                null
                            },
                        ),
                ),
            )
    }
}

private val Message.successTitle
    get() =
        when (this) {
            Message.Compose -> R.string.compose_notification_success
            Message.LoginExpired -> R.string.notification_login_expired
        }

private val Message.errorTitle
    get() =
        when (this) {
            Message.Compose -> R.string.compose_notification_error
            Message.LoginExpired -> R.string.notification_login_expired
        }
