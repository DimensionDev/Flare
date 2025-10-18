package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleCheck
import compose.icons.fontawesomeicons.solid.CircleExclamation
import dev.chrisbanes.haze.HazeState
import dev.dimension.flare.common.ComposeInAppNotification
import dev.dimension.flare.common.Notification
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun InAppNotificationComponent(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    notification: ComposeInAppNotification = koinInject(),
) {
    val source by notification.source.collectAsState()
    val content = remember(source) { source.getContentIfNotHandled() }

    content?.let {
        when (it) {
            is Notification.Progress -> {
                LinearProgressIndicator(
                    progress = { it.percentage },
                    modifier =
                        modifier
                            .fillMaxWidth(),
                )
            }
            is Notification.StringNotification -> {
                var showNotification by remember { mutableStateOf(false) }
                var showText by remember { mutableStateOf(false) }
                LaunchedEffect(source) {
                    showNotification = true
                    delay(500.milliseconds)
                    showText = true
                    delay(3.seconds)
                    showText = false
                    delay(1.seconds)
                    showNotification = false
                }
                AnimatedVisibility(
                    showNotification,
                    modifier =
                        modifier
                            .systemBarsPadding(),
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    Glassify(
                        shape = RoundedCornerShape(50),
                        shadowElevation = 8.dp,
                        tonalElevation = 8.dp,
                        hazeState = hazeState,
                        modifier =
                            Modifier.clickable {
                                showText = false
                                showNotification = false
                            },
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .padding(12.dp)
                                    .animateContentSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (it.success) {
                                FAIcon(
                                    FontAwesomeIcons.Solid.CircleCheck,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                FAIcon(
                                    FontAwesomeIcons.Solid.CircleExclamation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                            AnimatedVisibility(showText) {
                                Row {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        stringResource(it.messageId, *it.args.toTypedArray()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
