package dev.dimension.flare.ui.presenter.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.map
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabSection
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.data.model.tab.toUiTimelineTabItem
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.accountServiceFlow
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Presenter for pinnable lists.
 * Different from [AllListPresenter] in that it also includes Bluesky feeds.
 * This presenter should be used for managing pinnable tabs.
 */
public class PinnableTimelineTabPresenter(
    private val accountType: AccountType,
) : PresenterBase<PinnableTimelineTabPresenter.State>(),
    KoinComponent {
    @Immutable
    public interface State {
        public val tabs: UiState<ImmutableList<PinnableTimelineTab>>
    }

    @Immutable
    public data class PinnableTimelineTab(
        val title: UiStrings,
        val data: PagingState<UiTimelineTabItem>,
    )

    private val accountRepository: AccountRepository by inject()

    private data class Sections(
        val builtInTabs: ImmutableList<TimelineCandidate<*>>,
        val pinnableTabs: List<PinnableTimelineTabSection>,
    )

    private val sectionsFlow by lazy {
        accountServiceFlow(
            accountType = accountType,
            repository = accountRepository,
        ).map { service ->
            Sections(
                builtInTabs =
                    (service as? TimelineTabConfigurationDataSource)
                        ?.builtInTimelineTabs
                        ?: persistentListOf(),
                pinnableTabs =
                    (service as? PinnableTimelineTabDataSource)
                        ?.pinnableTimelineTabs
                        .orEmpty(),
            )
        }
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val sections by sectionsFlow.collectAsUiState()

        val tabs =
            sections.map { sections ->
                val defaultSection =
                    if (sections.builtInTabs.isEmpty()) {
                        null
                    } else {
                        PinnableTimelineTab(
                            title = UiStrings.Default,
                            data =
                                PagingState.Success.ImmutableSuccess(
                                    sections.builtInTabs
                                        .map<TimelineCandidate<*>, UiTimelineTabItem> { it.toUiTimelineTabItem() }
                                        .toImmutableList(),
                                ),
                        )
                    }
                (
                    listOfNotNull(defaultSection) +
                        sections.pinnableTabs.map { section ->
                            val pagingState =
                                remember(section) {
                                    section.data
                                        .map { pagingData ->
                                            pagingData.map { it.toUiTimelineTabItem() as UiTimelineTabItem }
                                        }.cachedIn(scope)
                                }.collectAsLazyPagingItems().toPagingState()
                            PinnableTimelineTab(
                                title = section.title,
                                data = pagingState,
                            )
                        }
                ).toImmutableList()
            }

        return object : State {
            override val tabs = tabs
        }
    }
}
