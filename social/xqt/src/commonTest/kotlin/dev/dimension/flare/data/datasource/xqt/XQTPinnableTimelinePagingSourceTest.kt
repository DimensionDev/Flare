package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingSource
import dev.dimension.flare.data.network.xqt.model.TagPinnedTimeline
import dev.dimension.flare.data.platform.XqtPlatformSpec
import dev.dimension.flare.data.platform.XqtTagTimelineData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class XQTPinnableTimelinePagingSourceTest {
    @Test
    fun mapsTagPinnedTimelineToRegisteredTimelineSpec() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "x.com")
            val pagingSource =
                XQTPinnableTimelinePagingSource(accountKey) {
                    listOf(
                        TagPinnedTimeline(
                            name = "Technology",
                            tabLabel = "Tech",
                            tag = "tag-id",
                        ),
                    )
                }

            val result =
                pagingSource.load(
                    PagingSource.LoadParams.Refresh(
                        key = null,
                        loadSize = 20,
                        placeholdersEnabled = false,
                    ),
                )
            val candidate = assertIs<PagingSource.LoadResult.Page<Int, *>>(result).data.single()
            val timelineCandidate = assertIs<dev.dimension.flare.data.model.tab.TimelineCandidate<*>>(candidate)
            val data = assertIs<XqtTagTimelineData>(timelineCandidate.target.data)

            assertSame(XqtPlatformSpec.tagTimelineSpec, timelineCandidate.target.spec)
            assertEquals(accountKey, data.accountKey)
            assertEquals("tag-id", data.tag)
            assertEquals(UiText.Raw("Tech"), timelineCandidate.title)
            assertEquals(UiIcon.World.asType(), timelineCandidate.icon)
        }
}
