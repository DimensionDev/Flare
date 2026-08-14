package dev.dimension.flare.data.datasource.xqt

import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.TagPinnedTimeline
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.data.platform.XqtTagTimelineData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

internal fun XQTService.pinnableTagTimelines(accountKey: MicroBlogKey): Flow<PagingData<TimelineCandidate<*>>> =
    Pager(
        config = pagingConfig,
        pagingSourceFactory = {
            XQTPinnableTimelinePagingSource(
                accountKey = accountKey,
                loadTimelines = {
                    getPinnedTimelinesManagementSheet()
                        .body()
                        ?.tagPinnedTimelines
                        .orEmpty()
                },
            )
        },
    ).flow

internal class XQTPinnableTimelinePagingSource(
    private val accountKey: MicroBlogKey,
    private val loadTimelines: suspend () -> List<TagPinnedTimeline>,
) : PagingSource<Int, TimelineCandidate<*>>() {
    override fun getRefreshKey(state: PagingState<Int, TimelineCandidate<*>>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TimelineCandidate<*>> =
        try {
            LoadResult.Page(
                data = loadTimelines().map { it.toTimelineCandidate(accountKey) },
                prevKey = null,
                nextKey = null,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            LoadResult.Error(error)
        }
}

internal fun TagPinnedTimeline.toTimelineCandidate(accountKey: MicroBlogKey): TimelineCandidate<*> =
    XqtPlatformSpec.tagTimelineSpec.candidate(
        data =
            XqtTagTimelineData(
                accountKey = accountKey,
                tag = tag,
            ),
        title = UiText.Raw(title),
        icon = UiIcon.World.asType(),
    )
