package dev.dimension.flare.ui.screen.dm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eygraber.compose.placeholder.material3.placeholder
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.dm.UserDMConversationPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.home.NavigationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserDMConversationScreen(
    accountType: AccountType,
    userKey: MicroBlogKey,
    onBack: () -> Unit,
    navigationState: NavigationState,
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
            DMConversationScreen(
                accountType = accountType,
                roomKey = it,
                onBack = onBack,
                navigationState = navigationState,
                toProfile = toProfile,
            )
        }.onLoading {
            FlareScaffold(
                topBar = {
                    FlareTopAppBar(
                        title = {
                            Text(
                                "Loading...",
                                modifier =
                                    Modifier
                                        .placeholder(true),
                            )
                        },
                        navigationIcon = {
                            BackButton(onBack = onBack)
                        },
                    )
                },
            ) {
                Box(
                    modifier = Modifier.padding(it).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }.onError {
            FlareScaffold(
                topBar = {
                    FlareTopAppBar(
                        title = {
                            Text(
                                "Loading...",
                                modifier =
                                    Modifier
                                        .placeholder(true),
                            )
                        },
                        navigationIcon = {
                            BackButton(onBack = onBack)
                        },
                    )
                },
            ) {
                Box(
                    modifier = Modifier.padding(it).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(R.string.dm_list_error))
                }
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
