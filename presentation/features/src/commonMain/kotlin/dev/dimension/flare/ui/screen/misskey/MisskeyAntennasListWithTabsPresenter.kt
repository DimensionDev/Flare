package dev.dimension.flare.ui.screen.misskey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.dimension.flare.data.model.tab.TimelinePersistenceMapper
import dev.dimension.flare.data.model.tab.TimelineSlot
import dev.dimension.flare.data.platform.toTimelineTabDescriptor
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.PinTabsPresenter
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.list.AntennasListPresenter
import org.koin.core.component.inject

public class MisskeyAntennasListWithTabsPresenter(
    private val accountType: AccountType,
) : PresenterBase<MisskeyAntennasListWithTabsPresenter.State>() {
    private val pinTabsPresenter by lazy {
        object : PinTabsPresenter<UiList>() {
            private val timelinePersistenceMapper by inject<TimelinePersistenceMapper>()

            override fun getTimelineTabItem(item: UiList): TimelineSlot =
                timelinePersistenceMapper.toSlot((item as UiList.Antenna).toTimelineTabDescriptor(specificAccountKey()))
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
