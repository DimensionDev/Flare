package dev.dimension.flare.ui.presenter

import dev.dimension.flare.common.PagingState
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.TimelineReadingState
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineItemPresenterTest {
    @Test
    fun automaticRefreshUsesReadingPolicyAndWaitsForRestorationOrAnActiveRefresh() =
        runTest {
            val refreshes = mutableListOf<String>()
            val state =
                object : TimelineItemPresenter.State {
                    override val listState = PagingState.Loading<UiTimelineV2>()
                    override var isRefreshing = false
                    override var readingState: TimelineReadingState? = null

                    override fun refreshSync() = Unit

                    override suspend fun refreshSuspend() {
                        refreshes += "ordinary"
                    }
                }
            val reading =
                object : TimelineReadingState {
                    override var position: TimelineReadingPosition? = TimelineReadingPosition("post", -12.5, 1)
                    override val isRefreshing = false
                    override val hasNewContent = false

                    override fun updateViewport(
                        itemKey: String?,
                        offset: Double,
                        atTop: Boolean,
                        interacting: Boolean,
                    ) = Unit

                    override fun positionRestored(requestId: Long) = Unit

                    override fun savePosition() = Unit

                    override fun cancelRestoration() = Unit

                    override suspend fun showLatest() = Unit

                    override suspend fun refreshAutomatically() {
                        refreshes += "reading"
                    }
                }

            state.isRefreshing = true
            state.refreshAutomatically()
            assertEquals(emptyList(), refreshes)
            state.isRefreshing = false
            state.refreshAutomatically()
            assertEquals(listOf("ordinary"), refreshes)

            state.readingState = reading
            state.refreshAutomatically()
            assertEquals(listOf("ordinary"), refreshes, "Restoring a position must not start a timer refresh")
            reading.position = null
            state.refreshAutomatically()
            assertEquals(listOf("ordinary", "reading"), refreshes)

            state.isRefreshing = true
            state.refreshAutomatically()
            assertEquals(listOf("ordinary", "reading"), refreshes)
        }
}
