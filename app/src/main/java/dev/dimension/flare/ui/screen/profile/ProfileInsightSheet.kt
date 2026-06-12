package dev.dimension.flare.ui.screen.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.feature.agent.presenter.profile.ProfileInsightPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.agent.AgentChatSheetScaffold
import dev.dimension.flare.ui.component.agent.label
import dev.dimension.flare.ui.component.status.UserCompat
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun ProfileInsightSheet(
    accountType: AccountType,
    userKey: MicroBlogKey,
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

    AgentChatSheetScaffold(
        messages = state.messages,
        input = state.input,
        isRunning = state.room.isRunning,
        canSend = state.canSend,
        errorMessage = state.room.errorMessage,
        runningTrace = state.room.currentTrace?.label() ?: stringResource(id = R.string.profile_insight_analyzing),
        inputPlaceholder = stringResource(id = R.string.agent_chat_input_placeholder),
        sendContentDescription = stringResource(id = R.string.agent_chat_send),
        onInputChange = state::setInput,
        onSend = state::sendMessage,
        onInputRequestOptionSelected = state::selectInputRequestOption,
        onPostClick = { post ->
            navigate(Route.Status.Detail(statusKey = post.statusKey, accountType = post.accountType))
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
                            navigate(Route.Profile.User(accountType = accountType, userKey = profile.key))
                        },
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
internal fun ProfileInsightUserPreview(
    profile: UiProfile,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { base ->
                    if (onClick != null) {
                        base.clickable(onClick = onClick)
                    } else {
                        base
                    }
                },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
