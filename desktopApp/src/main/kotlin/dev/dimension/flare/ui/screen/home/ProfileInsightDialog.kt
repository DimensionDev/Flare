package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Robot
import dev.dimension.flare.Res
import dev.dimension.flare.agent_chat_input_placeholder
import dev.dimension.flare.agent_chat_send
import dev.dimension.flare.feature.agent.presenter.profile.ProfileInsightPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ok
import dev.dimension.flare.profile_insight_analyzing
import dev.dimension.flare.profile_insight_title
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.agent.AgentChatScaffold
import dev.dimension.flare.ui.component.agent.label
import dev.dimension.flare.ui.component.status.UserCompat
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.AccentButton
import io.github.composefluent.component.FluentDialog
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProfileInsightDialog(
    accountType: AccountType,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter("profile_insight_${accountType}_$userKey") {
        remember(accountType, userKey) {
            ProfileInsightPresenter(
                accountType = accountType,
                userKey = userKey,
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
                    text = stringResource(Res.string.profile_insight_title),
                    style = FluentTheme.typography.title,
                )
            }

            AgentChatScaffold(
                messages = state.messages,
                input = state.input,
                isRunning = state.room.isRunning,
                canSend = state.canSend,
                errorMessage = state.room.errorMessage,
                runningTrace = state.room.currentTrace?.label() ?: stringResource(Res.string.profile_insight_analyzing),
                inputPlaceholder = stringResource(Res.string.agent_chat_input_placeholder),
                sendContentDescription = stringResource(Res.string.agent_chat_send),
                onInputChange = state::setInput,
                onSend = state::sendMessage,
                onInputRequestOptionSelected = state::selectInputRequestOption,
                onPostClick = { post ->
                    navigate(Route.StatusDetail(accountType = post.accountType, statusKey = post.statusKey))
                },
                onUserClick = { user ->
                    user.toRoute()?.let(navigate)
                },
                leadingContentItemCount = if (state.profile != null) 1 else 0,
                leadingContent = {
                    state.profile?.let { profile ->
                        item {
                            ProfileInsightUserPreview(
                                profile = profile,
                                onClick = {
                                    navigate(Route.Profile(accountType = accountType, userKey = profile.key))
                                },
                            )
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
internal fun ProfileInsightUserPreview(
    profile: UiProfile,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { base ->
                    if (onClick != null) {
                        base.clickable(onClick = onClick)
                    } else {
                        base
                    }
                }.border(
                    border = BorderStroke(1.dp, FluentTheme.colors.stroke.card.default),
                    shape = RoundedCornerShape(8.dp),
                ),
    ) {
        UserCompat(
            user = profile,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = screenHorizontalPadding,
                        vertical = 12.dp,
                    ),
            onUserClick = {
                onClick?.invoke()
            },
        )
    }
}

private fun UiProfile.toRoute(): Route? =
    when (val event = clickEvent) {
        is ClickEvent.Deeplink -> Route.parse(event.url)
        ClickEvent.Noop -> null
    }
