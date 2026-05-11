package dev.dimension.flare.ui.screen.misskey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.toSlot
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.presenter.PinTabsPresenter
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.list.AntennasListPresenter

public class MisskeyAntennasListWithTabsPresenter(
    private val accountType: AccountType,
) : PresenterBase<MisskeyAntennasListWithTabsPresenter.State>() {
    private val pinTabsPresenter by lazy {
        object : PinTabsPresenter<UiList>() {
            override fun getTimelineTabItem(item: UiList): TimelineSlot =
                MisskeyPlatformSpec.antennaTimelineSpec
                    .target(
                        data = TimelineSpec.AccountResourceData(specificAccountKey(), item.id),
                        title = UiText.Raw(item.title),
                        icon = IconType.Material(UiIcon.List),
                    ).toSlot()
        }
    }

    @Composable
    override fun body(): State {
        val state =
            remember(accountType) {
                AntennasListPresenter(accountType)
            }.body()

        val pinTabsState = pinTabsPresenter.body()
        return object :
            State,
            AntennasListPresenter.State by state,
            PinTabsPresenter.State<UiList> by pinTabsState {}
    }

    public interface State :
        PinTabsPresenter.State<UiList>,
        AntennasListPresenter.State

    private fun specificAccountKey() = (accountType as AccountType.Specific).accountKey
}
