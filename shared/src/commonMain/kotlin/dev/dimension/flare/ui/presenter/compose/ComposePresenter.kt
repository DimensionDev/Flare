package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.LazyPagingItemsProxy
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.datasource.ComposeData
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.compose.koinInject

class ComposePresenter(
    private val status: ComposeStatus? = null,
) : PresenterBase<ComposeState>() {
    @Composable
    override fun body(): ComposeState {
        val account by activeAccountPresenter()
        val composeUseCase: ComposeUseCase = koinInject()
        val visibilityState =
            account.flatMap {
                when (it) {
                    is UiAccount.Mastodon -> UiState.Success(mastodonVisibilityPresenter())
                    is UiAccount.Misskey -> UiState.Success(misskeyVisibilityPresenter())
                    is UiAccount.Bluesky -> UiState.Error(IllegalStateException("Bluesky not supported"))
                }
            }

        val replyState =
            status?.let { status ->
                account.map {
                    statusPresenter(it, status)
                }
            }
        val emojiState =
            account.flatMap {
                emojiPresenter(it)
                    ?: UiState.Error(IllegalStateException("Emoji not supported"))
            }

        return object : ComposeState(
            account = account,
            visibilityState = visibilityState,
            replyState = replyState,
            emojiState = emojiState,
            canPoll =
                account.map {
                    it is UiAccount.Misskey || it is UiAccount.Mastodon
                },
            canCW =
                account.map {
                    it is UiAccount.Misskey || it is UiAccount.Mastodon
                },
        ) {
            override fun send(data: ComposeData) {
                composeUseCase.invoke(data) {
                    // TODO: show notification
                }
            }
        }
    }

    @Composable
    private fun statusPresenter(
        account: UiAccount,
        status: ComposeStatus,
    ): LazyPagingItemsProxy<UiStatus> {
        val service = accountServiceProvider(account = account)
        return remember(account.accountKey) {
            service.status(status.statusKey)
        }.collectPagingProxy()
    }

    @Composable
    private fun emojiPresenter(account: UiAccount): UiState<ImmutableList<UiEmoji>>? {
        val service = accountServiceProvider(account = account)
        return remember(account.accountKey) {
            when (service) {
                is MastodonDataSource -> service.emoji()
                is MisskeyDataSource -> service.emoji()
                else -> null
            }
        }?.collectAsState()?.toUi()
    }

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
            allVisibilities = UiStatus.Misskey.Visibility.entries.toImmutableList(),
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
            allVisibilities = UiStatus.Mastodon.Visibility.entries.toImmutableList(),
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
    val replyState: UiState<LazyPagingItemsProxy<UiStatus>>?,
    val emojiState: UiState<ImmutableList<UiEmoji>>,
    val canPoll: UiState<Boolean>,
    val canCW: UiState<Boolean>,
) {
    abstract fun send(data: ComposeData)
}
