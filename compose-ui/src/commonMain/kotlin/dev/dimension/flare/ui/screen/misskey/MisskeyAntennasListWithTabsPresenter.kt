package dev.dimension.flare.ui.screen.misskey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.PinTabsPresenter
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.list.AntennasListPresenter

public class MisskeyAntennasListWithTabsPresenter(
    private val accountType: AccountType,
) : PresenterBase<MisskeyAntennasListWithTabsPresenter.State>() {
    private val pinTabsPresenter by lazy {
        object : PinTabsPresenter<UiList>() {
            override fun List<TimelineTabItem>.filterPinned(): List<String> =
                filterIsInstance<Misskey.AntennasTimelineTabItem>()
                    .map { it.id }

            override fun getTimelineTabItem(item: UiList): TimelineTabItem =
                Misskey.AntennasTimelineTabItem(
                    account = accountType,
                    id = item.id,
                    metaData =
                        TabMetaData(
                            title = TitleType.Text(item.title),
                            icon = IconType.Material(IconType.Material.MaterialIcon.List),
                        ),
                )

            override fun List<TimelineTabItem>.filter(item: UiList): List<TimelineTabItem> =
                filter {
                    if (it is Misskey.AntennasTimelineTabItem) {
                        it.id != item.id
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
                AntennasListPresenter(accountType)
            }.invoke()

        val pinTabsState = pinTabsPresenter.invoke()
        return object :
            State,
            AntennasListPresenter.State by state,
            PinTabsPresenter.State<UiList> by pinTabsState {}
    }

    public interface State :
        PinTabsPresenter.State<UiList>,
        AntennasListPresenter.State
}
