package dev.dimension.flare.ui.screen.dm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.dimension.flare.Res
import dev.dimension.flare.dm_list_error
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.dm.UserDMConversationPresenter
import dev.dimension.flare.ui.presenter.invoke
import io.github.composefluent.component.ProgressRing
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UserDMConversationScreen(
    accountType: AccountType,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
    toProfile: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter(key = "UserDMConversationScreen${userKey}$accountType") {
        presenter(
            accountType = accountType,
            userKey = userKey,
        )
    }
    state.roomKey
        .onSuccess {
            DmConversationScreen(
                accountType = accountType,
                roomKey = it,
                onBack = onBack,
                toProfile = toProfile,
            )
        }.onLoading {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                ProgressRing()
            }
        }.onError {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(Res.string.dm_list_error))
            }
        }
}

@Composable
private fun presenter(
    accountType: AccountType,
    userKey: MicroBlogKey,
) = run {
    remember(accountType, userKey) {
        UserDMConversationPresenter(
            accountType = accountType,
            userKey = userKey,
        )
    }.invoke()
}
