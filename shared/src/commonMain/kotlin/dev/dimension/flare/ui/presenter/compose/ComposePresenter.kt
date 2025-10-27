package dev.dimension.flare.ui.presenter.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.ImmutableListWrapper
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.common.toImmutableListWrapper
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountProvider
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.data.repository.allAccountsPresenter
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.EmojiData
import dev.dimension.flare.ui.model.UiAccount
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
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.status.StatusPresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ComposePresenter(
    private val accountType: AccountType,
    private val status: ComposeStatus? = null,
) : PresenterBase<ComposeState>(),
    KoinComponent {
    private val composeUseCase: ComposeUseCase by inject()
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): ComposeState {
        val userState =
            remember(accountType) {
                UserPresenter(accountType = accountType, userKey = null)
            }.body().user
        val accountState by accountProvider(accountType = accountType, repository = accountRepository)
        val accounts by allAccountsPresenter(repository = accountRepository)
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
        val allUsers =
            accounts.flatMap { data ->
                accountState
                    .flatMap { current ->
                        statusState
                            ?.mapNotNull {
                                it.content as? UiTimeline.ItemContent.Status
                            }?.map {
                                current to listOf(it.platformType)
                            } ?: UiState.Success(current to PlatformType.entries.toList())
                    }.map { (current, platforms) ->
                        data
                            .sortedBy {
                                it.accountKey != current.accountKey
                            }.filter {
                                it.platformType in platforms
                            }.map { account ->
                                accountServiceProvider(
                                    accountType = AccountType.Specific(accountKey = account.accountKey),
                                    repository = accountRepository,
                                ).flatMap { service ->
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
        val replyState =
            statusState?.map {
                if (it.content is UiTimeline.ItemContent.Status && it.content.platformType == PlatformType.VVo) {
                    it.copy(
                        content = it.content.quote.firstOrNull() ?: it.content,
                    )
                } else {
                    it
                }
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
        val initialTextState =
            if (statusState != null) {
                userState.flatMap { user ->
                    remember(statusState) {
                        statusState.mapNotNull { timeline ->
                            val content = timeline.content
                            if (content is UiTimeline.ItemContent.Status) {
                                when (content.platformType) {
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
                                    PlatformType.Mastodon, PlatformType.Misskey -> {
                                        if (status is ComposeStatus.Reply) {
                                            val handleToAdd = mutableSetOf<String>()
                                            if (content.user?.key != selectedAccounts.firstOrNull()?.accountKey) {
                                                content.user?.handle?.let {
                                                    handleToAdd.add(it)
                                                }
                                            }
                                            content.content.data
                                                .getElementsByAttributeValueStarting(
                                                    "href",
                                                    "flare://ProfileWithNameAndHost",
                                                ).filter {
                                                    val href = it.attr("href")
                                                    val params =
                                                        href
                                                            .substringAfter("flare://ProfileWithNameAndHost/")
                                                            .substringBefore("?accountKey=")
                                                            .split('/')
                                                    val userName = params.getOrNull(0)
                                                    val host = params.getOrNull(1)
                                                    user.handle != "@$userName@$host"
                                                }.filter {
                                                    it.text() != content.user?.handle
                                                }.forEach {
                                                    handleToAdd.add(it.text())
                                                }
                                            val text =
                                                buildString {
                                                    handleToAdd.distinct().forEach {
                                                        append("$it ")
                                                    }
                                                }
                                            InitialText(
                                                text = text,
                                                cursorPosition = text.length,
                                            )
                                        } else {
                                            null
                                        }
                                    }
                                    else -> null
                                }
                            } else {
                                null
                            }
                        }
                    }
                }
            } else {
                null
            }

        val services =
            selectedAccounts.map {
                accountServiceProvider(accountType = AccountType.Specific(accountKey = it.accountKey), repository = accountRepository)
            }
        val composeConfig: UiState<ComposeConfig> =
            remember(services) {
                services.merge().map {
                    it
                        .mapNotNull {
                            if (it is AuthenticatedMicroblogDataSource) {
                                it.composeConfig(statusKey = status?.statusKey)
                            } else {
                                null
                            }
                        }.reduceOrNull { acc, config -> acc.merge(config) } ?: ComposeConfig()
                }
            }

        val emojiState =
            composeConfig
                .mapNotNull {
                    it.emoji
                }.flatMap {
                    it.emoji.collectAsState().toUi()
                }.map {
                    remember(it) {
                        EmojiData(it)
                    }
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
                composeUseCase.invoke(data)
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

            override fun clear() {
                visibility = UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public
                showVisibilityMenu = false
            }
        }
    }
}

@Immutable
public interface VisibilityState {
    public val visibility: UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type
    public val allVisibilities: ImmutableList<UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type>
    public val showVisibilityMenu: Boolean

    public fun showVisibilityMenu()

    public fun hideVisibilityMenu()

    public fun setVisibility(value: UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type)

    public fun clear()
}

@Immutable
public sealed class ComposeStatus {
    public abstract val statusKey: MicroBlogKey

    public data class Quote(
        override val statusKey: MicroBlogKey,
    ) : ComposeStatus()

    public open class Reply(
        override val statusKey: MicroBlogKey,
    ) : ComposeStatus()

    public data class VVOComment(
        override val statusKey: MicroBlogKey,
        val rootId: String,
    ) : Reply(statusKey)
}

@Immutable
public abstract class ComposeState(
    public val account: UiState<UiAccount>,
    public val visibilityState: UiState<VisibilityState>,
    public val replyState: UiState<UiTimeline>?,
    public val initialTextState: UiState<InitialText>?,
    public val emojiState: UiState<EmojiData>,
    public val composeConfig: UiState<ComposeConfig>,
    public val enableCrossPost: UiState<Boolean>,
    public val selectedAccounts: ImmutableList<UiAccount>,
    public val otherAccounts: UiState<ImmutableListWrapper<Pair<UiState<UiUserV2>, UiAccount>>>,
    public val selectedUsers: UiState<ImmutableListWrapper<Pair<UiState<UiUserV2>, UiAccount>>>,
) {
    public abstract fun send(data: ComposeData)

    public abstract fun selectAccount(account: UiAccount)
}

@Immutable
public data class InitialText internal constructor(
    val text: String,
    val cursorPosition: Int,
)
