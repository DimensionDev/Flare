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
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
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
        val statusState =
            status?.let { status ->
                remember(status.statusKey) {
                    StatusPresenter(accountType = accountType, statusKey = status.statusKey)
                }.body().status
            }
        val replyState =
            statusState?.map {
                if (it.platformType == PlatformType.VVo) {
                    it.copy(
                        content = (it.content as? UiTimeline.ItemContent.Status)?.quote?.firstOrNull() ?: it.content,
                    )
                } else {
                    it
                }
            }
        val initialTextState =
            statusState?.mapNotNull {
                val content = it.content
                if (content is UiTimeline.ItemContent.Status) {
                    when (it.platformType) {
                        PlatformType.VVo -> {
                            if (content.quote.any() && status is ComposeStatus.Quote) {
                                InitialText(
                                    text = "//@${content.user?.name?.raw}:${content.content.raw}",
                                    cursorPosition = 0,
                                )
                            } else {
                                null
                            }
                        }
                        PlatformType.Mastodon -> {
                            val text = "${content.user?.handle} "
                            InitialText(
                                text = text,
                                cursorPosition = text.length,
                            )
                        }
                        else -> null
                    }
                } else {
                    null
                }
            }
        val allUsers =
            accounts.flatMap { data ->
                accountState
                    .flatMap { current ->
                        statusState?.map {
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
                                        }.collectAsState()
                                            .toUi()
                                            .map {
                                                it as UiUserV2
                                            }
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

        val services =
            selectedAccounts.map {
                accountServiceProvider(accountType = AccountType.Specific(accountKey = it.accountKey))
            }
        val composeUseCase: ComposeUseCase = koinInject()
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

        val visibilityState =
            composeConfig
                .mapNotNull {
                    it.visibility
                }.map {
                    visibilityPresenter()
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
            initialTextState = initialTextState,
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

    @Composable
    private fun visibilityPresenter(): VisibilityState {
        var showVisibilityMenu by remember {
            mutableStateOf(false)
        }
        var visibility by remember {
            mutableStateOf(UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public)
        }
        return object : VisibilityState {
            override val visibility: UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type
                get() = visibility

            override val allVisibilities: ImmutableList<UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type>
                get() =
                    UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.entries
                        .toImmutableList()

            override val showVisibilityMenu: Boolean
                get() = showVisibilityMenu

            override fun showVisibilityMenu() {
                showVisibilityMenu = true
            }

            override fun hideVisibilityMenu() {
                showVisibilityMenu = false
            }

            override fun setVisibility(value: UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type) {
                visibility = value
            }
        }
    }
}

interface VisibilityState {
    val visibility: UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type
    val allVisibilities: ImmutableList<UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type>
    val showVisibilityMenu: Boolean

    fun showVisibilityMenu()

    fun hideVisibilityMenu()

    fun setVisibility(value: UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type)
}

sealed interface ComposeStatus {
    val statusKey: MicroBlogKey

    data class Quote(
        override val statusKey: MicroBlogKey,
    ) : ComposeStatus

    open class Reply(
        override val statusKey: MicroBlogKey,
    ) : ComposeStatus

    data class VVOComment(
        override val statusKey: MicroBlogKey,
        val rootId: String,
    ) : Reply(statusKey)
}

@Immutable
abstract class ComposeState(
    val account: UiState<UiAccount>,
    val visibilityState: UiState<VisibilityState>,
    val replyState: UiState<UiTimeline>?,
    val initialTextState: UiState<InitialText>?,
    val emojiState: UiState<ImmutableListWrapper<UiEmoji>>,
    val composeConfig: UiState<ComposeConfig>,
    val enableCrossPost: UiState<Boolean>,
    val selectedAccounts: ImmutableList<UiAccount>,
    val otherAccounts: UiState<ImmutableListWrapper<Pair<UiState<UiUserV2>, UiAccount>>>,
    val selectedUsers: UiState<ImmutableListWrapper<Pair<UiState<UiUserV2>, UiAccount>>>,
) {
    abstract fun send(data: ComposeData)

    abstract fun selectAccount(account: UiAccount)
}

@Immutable
data class InitialText internal constructor(
    val text: String,
    val cursorPosition: Int,
)
