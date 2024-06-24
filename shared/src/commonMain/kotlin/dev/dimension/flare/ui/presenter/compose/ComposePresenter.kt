package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.repository.accountProvider
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.data.repository.allAccountsPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.mapNotNull
import dev.dimension.flare.ui.model.merge
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.settings.ImmutableListWrapper
import dev.dimension.flare.ui.presenter.settings.toImmutableListWrapper
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject

class ComposePresenter(
    private val accountType: AccountType,
    private val status: ComposeStatus? = null,
) : PresenterBase<ComposeState>() {
    @Composable
    override fun body(): ComposeState {
        val accountState by accountProvider(accountType = accountType)
        val accounts by allAccountsPresenter()
        val selectedAccounts =
            remember {
                mutableStateListOf<UiAccount>()
            }
        accountState.onSuccess {
            LaunchedEffect(Unit) {
                selectedAccounts.add(it)
            }
        }
        val replyState =
            status?.let { status ->
                remember(status.statusKey) {
                    StatusPresenter(accountType = accountType, statusKey = status.statusKey)
                }.body().status
            }
        val allUsers =
            accounts.flatMap { data ->
                accountState
                    .flatMap { current ->
                        replyState?.map {
                            current to listOf(it.platformType)
                        } ?: UiState.Success(current to PlatformType.entries.toList())
                    }.map { (current, platforms) ->
                        data
                            .sortedBy {
                                it.accountKey != current.accountKey
                            }.filter {
                                it.platformType in platforms
                            }.map { account ->
                                accountServiceProvider(accountType = AccountType.Specific(accountKey = account.accountKey))
                                    .flatMap { service ->
                                        remember(account.accountKey) {
                                            service.userById(account.accountKey.id)
                                        }.collectAsState().toUi()
                                    } to account
                            }.toImmutableList()
                            .toImmutableListWrapper()
                    }
            }

        val selectedUsers =
            allUsers.map {
                it
                    .toImmutableList()
                    .filter {
                        selectedAccounts.contains(it.second)
                    }.toImmutableList()
                    .toImmutableListWrapper()
            }
        val remainingAccounts =
            allUsers.map {
                it
                    .toImmutableList()
                    .filter {
                        !selectedAccounts.contains(it.second)
                    }.toImmutableList()
                    .toImmutableListWrapper()
            }
        val enableCrossPost =
            allUsers.map {
                it.size > 1 // && status == null
            }

//        val serviceState = accountServiceProvider(accountType = accountType)
        val services =
            selectedAccounts.map {
                accountServiceProvider(accountType = AccountType.Specific(accountKey = it.accountKey))
            }
        val composeUseCase: ComposeUseCase = koinInject()
        val visibilityState: UiState<VisibilityState> =
            selectedAccounts
                .takeIf {
                    it.size == 1
                }?.first()
                ?.let {
                    when (it) {
                        is UiAccount.Mastodon -> UiState.Success(mastodonVisibilityPresenter())
                        is UiAccount.Misskey -> UiState.Success(misskeyVisibilityPresenter())
                        is UiAccount.XQT -> UiState.Error(IllegalStateException("XQT not supported"))
                        is UiAccount.Bluesky -> UiState.Error(IllegalStateException("Bluesky not supported"))
                        UiAccount.Guest -> UiState.Error(IllegalStateException("Guest not supported"))
                        // TODO: handle VVo
                        is UiAccount.VVo -> UiState.Error(IllegalStateException("VVo not supported"))
                    }
                } ?: UiState.Error(IllegalStateException("Visibility not supported"))

        val composeConfig: UiState<ComposeConfig> =
            remember(services) {
                services.merge().map {
                    it
                        .map { it.composeConfig(statusKey = status?.statusKey) }
                        .reduceOrNull { acc, config -> acc.merge(config) } ?: ComposeConfig()
                }
            }

        val emojiState =
            composeConfig
                .mapNotNull {
                    it.emoji
                }.flatMap {
                    it.emoji.collectAsState().toUi()
                }.map {
                    it.toImmutableListWrapper()
                }

        return object : ComposeState(
            account = accountState,
            visibilityState = visibilityState,
            replyState = replyState,
            emojiState = emojiState,
            composeConfig = composeConfig,
            enableCrossPost = enableCrossPost,
            selectedAccounts = selectedAccounts.toImmutableList(),
            selectedUsers = selectedUsers,
            otherAccounts = remainingAccounts,
        ) {
            override fun send(data: ComposeData) {
                composeUseCase.invoke(data) {
                    // TODO: show notification
                }
            }

            override fun selectAccount(account: UiAccount) {
                if (selectedAccounts.contains(account)) {
                    if (selectedAccounts.size == 1) {
                        return
                    }
                    selectedAccounts.remove(account)
                } else {
                    selectedAccounts.add(account)
                }
            }
        }
    }

//    @Composable
//    private fun emojiPresenter(accountType: AccountType): UiState<ImmutableListWrapper<UiEmoji>> {
//        return accountServiceProvider(accountType = accountType)
//            .flatMap {
//                when (it) {
//                    is MastodonDataSource -> it.emoji()
//                    is MisskeyDataSource -> it.emoji()
//                    else -> null
//                }?.collectAsState()?.toUi()?.map {
//                    it.toImmutableListWrapper()
//                } ?: UiState.Error(IllegalStateException("Emoji not supported"))
//            }
//    }

    @Composable
    private fun misskeyVisibilityPresenter(): MisskeyVisibilityState {
        var localOnly by remember {
            mutableStateOf(false)
        }
        var showVisibilityMenu by remember {
            mutableStateOf(false)
        }
        var visibility by remember {
            mutableStateOf(UiStatus.Misskey.Visibility.Public)
        }
        return object : MisskeyVisibilityState(
            visibility = visibility,
            showVisibilityMenu = showVisibilityMenu,
            allVisibilities =
                UiStatus.Misskey.Visibility.entries
                    .toImmutableList(),
            localOnly = localOnly,
        ) {
            override fun setLocalOnly(value: Boolean) {
                localOnly = value
            }

            override fun setVisibility(value: UiStatus.Misskey.Visibility) {
                visibility = value
            }

            override fun showVisibilityMenu() {
                showVisibilityMenu = true
            }

            override fun hideVisibilityMenu() {
                showVisibilityMenu = false
            }
        }
    }

    @Composable
    private fun mastodonVisibilityPresenter(): MastodonVisibilityState {
        var showVisibilityMenu by remember {
            mutableStateOf(false)
        }
        var visibility by remember {
            mutableStateOf(UiStatus.Mastodon.Visibility.Public)
        }
        return object : MastodonVisibilityState(
            visibility = visibility,
            showVisibilityMenu = showVisibilityMenu,
            allVisibilities =
                UiStatus.Mastodon.Visibility.entries
                    .toImmutableList(),
        ) {
            override fun setVisibility(value: UiStatus.Mastodon.Visibility) {
                visibility = value
            }

            override fun showVisibilityMenu() {
                showVisibilityMenu = true
            }

            override fun hideVisibilityMenu() {
                showVisibilityMenu = false
            }
        }
    }
}

sealed interface VisibilityState

@Immutable
abstract class MastodonVisibilityState(
    val visibility: UiStatus.Mastodon.Visibility,
    val showVisibilityMenu: Boolean,
    val allVisibilities: ImmutableList<UiStatus.Mastodon.Visibility>,
) : VisibilityState {
    abstract fun setVisibility(value: UiStatus.Mastodon.Visibility)

    abstract fun showVisibilityMenu()

    abstract fun hideVisibilityMenu()
}

@Immutable
abstract class MisskeyVisibilityState(
    val visibility: UiStatus.Misskey.Visibility,
    val showVisibilityMenu: Boolean,
    val allVisibilities: ImmutableList<UiStatus.Misskey.Visibility>,
    val localOnly: Boolean,
) : VisibilityState {
    abstract fun setLocalOnly(value: Boolean)

    abstract fun setVisibility(value: UiStatus.Misskey.Visibility)

    abstract fun showVisibilityMenu()

    abstract fun hideVisibilityMenu()
}

sealed interface ComposeStatus {
    val statusKey: MicroBlogKey

    data class Quote(
        override val statusKey: MicroBlogKey,
    ) : ComposeStatus

    data class Reply(
        override val statusKey: MicroBlogKey,
    ) : ComposeStatus
}

@Immutable
abstract class ComposeState(
    val account: UiState<UiAccount>,
    val visibilityState: UiState<VisibilityState>,
    val replyState: UiState<UiStatus>?,
    val emojiState: UiState<ImmutableListWrapper<UiEmoji>>,
    val composeConfig: UiState<ComposeConfig>,
    val enableCrossPost: UiState<Boolean>,
    val selectedAccounts: ImmutableList<UiAccount>,
    val otherAccounts: UiState<ImmutableListWrapper<Pair<UiState<UiUser>, UiAccount>>>,
    val selectedUsers: UiState<ImmutableListWrapper<Pair<UiState<UiUser>, UiAccount>>>,
) {
    abstract fun send(data: ComposeData)

    abstract fun selectAccount(account: UiAccount)
}
