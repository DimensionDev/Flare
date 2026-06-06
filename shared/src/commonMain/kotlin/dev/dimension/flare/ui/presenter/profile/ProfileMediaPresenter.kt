package dev.dimension.flare.ui.presenter.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.flatMap
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.common.toPagingState
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.home.TimelinePresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public class ProfileMediaPresenter(
    private val mediaTimelinePresenter: TimelinePresenter,
    private val showAllImages: Boolean,
) : PresenterBase<ProfileMediaState>() {
    @Composable
    override fun body(): ProfileMediaState {
        val scope = rememberCoroutineScope()
        val state =
            remember(mediaTimelinePresenter, showAllImages) {
                mediaTimelinePresenter.createTransformedPager(
                    scope = scope,
                    showAllImages = showAllImages,
                )
            }.collectAsLazyPagingItems()
                .toPagingState()
        return object : ProfileMediaState {
            override val mediaState = state
        }
    }

    public fun getMediaTimelinePresenter(): TimelinePresenter = mediaTimelinePresenter
}

private fun TimelinePresenter.createTransformedPager(
    scope: CoroutineScope,
    showAllImages: Boolean,
): Flow<PagingData<ProfileMedia>> =
    createPager(scope).map { data ->
        data.flatMap { status ->
            if (status is UiTimelineV2.Post) {
                if (showAllImages) {
                    status.images.mapIndexed { index, it ->
                        ProfileMedia(
                            it,
                            status,
                            status.statusKey,
                            index,
                        )
                    }
                } else {
                    status.images
                        .firstOrNull()
                        ?.let {
                            listOf(
                                ProfileMedia(
                                    it,
                                    status,
                                    status.statusKey,
                                    0,
                                ),
                            )
                        }.orEmpty()
                }
            } else {
                emptyList()
            }
        }
    }

@Immutable
public interface ProfileMediaState {
    public val mediaState: PagingState<ProfileMedia>
}

@Immutable
public data class ProfileMedia internal constructor(
    val media: UiMedia,
    val status: UiTimelineV2,
    val statusKey: MicroBlogKey,
    val index: Int,
) {
    val key: String = "$statusKey-$index"
}
