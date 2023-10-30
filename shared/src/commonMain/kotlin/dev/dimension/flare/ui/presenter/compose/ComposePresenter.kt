package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.cash.paging.compose.LazyPagingItems
import app.cash.paging.compose.collectAsLazyPagingItems
import dev.dimension.flare.common.collectAsState
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

class ComposePresenter(
    private val status: ComposeStatus? = null,
) : PresenterBase<ComposeState>() {
    @Composable
    override fun body(): ComposeState {
        val account by activeAccountPresenter()
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
                emojiPresenter(it).emojiState
                    ?: UiState.Error(IllegalStateException("Emoji not supported"))
            }

        return object : ComposeState(
            account = account,
            visibilityState = visibilityState,
            replyState = replyState,
            emojiState = emojiState,
        ) {
        }

    }

    @Composable
    private fun statusPresenter(
        account: UiAccount,
        status: ComposeStatus,
    ) = run {
        val service = accountServiceProvider(account = account)
        remember(account.accountKey) {
            service.status(status.statusKey)
        }.collectAsLazyPagingItems()
    }

    @Composable
    private fun emojiPresenter(account: UiAccount) =
        run {
            val service = accountServiceProvider(account = account)
            val emojiState =
                remember(account.accountKey) {
                    when (service) {
                        is MastodonDataSource -> service.emoji()
                        is MisskeyDataSource -> service.emoji()
                        else -> null
                    }
                }?.collectAsState()?.toUi()
            object {
                val emojiState = emojiState
            }
        }

    @Composable
    private fun misskeyVisibilityPresenter() =
        run {
            var localOnly by remember {
                mutableStateOf(false)
            }
            var showVisibilityMenu by remember {
                mutableStateOf(false)
            }
            var visibility by remember {
                mutableStateOf(UiStatus.Misskey.Visibility.Public)
            }
            object : MisskeyVisibilityState(
                visibility = visibility,
                showVisibilityMenu = showVisibilityMenu,
                allVisibilities = UiStatus.Misskey.Visibility.entries.toList(),
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
    private fun mastodonVisibilityPresenter() =
        run {
            var showVisibilityMenu by remember {
                mutableStateOf(false)
            }
            var visibility by remember {
                mutableStateOf(UiStatus.Mastodon.Visibility.Public)
            }
            object : MastodonVisibilityState(
                visibility = visibility,
                showVisibilityMenu = showVisibilityMenu,
                allVisibilities = UiStatus.Mastodon.Visibility.entries.toList(),
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


sealed interface VisibilityState<T: Any> {
    val visibility: T
    val showVisibilityMenu: Boolean
    val allVisibilities: List<T>

    fun setVisibility(value: T)

    fun showVisibilityMenu()

    fun hideVisibilityMenu()
}

abstract class MastodonVisibilityState(
    override val visibility: UiStatus.Mastodon.Visibility,
    override val showVisibilityMenu: Boolean,
    override val allVisibilities: List<UiStatus.Mastodon.Visibility>,
) : VisibilityState<UiStatus.Mastodon.Visibility>

abstract class MisskeyVisibilityState(
    override val visibility: UiStatus.Misskey.Visibility,
    override val showVisibilityMenu: Boolean,
    override val allVisibilities: List<UiStatus.Misskey.Visibility>,
    val localOnly: Boolean,
) : VisibilityState<UiStatus.Misskey.Visibility> {
    abstract fun setLocalOnly(value: Boolean)
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
    val visibilityState: UiState<VisibilityState<out Enum<*>>>,
    val replyState: UiState<LazyPagingItems<UiStatus>>?,
    val emojiState: UiState<ImmutableList<UiEmoji>>,
)