package dev.dimension.flare.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import dev.dimension.flare.common.combineLatestFlowLists
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.model.ProfileTabItem
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.allAccountServicesFlow
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class SecondaryTabsPresenter :
    PresenterBase<SecondaryTabsPresenter.State>(),
    KoinComponent {
    @Immutable
    public interface State {
        public val items: UiState<ImmutableList<Item>>
    }

    @Immutable
    public class Item(
        public val user: UiState<UiProfile>,
        public val tabs: ImmutableList<TabItem>,
    )

    private val accountRepository: AccountRepository by inject()

    private val itemsFlow by lazy {
        allAccountServicesFlow(accountRepository)
            .map { services ->
                services
                    .mapNotNull { service ->
                        if (service is UserDataSource && service is AuthenticatedMicroblogDataSource) {
                            service.userHandler
                                .userById(service.accountKey.id)
                                .toUi()
                                .distinctUntilChangedBy {
                                    it.takeSuccess()?.let {
                                        buildString {
                                            append(it.key)
                                            append("-")
                                            append(it.name.raw)
                                            append("-")
                                            append(it.avatar)
                                            append("-")
                                            append(it.handle.raw)
                                        }
                                    }
                                }.map { userState ->
                                    userState.takeSuccess()?.let { user ->
                                        Item(
                                            user = userState,
                                            tabs =
                                                listOf(
                                                    ProfileTabItem(
                                                        accountKey = service.accountKey,
                                                        userKey = service.accountKey,
                                                    ),
                                                ).plus(
                                                    TimelineTabItem.secondaryFor(
                                                        user.platformType,
                                                        service.accountKey,
                                                    ),
                                                ).toImmutableList(),
                                        )
                                    }
                                }
                        } else {
                            null
                        }
                    }
            }.combineLatestFlowLists()
            .map {
                it.filterNotNull().toImmutableList()
            }.distinctUntilChanged()
    }

    @Composable
    override fun body(): State {
        val items by itemsFlow.collectAsUiState()

        return object : State {
            override val items = items
        }
    }
}
