package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.SupportedComposeEvent
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.repository.accountProvider
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiEmoji
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
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
        val serviceState = accountServiceProvider(accountType = accountType)
        val composeUseCase: ComposeUseCase = koinInject()
        val visibilityState =
            accountState.flatMap {
                when (it) {
                    is UiAccount.Mastodon -> UiState.Success(mastodonVisibilityPresenter())
                    is UiAccount.Misskey -> UiState.Success(misskeyVisibilityPresenter())
                    is UiAccount.XQT -> UiState.Error(IllegalStateException("XQT not supported"))
                    is UiAccount.Bluesky -> UiState.Error(IllegalStateException("Bluesky not supported"))
                    UiAccount.Guest -> UiState.Error(IllegalStateException("Guest not supported"))
                }
            }

        val replyState =
            status?.let { status ->
                remember(status.statusKey) {
                    StatusPresenter(accountType = accountType, statusKey = status.statusKey)
                }.body().status
            }
        val emojiState = emojiPresenter(accountType)

        return object : ComposeState(
            account = accountState,
            visibilityState = visibilityState,
            replyState = replyState,
            emojiState = emojiState,
            supportedComposeEvent =
                serviceState.map {
                    it.supportedComposeEvent(statusKey = status?.statusKey).toImmutableList().toImmutableListWrapper()
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
    private fun emojiPresenter(accountType: AccountType): UiState<ImmutableListWrapper<UiEmoji>> {
        return accountServiceProvider(accountType = accountType)
            .flatMap {
                when (it) {
                    is MastodonDataSource -> it.emoji()
                    is MisskeyDataSource -> it.emoji()
                    else -> null
                }?.collectAsState()?.toUi()?.map {
                    it.toImmutableListWrapper()
                } ?: UiState.Error(IllegalStateException("Emoji not supported"))
            }
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
    val replyState: UiState<UiStatus>?,
    val emojiState: UiState<ImmutableListWrapper<UiEmoji>>,
    val supportedComposeEvent: UiState<ImmutableListWrapper<SupportedComposeEvent>>,
) {
    abstract fun send(data: ComposeData)
}
