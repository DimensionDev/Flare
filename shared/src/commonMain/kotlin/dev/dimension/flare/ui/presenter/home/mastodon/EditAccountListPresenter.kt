package dev.dimension.flare.ui.presenter.home.mastodon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.common.collectAsState
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.repository.accountServiceProvider
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flatMap
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch

class EditAccountListPresenter(
    private val accountType: AccountType,
    private val userKey: MicroBlogKey,
) : PresenterBase<EditAccountListState>() {
    @Composable
    override fun body(): EditAccountListState {
        val scope = rememberCoroutineScope()
        val serviceState = accountServiceProvider(accountType = accountType)
        val allList =
            serviceState.flatMap { service ->
                require(service is MastodonDataSource)
                remember(service) {
                    service.allLists()
                }.collectAsState().toUi()
            }
        val userLists =
            serviceState.flatMap { service ->
                require(service is MastodonDataSource)
                remember(service) {
                    service.userLists(userKey)
                }.collectAsState().toUi()
            }

        val result =
            allList.flatMap { alist ->
                userLists.map { userLists ->
                    alist
                        .associateWith { list ->
                            userLists.any { it.id == list.id }
                        }.toImmutableMap()
                }
            }

        return object : EditAccountListState {
            override val lists = result

            override fun addList(list: UiList) {
                serviceState.onSuccess {
                    require(it is MastodonDataSource)
                    scope.launch {
                        it.addMember(listId = list.id, userKey = userKey)
                    }
                }
            }

            override fun removeList(list: UiList) {
                serviceState.onSuccess {
                    require(it is MastodonDataSource)
                    scope.launch {
                        it.removeMember(listId = list.id, userKey = userKey)
                    }
                }
            }
        }
    }
}

@Immutable
interface EditAccountListState {
    val lists: UiState<ImmutableMap<UiList, Boolean>>

    fun addList(list: UiList)

    fun removeList(list: UiList)
}
