package dev.dimension.flare.ui.screen.status.action

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.Res
import dev.dimension.flare.agent_chat_input_placeholder
import dev.dimension.flare.agent_chat_send
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.feature.agent.common.AgentPhase
import dev.dimension.flare.feature.agent.common.AgentToolKey
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.feature.agent.presenter.status.StatusInsightPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ok
import dev.dimension.flare.status_insight_analyzing
import dev.dimension.flare.status_insight_error
import dev.dimension.flare.status_insight_title
import dev.dimension.flare.status_insight_trace_agent_closing
import dev.dimension.flare.status_insight_trace_agent_completed
import dev.dimension.flare.status_insight_trace_agent_failed
import dev.dimension.flare.status_insight_trace_agent_started
import dev.dimension.flare.status_insight_trace_asking_model
import dev.dimension.flare.status_insight_trace_images_unsupported_fallback
import dev.dimension.flare.status_insight_trace_loading_post_context
import dev.dimension.flare.status_insight_trace_model_response_received
import dev.dimension.flare.status_insight_trace_post_context_loaded
import dev.dimension.flare.status_insight_trace_preparing_images
import dev.dimension.flare.status_insight_trace_running_step
import dev.dimension.flare.status_insight_trace_step_completed
import dev.dimension.flare.status_insight_trace_step_failed
import dev.dimension.flare.status_insight_trace_strategy_completed
import dev.dimension.flare.status_insight_trace_strategy_started
import dev.dimension.flare.status_insight_trace_streaming_completed
import dev.dimension.flare.status_insight_trace_streaming_failed
import dev.dimension.flare.status_insight_trace_streaming_response
import dev.dimension.flare.status_insight_trace_streaming_started
import dev.dimension.flare.status_insight_trace_subgraph_completed
import dev.dimension.flare.status_insight_trace_subgraph_failed
import dev.dimension.flare.status_insight_trace_subgraph_started
import dev.dimension.flare.status_insight_trace_tool_call_completed
import dev.dimension.flare.status_insight_trace_tool_call_failed
import dev.dimension.flare.status_insight_trace_tool_call_started
import dev.dimension.flare.status_insight_trace_tool_load_status_context_completed
import dev.dimension.flare.status_insight_trace_tool_load_status_context_failed
import dev.dimension.flare.status_insight_trace_tool_load_status_context_started
import dev.dimension.flare.status_insight_trace_tool_load_status_context_validation_failed
import dev.dimension.flare.status_insight_trace_tool_search_status_completed
import dev.dimension.flare.status_insight_trace_tool_search_status_failed
import dev.dimension.flare.status_insight_trace_tool_search_status_started
import dev.dimension.flare.status_insight_trace_tool_search_status_validation_failed
import dev.dimension.flare.status_insight_trace_tool_validation_failed
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.agent.AgentChatScaffold
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.LocalContentColor
import io.github.composefluent.LocalTextStyle
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.FluentDialog
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StatusInsightDialog(
    accountType: AccountType,
    statusKey: MicroBlogKey,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter("status_insight_${accountType}_$statusKey") {
        remember(accountType, statusKey) {
            StatusInsightPresenter(
                accountType = accountType,
                statusKey = statusKey,
            )
        }.invoke()
    }

    FluentDialog(
        visible = true,
    ) {
        Column(
            modifier =
                modifier
                    .onKeyEvent {
                        if (it.key == Key.Escape) {
                            onBack()
                            true
                        } else {
                            false
                        }
                    }.width(560.dp)
                    .heightIn(max = 720.dp)
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Robot,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(Res.string.status_insight_title),
                    style = FluentTheme.typography.title,
                )
            }

            AgentChatScaffold(
                messages = state.messages,
                input = state.input,
                isRunning = state.isRunning,
                canSend = state.canSend,
                error = state.error,
                runningTrace = state.currentTrace?.label() ?: stringResource(Res.string.status_insight_analyzing),
                inputPlaceholder = stringResource(Res.string.agent_chat_input_placeholder),
                sendContentDescription = stringResource(Res.string.agent_chat_send),
                messageText = StatusInsightPresenter.Message::text,
                isUserMessage = { it is StatusInsightPresenter.Message.User },
                onInputChange = state::setInput,
                onSend = state::sendMessage,
                leadingContentItemCount = if (state.post != null) 1 else 0,
                leadingContent = {
                    state.post?.let { post ->
                        item {
                            StatusInsightPostPreview(post = post)
                        }
                    }
                },
                modifier =
                    Modifier
                        .weight(1f, fill = true),
            )

            AccentButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.ok))
            }
        }
    }
}

@Composable
internal fun StatusInsightPostPreview(post: UiTimelineV2.Post) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(
                    border = BorderStroke(1.dp, FluentTheme.colors.stroke.card.default),
                    shape = RoundedCornerShape(8.dp),
                ),
    ) {
        CompositionLocalProvider(
            LocalTimelineAppearance provides
                LocalTimelineAppearance.current.copy(
                    showMedia = false,
                    expandMediaSize = false,
                    showLinkPreview = false,
                    postActionStyle = PostActionStyle.Hidden,
                ),
        ) {
            CommonStatusComponent(
                item = post,
                modifier =
                    Modifier
                        .padding(
                            horizontal = screenHorizontalPadding,
                            vertical = 8.dp,
                        ).fillMaxWidth(),
                isQuote = true,
                maxLines = 3,
            )
        }
    }
}

@Composable
private fun AgentTrace.label(): String =
    toolKey?.label()
        ?: when (phase) {
            AgentPhase.LoadingPostContext -> {
                stringResource(Res.string.status_insight_trace_loading_post_context)
            }

            AgentPhase.PostContextLoaded -> {
                stringResource(Res.string.status_insight_trace_post_context_loaded)
            }

            AgentPhase.PreparingImages -> {
                stringResource(Res.string.status_insight_trace_preparing_images)
            }

            AgentPhase.ImagesUnsupportedFallback -> {
                stringResource(Res.string.status_insight_trace_images_unsupported_fallback)
            }

            AgentPhase.AgentStarted -> {
                stringResource(Res.string.status_insight_trace_agent_started)
            }

            AgentPhase.StrategyStarted -> {
                stringResource(Res.string.status_insight_trace_strategy_started)
            }

            AgentPhase.StrategyCompleted -> {
                stringResource(Res.string.status_insight_trace_strategy_completed)
            }

            AgentPhase.SubgraphStarted -> {
                stringResource(Res.string.status_insight_trace_subgraph_started)
            }

            AgentPhase.SubgraphCompleted -> {
                stringResource(Res.string.status_insight_trace_subgraph_completed)
            }

            AgentPhase.SubgraphFailed -> {
                stringResource(Res.string.status_insight_trace_subgraph_failed)
            }

            AgentPhase.AskingModel -> {
                stringResource(
                    Res.string.status_insight_trace_asking_model,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ModelResponseReceived -> {
                stringResource(Res.string.status_insight_trace_model_response_received)
            }

            AgentPhase.StreamingStarted -> {
                stringResource(
                    Res.string.status_insight_trace_streaming_started,
                    detail.orEmpty(),
                )
            }

            AgentPhase.StreamingResponse -> {
                stringResource(Res.string.status_insight_trace_streaming_response)
            }

            AgentPhase.StreamingCompleted -> {
                stringResource(Res.string.status_insight_trace_streaming_completed)
            }

            AgentPhase.StreamingFailed -> {
                stringResource(Res.string.status_insight_trace_streaming_failed)
            }

            AgentPhase.RunningStep -> {
                stringResource(Res.string.status_insight_trace_running_step)
            }

            AgentPhase.StepCompleted -> {
                stringResource(Res.string.status_insight_trace_step_completed)
            }

            AgentPhase.StepFailed -> {
                stringResource(Res.string.status_insight_trace_step_failed)
            }

            AgentPhase.ToolCallStarted -> {
                stringResource(
                    Res.string.status_insight_trace_tool_call_started,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolCallCompleted -> {
                stringResource(
                    Res.string.status_insight_trace_tool_call_completed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolValidationFailed -> {
                stringResource(
                    Res.string.status_insight_trace_tool_validation_failed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolCallFailed -> {
                stringResource(
                    Res.string.status_insight_trace_tool_call_failed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.AgentCompleted -> {
                stringResource(Res.string.status_insight_trace_agent_completed)
            }

            AgentPhase.AgentFailed -> {
                stringResource(Res.string.status_insight_trace_agent_failed)
            }

            AgentPhase.AgentClosing -> {
                stringResource(Res.string.status_insight_trace_agent_closing)
            }
        }

@Composable
private fun AgentToolKey.label(): String =
    when (this) {
        AgentToolKey.LoadStatusContextStarted -> {
            stringResource(Res.string.status_insight_trace_tool_load_status_context_started)
        }

        AgentToolKey.LoadStatusContextCompleted -> {
            stringResource(Res.string.status_insight_trace_tool_load_status_context_completed)
        }

        AgentToolKey.LoadStatusContextValidationFailed -> {
            stringResource(Res.string.status_insight_trace_tool_load_status_context_validation_failed)
        }

        AgentToolKey.LoadStatusContextFailed -> {
            stringResource(Res.string.status_insight_trace_tool_load_status_context_failed)
        }

        AgentToolKey.SearchPostsStarted,
        AgentToolKey.SearchUsersStarted,
        -> {
            stringResource(Res.string.status_insight_trace_tool_search_status_started)
        }

        AgentToolKey.SearchPostsCompleted,
        AgentToolKey.SearchUsersCompleted,
        -> {
            stringResource(Res.string.status_insight_trace_tool_search_status_completed)
        }

        AgentToolKey.SearchPostsValidationFailed,
        AgentToolKey.SearchUsersValidationFailed,
        -> {
            stringResource(Res.string.status_insight_trace_tool_search_status_validation_failed)
        }

        AgentToolKey.SearchPostsFailed,
        AgentToolKey.SearchUsersFailed,
        -> {
            stringResource(Res.string.status_insight_trace_tool_search_status_failed)
        }
    }

@Composable
private fun StatusInsightCurrentTrace(trace: String) {
    val transition = rememberInfiniteTransition()
    val shimmerOffset by transition.animateFloat(
        initialValue = -240f,
        targetValue = 480f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
    )
    val color = LocalContentColor.current
    val shimmerBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    color.copy(alpha = 0.35f),
                    color,
                    color.copy(alpha = 0.35f),
                ),
            start = Offset(shimmerOffset, 0f),
            end = Offset(shimmerOffset + 180f, 0f),
        )
    BasicText(
        text = trace,
        style =
            LocalTextStyle.current.merge(
                TextStyle(brush = shimmerBrush),
            ),
    )
}
