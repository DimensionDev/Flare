package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.collections.immutable.persistentListOf

@WebPresenter("profile")
public class WebProfilePresenter(
    private val accountKey: String?,
    private val userKey: String?,
    private val userName: String?,
    private val host: String?,
) : PresenterBase<ProfileState>() {
    @Composable
    override fun body(): ProfileState {
        if (accountKey == null && userKey == null && userName == null && host == null) {
            return emptyProfileState()
        }

        val accountType = remember(accountKey) { accountKey.toWebAccountType() }
        val profileUserKey = remember(userKey) { userKey?.let(MicroBlogKey::valueOf) }

        if (profileUserKey != null || userName == null || host == null) {
            return remember(accountType, profileUserKey) {
                ProfilePresenter(
                    accountType = accountType,
                    userKey = profileUserKey,
                )
            }.body()
        }

        val resolvedUserState =
            remember(accountType, userName, host) {
                ProfileWithUserNameAndHostPresenter(
                    userName = userName,
                    host = host,
                    accountType = accountType,
                )
            }.body().user
        val resolvedUserKey = resolvedUserState.takeSuccess()?.key

        if (resolvedUserKey != null) {
            return remember(accountType, resolvedUserKey) {
                ProfilePresenter(
                    accountType = accountType,
                    userKey = resolvedUserKey,
                )
            }.body()
        }

        return emptyProfileState(userState = resolvedUserState)
    }
}

private fun emptyProfileState(
    userState: UiState<UiProfile> = UiState.Loading(),
): ProfileState =
    object : ProfileState(
        userState = userState,
        relationState = UiState.Loading(),
        followButtonState = UiState.Loading(),
        isMe = UiState.Loading(),
        actions = persistentListOf<ActionMenu>(),
        myAccountKey = UiState.Loading(),
        tabs = UiState.Loading(),
    ) {
        override fun follow(userKey: MicroBlogKey) = Unit

        override fun unfollow(userKey: MicroBlogKey) = Unit

        override fun unblock(userKey: MicroBlogKey) = Unit

        override fun report(userKey: MicroBlogKey) = Unit
    }

private fun String?.toWebAccountType(): AccountType =
    when {
        this == null || this == "guest" -> AccountType.Guest
        startsWith("guest@") -> AccountType.GuestHost(removePrefix("guest@"))
        else -> AccountType.Specific(MicroBlogKey.valueOf(this))
    }
