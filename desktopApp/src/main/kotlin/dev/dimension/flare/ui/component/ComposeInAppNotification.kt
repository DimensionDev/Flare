package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.dimension.flare.LocalWindowPadding
import dev.dimension.flare.Res
import dev.dimension.flare.common.Event
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.compose_notification_title
import dev.dimension.flare.notification_login_expired
import io.github.composefluent.component.InfoBar
import io.github.composefluent.component.InfoBarSeverity
import io.github.composefluent.component.ProgressBar
import io.github.composefluent.component.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds

internal sealed interface Notification {
    data class Progress(
        val progress: Int,
        val total: Int,
    ) : Notification {
        val percentage: Float
            get() = progress.toFloat() / total
    }

    data class StringNotification(
        val messageId: StringResource,
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
            Message.Compose -> Res.string.compose_notification_title
            Message.LoginExpired -> Res.string.notification_login_expired
        }

@Composable
internal fun InAppNotificationComponent(
    modifier: Modifier = Modifier,
    notification: ComposeInAppNotification = koinInject(),
) {
    val source by notification.source.collectAsState()
    val content = remember(source) { source.getContentIfNotHandled() }

    content?.let {
        when (it) {
            is Notification.Progress -> {
                ProgressBar(
                    progress = it.percentage,
                    modifier =
                        modifier
                            .fillMaxWidth(),
                )
            }
            is Notification.StringNotification -> {
                var showNotification by remember { mutableStateOf(false) }
                LaunchedEffect(source) {
                    showNotification = true
                    delay(5.seconds)
                    showNotification = false
                }
                AnimatedVisibility(
                    showNotification,
                    modifier =
                        modifier
                            .padding(
                                LocalWindowPadding.current,
                            ),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    InfoBar(
                        title = {
                            Text(
                                stringResource(it.messageId),
                            )
                        },
                        message = {
                        },
                        severity =
                            if (it.success) {
                                InfoBarSeverity.Success
                            } else {
                                InfoBarSeverity.Critical
                            },
                    )
                }
            }
        }
    }
}
