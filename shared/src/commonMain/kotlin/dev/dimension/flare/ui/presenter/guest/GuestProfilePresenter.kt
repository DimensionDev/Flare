package dev.dimension.flare.ui.presenter.guest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.paging.Pager
import androidx.paging.PagingConfig
import dev.dimension.flare.common.collectPagingProxy
import dev.dimension.flare.data.datasource.guest.GuestUserTimelinePagingSource
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.profile.ProfileState

class GuestProfilePresenter(
    private val userKey: MicroBlogKey,
) : PresenterBase<ProfileState>() {
    @Composable
    override fun body(): ProfileState {
        val listState =
            remember(userKey) {
                Pager(PagingConfig(pageSize = 20)) {
                    GuestUserTimelinePagingSource(userKey.id)
                }.flow
            }.collectPagingProxy()

        val useState by produceState<UiState<UiUser>>(UiState.Loading()) {
            value =
                try {
                    val user =
                        GuestMastodonService.lookupUser(userKey.id)
                            .toUi(GuestMastodonService.instance)
                    UiState.Success(user)
                } catch (e: Exception) {
                    UiState.Error(e.cause ?: Throwable(e.message ?: "Unknown error"))
                }
        }

        return object : ProfileState(
            userState = useState,
            listState = UiState.Success(listState),
            mediaState = UiState.Error(Throwable("Not implemented")),
            relationState = UiState.Error(Throwable("Not implemented")),
            isMe = UiState.Success(false),
        ) {
            override suspend fun refresh() {
                listState.refreshSuspend()
            }

            override fun follow(
                user: UiUser,
                data: UiRelation,
            ) {
            }

            override fun block(
                user: UiUser,
                data: UiRelation,
            ) {
            }

            override fun mute(
                user: UiUser,
                data: UiRelation,
            ) {
            }

            override fun report(user: UiUser) {
            }
        }
    }
}
