package dev.dimension.flare.ui.screen.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.feature.agent.presenter.profile.ProfileInsightPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.agent.AgentChatSheetScaffold
import dev.dimension.flare.ui.component.agent.label
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.route.Route
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
        modifier = modifier,
    )
}

private fun UiProfile.toRoute(): Route? =
    when (val event = clickEvent) {
        is ClickEvent.Deeplink -> Route.parse(event.url)
        ClickEvent.Noop -> null
    }
