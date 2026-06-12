package dev.dimension.flare.ui.screen.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.model.tab.toUiTimelineTabItem
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiText
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
            override fun getTimelineTabItem(item: UiList): UiTimelineTabItem =
                CommonTimelineSpecs.list
                    .candidate(
                        data = TimelineSpec.AccountResourceData(specificAccountKey(), item.id),
                        title = UiText.Raw(item.title),
                        icon =
                            (item as? UiList.List)?.avatar?.let {
                                IconType.Url(it)
                            } ?: IconType.Material(UiIcon.List),
                    ).toUiTimelineTabItem()
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

    private fun specificAccountKey() = (accountType as AccountType.Specific).accountKey
}
