package dev.dimension.flare.data.datasource.misskey

import androidx.paging.PagingData
import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.platform.MisskeyTimelineDataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

public interface MisskeyAntennaDataSource : MisskeyTimelineDataSource {
    public fun antennasList(): Flow<PagingData<UiList.Antenna>>
}

public interface MisskeyChannelDataSource : MisskeyTimelineDataSource {
    public val channelHandler: ListHandler<UiList.Channel>

    public val myFavoriteChannelHandler: ListHandler<UiList.Channel>

    public val ownedChannelHandler: ListHandler<UiList.Channel>

    public fun featuredChannels(scope: CoroutineScope): Flow<PagingData<UiList.Channel>>

    public suspend fun followChannel(data: UiList)

    public suspend fun unfollowChannel(data: UiList)

    public suspend fun favoriteChannel(data: UiList)

    public suspend fun unfavoriteChannel(data: UiList)
}

public interface MisskeyReportDataSource {
    public suspend fun report(
        userKey: MicroBlogKey,
        statusKey: MicroBlogKey?,
        comment: String,
    )
}
