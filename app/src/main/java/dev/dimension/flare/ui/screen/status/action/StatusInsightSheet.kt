package dev.dimension.flare.ui.screen.status.action

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.feature.agent.common.AgentPhase
import dev.dimension.flare.feature.agent.common.AgentToolKey
import dev.dimension.flare.feature.agent.common.AgentTrace
import dev.dimension.flare.feature.agent.presenter.status.StatusInsightPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.agent.AgentChatScaffold
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusInsightSheet(
    accountType: AccountType,
    statusKey: MicroBlogKey,
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

    val title = stringResource(id = R.string.status_insight_title)

    AgentChatScaffold(
        messages = state.messages,
        input = state.input,
        isRunning = state.isRunning,
        canSend = state.canSend,
        error = state.error,
        runningTrace = state.currentTrace?.label() ?: stringResource(id = R.string.status_insight_analyzing),
        inputPlaceholder = stringResource(id = R.string.status_insight_input_placeholder),
        sendContentDescription = stringResource(id = R.string.status_insight_send),
        messageText = StatusInsightPresenter.Message::text,
        isUserMessage = { it is StatusInsightPresenter.Message.User },
        onInputChange = state::setInput,
        onSend = state::sendMessage,
        modifier = modifier,
        topBar = {
            FlareTopAppBar(
                title = {
                    Text(text = title)
                },
                windowInsets = WindowInsets(0),
            )
        },
        reserveBottomBarHeight = false,
        leadingContentItemCount = if (state.post != null) 1 else 0,
        leadingContent = {
            state.post?.let { post ->
                item {
                    StatusInsightPostPreview(post = post)
                }
            }
        },
    )
}

@Composable
internal fun StatusInsightPostPreview(post: UiTimelineV2.Post) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                stringResource(id = R.string.status_insight_trace_loading_post_context)
            }

            AgentPhase.PostContextLoaded -> {
                stringResource(id = R.string.status_insight_trace_post_context_loaded)
            }

            AgentPhase.PreparingImages -> {
                stringResource(id = R.string.status_insight_trace_preparing_images)
            }

            AgentPhase.ImagesUnsupportedFallback -> {
                stringResource(id = R.string.status_insight_trace_images_unsupported_fallback)
            }

            AgentPhase.AgentStarted -> {
                stringResource(id = R.string.status_insight_trace_agent_started)
            }

            AgentPhase.StrategyStarted -> {
                stringResource(id = R.string.status_insight_trace_strategy_started)
            }

            AgentPhase.StrategyCompleted -> {
                stringResource(id = R.string.status_insight_trace_strategy_completed)
            }

            AgentPhase.SubgraphStarted -> {
                stringResource(id = R.string.status_insight_trace_subgraph_started)
            }

            AgentPhase.SubgraphCompleted -> {
                stringResource(id = R.string.status_insight_trace_subgraph_completed)
            }

            AgentPhase.SubgraphFailed -> {
                stringResource(id = R.string.status_insight_trace_subgraph_failed)
            }

            AgentPhase.AskingModel -> {
                stringResource(
                    id = R.string.status_insight_trace_asking_model,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ModelResponseReceived -> {
                stringResource(id = R.string.status_insight_trace_model_response_received)
            }

            AgentPhase.StreamingStarted -> {
                stringResource(
                    id = R.string.status_insight_trace_streaming_started,
                    detail.orEmpty(),
                )
            }

            AgentPhase.StreamingResponse -> {
                stringResource(id = R.string.status_insight_trace_streaming_response)
            }

            AgentPhase.StreamingCompleted -> {
                stringResource(id = R.string.status_insight_trace_streaming_completed)
            }

            AgentPhase.StreamingFailed -> {
                stringResource(id = R.string.status_insight_trace_streaming_failed)
            }

            AgentPhase.RunningStep -> {
                stringResource(id = R.string.status_insight_trace_running_step)
            }

            AgentPhase.StepCompleted -> {
                stringResource(id = R.string.status_insight_trace_step_completed)
            }

            AgentPhase.StepFailed -> {
                stringResource(id = R.string.status_insight_trace_step_failed)
            }

            AgentPhase.ToolCallStarted -> {
                stringResource(
                    id = R.string.status_insight_trace_tool_call_started,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolCallCompleted -> {
                stringResource(
                    id = R.string.status_insight_trace_tool_call_completed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolValidationFailed -> {
                stringResource(
                    id = R.string.status_insight_trace_tool_validation_failed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.ToolCallFailed -> {
                stringResource(
                    id = R.string.status_insight_trace_tool_call_failed,
                    detail.orEmpty(),
                )
            }

            AgentPhase.AgentCompleted -> {
                stringResource(id = R.string.status_insight_trace_agent_completed)
            }

            AgentPhase.AgentFailed -> {
                stringResource(id = R.string.status_insight_trace_agent_failed)
            }

            AgentPhase.AgentClosing -> {
                stringResource(id = R.string.status_insight_trace_agent_closing)
            }
        }

@Composable
private fun AgentToolKey.label(): String =
    when (this) {
        AgentToolKey.LoadStatusContextStarted -> {
            stringResource(id = R.string.status_insight_trace_tool_load_status_context_started)
        }

        AgentToolKey.LoadStatusContextCompleted -> {
            stringResource(id = R.string.status_insight_trace_tool_load_status_context_completed)
        }

        AgentToolKey.LoadStatusContextValidationFailed -> {
            stringResource(id = R.string.status_insight_trace_tool_load_status_context_validation_failed)
        }

        AgentToolKey.LoadStatusContextFailed -> {
            stringResource(id = R.string.status_insight_trace_tool_load_status_context_failed)
        }

        AgentToolKey.SearchPostsStarted,
        AgentToolKey.SearchUsersStarted,
        -> {
            stringResource(id = R.string.status_insight_trace_tool_search_status_started)
        }

        AgentToolKey.SearchPostsCompleted,
        AgentToolKey.SearchUsersCompleted,
        -> {
            stringResource(id = R.string.status_insight_trace_tool_search_status_completed)
        }

        AgentToolKey.SearchPostsValidationFailed,
        AgentToolKey.SearchUsersValidationFailed,
        -> {
            stringResource(id = R.string.status_insight_trace_tool_search_status_validation_failed)
        }

        AgentToolKey.SearchPostsFailed,
        AgentToolKey.SearchUsersFailed,
        -> {
            stringResource(id = R.string.status_insight_trace_tool_search_status_failed)
        }
    }
