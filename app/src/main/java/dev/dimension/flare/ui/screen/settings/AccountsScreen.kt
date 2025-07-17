package dev.dimension.flare.ui.screen.settings

import androidx.compose.animation.core.AnimationConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FileCircleExclamation
import compose.icons.fontawesomeicons.solid.Plus
import dev.dimension.flare.R
import dev.dimension.flare.data.model.TabSettings
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.AvatarComponentDefaults
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.listCard
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.isError
import dev.dimension.flare.ui.model.isSuccess
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.presenter.settings.AccountsState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.fornewid.placeholder.material3.placeholder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountsScreen(
    onBack: () -> Unit,
    toLogin: () -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val state by producePresenter {
        accountsPresenter()
    }
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_accounts_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                actions = {
                    IconButton(
                        onClick = {
                            toLogin.invoke()
                        },
                    ) {
                        FAIcon(FontAwesomeIcons.Solid.Plus, contentDescription = null)
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        LazyColumn(
            contentPadding = it,
            modifier =
                Modifier
                    .padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when (val accountState = state.accounts) {
                // TODO: show error
                is UiState.Error -> Unit
                is UiState.Loading -> {
                    items(3) {
                        AccountItem(
                            userState = UiState.Loading(),
                            onClick = {},
                            toLogin = {},
                            modifier =
                                Modifier
                                    .listCard(
                                        index = it,
                                        totalCount = 3,
                                    ),
                        )
                    }
                }

                is UiState.Success -> {
                    items(accountState.data.size) { index ->
                        val (account, data) = accountState.data[index]
                        val swipeState =
                            rememberSwipeToDismissBoxState()

                        LaunchedEffect(swipeState.settledValue) {
                            if (swipeState.settledValue != SwipeToDismissBoxValue.Settled) {
                                delay(AnimationConstants.DefaultDurationMillis.toLong())
                                swipeState.reset()
                                state.logout(account.accountKey)
                            }
                        }
                        SwipeToDismissBox(
                            modifier =
                                Modifier
                                    .listCard(
                                        index = index,
                                        totalCount = accountState.data.size,
                                    ),
                            state = swipeState,
                            backgroundContent = {
                                if (swipeState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .background(color = MaterialTheme.colorScheme.error)
                                                .padding(16.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.settings_accounts_remove),
                                            color = MaterialTheme.colorScheme.onError,
                                        )
                                    }
                                }
                            },
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = data.isSuccess || data.isError,
                        ) {
                            AccountItem(
                                modifier = Modifier,
                                userState = data,
                                onClick = {
                                    state.setActiveAccount(it)
                                },
                                toLogin = toLogin,
                                trailingContent = { user ->
                                    state.activeAccount.onSuccess {
                                        RadioButton(
                                            selected = it.accountKey == user.key,
                                            onClick = {
                                                state.setActiveAccount(user.key)
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun <T : UiUserV2> AccountItem(
    userState: UiState<T>,
    onClick: (MicroBlogKey) -> Unit,
    toLogin: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (UiUserV2) -> Unit = { },
    headlineContent: @Composable (UiUserV2) -> Unit = {
        RichText(text = it.name, maxLines = 1)
    },
    supportingContent: @Composable (UiUserV2) -> Unit = {
        Text(text = it.handle, maxLines = 1)
    },
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    userState
        .onSuccess { data ->
            ListItem(
                headlineContent = {
                    headlineContent.invoke(data)
                },
                modifier =
                    modifier
                        .clickable {
                            onClick.invoke(data.key)
                        },
                leadingContent = {
                    AvatarComponent(data = data.avatar)
                },
                trailingContent = {
                    trailingContent.invoke(data)
                },
                supportingContent = {
                    supportingContent.invoke(data)
                },
                colors = colors,
                shadowElevation = shadowElevation,
                tonalElevation = tonalElevation,
            )
        }.onLoading {
            ListItem(
                headlineContent = {
                    Text(text = "Loading...", modifier = Modifier.placeholder(true))
                },
                modifier = modifier,
                leadingContent = {
                    AvatarComponent(
                        data = null,
                        modifier = Modifier.placeholder(true, shape = CircleShape),
                    )
                },
                supportingContent = {
                    Text(text = "Loading...", modifier = Modifier.placeholder(true))
                },
                colors = colors,
                shadowElevation = shadowElevation,
                tonalElevation = tonalElevation,
            )
        }.onError { throwable ->
            ListItem(
                headlineContent = {
                    if (throwable is LoginExpiredException) {
                        Text(text = stringResource(id = R.string.login_expired))
                    } else {
                        Text(text = stringResource(id = R.string.account_item_error_title))
                    }
                },
                modifier = modifier,
                leadingContent = {
                    FAIcon(
                        FontAwesomeIcons.Solid.FileCircleExclamation,
                        contentDescription = stringResource(id = R.string.account_item_error_title),
                        modifier = Modifier.size(AvatarComponentDefaults.size),
                    )
                },
                supportingContent = {
                    if (throwable is LoginExpiredException) {
                        Text(text = stringResource(id = R.string.login_expired_message))
                    } else {
                        Text(text = stringResource(id = R.string.account_item_error_message))
                    }
                },
                trailingContent =
                    if (throwable is LoginExpiredException) {
                        {
                            TextButton(onClick = toLogin) {
                                Text(text = stringResource(id = R.string.login_expired_relogin))
                            }
                        }
                    } else {
                        null
                    },
                colors = colors,
                shadowElevation = shadowElevation,
                tonalElevation = tonalElevation,
            )
        }
}

@Composable
private fun accountsPresenter(settingsRepository: SettingsRepository = koinInject()) =
    run {
        val scope = rememberCoroutineScope()
        val state =
            remember {
                AccountsPresenter()
            }.invoke()

        object : AccountsState by state {
            fun logout(accountKey: MicroBlogKey) {
                accounts.onSuccess { accountList ->
                    if (accountList.size == 1) {
                        // is Last account
                        scope.launch {
                            settingsRepository.updateTabSettings {
                                TabSettings()
                            }
                        }
                    } else {
                        scope.launch {
                            settingsRepository.updateTabSettings {
                                copy(
                                    secondaryItems =
                                        secondaryItems?.filter {
                                            (it.account as? AccountType.Specific)?.accountKey != accountKey
                                        },
                                    mainTabs =
                                        mainTabs.filter {
                                            (it.account as? AccountType.Specific)?.accountKey != accountKey
                                        },
                                )
                            }
                        }
                    }
                }
                removeAccount(accountKey)
            }
        }
    }
