package dev.dimension.flare.ui.screen.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.PinTabsPresenter
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.AllListPresenter
import dev.dimension.flare.ui.presenter.list.AllListState

public class AllListWithTabsPresenter(
    private val accountType: AccountType,
) : PresenterBase<AllListWithTabsPresenter.State>() {
    private val pinTabsPresenter by lazy {
        object : PinTabsPresenter<UiList>() {
            override fun List<TimelineTabItem>.filterPinned(): List<String> =
                filterIsInstance<ListTimelineTabItem>()
                    .map { it.listId }

            override fun getTimelineTabItem(item: UiList): TimelineTabItem =
                ListTimelineTabItem(
                    account = accountType,
                    listId = item.id,
                    metaData =
                        TabMetaData(
                            title = TitleType.Text(item.title),
                            icon = IconType.Material(IconType.Material.MaterialIcon.List),
                        ),
                )

            override fun List<TimelineTabItem>.filter(item: UiList): List<TimelineTabItem> =
                filter {
                    if (it is ListTimelineTabItem) {
                        it.listId != item.id
                    } else {
                        true
                    }
                }
        }
    }

    @Composable
    override fun body(): State {
        val state =
            remember(accountType) {
                AllListPresenter(accountType)
            }.invoke()

        val pinState = pinTabsPresenter.invoke()

        return object :
            State,
            AllListState by state,
            PinTabsPresenter.State<UiList> by pinState {
        }
    }

    public interface State :
        AllListState,
        PinTabsPresenter.State<UiList>
}
