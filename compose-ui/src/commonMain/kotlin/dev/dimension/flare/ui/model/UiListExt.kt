package dev.dimension.flare.ui.model

import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

public fun UiList.toTabItem(accountKey: MicroBlogKey): TabItem =
    when (this) {
        is UiList.List ->
            ListTimelineTabItem(
                account = AccountType.Specific(accountKey),
                listId = id,
                metaData =
                    TabMetaData(
                        title = TitleType.Text(title),
                        icon =
                            avatar?.let {
                                IconType.Url(it)
                            } ?: IconType.Material(IconType.Material.MaterialIcon.List),
                    ),
            )

        is UiList.Feed ->
            Bluesky.FeedTabItem(
                account = AccountType.Specific(accountKey),
                uri = id,
                metaData =
                    TabMetaData(
                        title = TitleType.Text(title),
                        icon =
                            avatar?.let {
                                IconType.Url(it)
                            } ?: IconType.Material(IconType.Material.MaterialIcon.Feeds),
                    ),
            )

        is UiList.Antenna ->
            Misskey.AntennasTimelineTabItem(
                account = AccountType.Specific(accountKey),
                antennasId = id,
                metaData =
                    TabMetaData(
                        title = TitleType.Text(title),
                        icon = IconType.Material(IconType.Material.MaterialIcon.List),
                    ),
            )

        is UiList.Channel ->
            Misskey.ChannelTimelineTabItem(
                account = AccountType.Specific(accountKey),
                channelId = id,
                metaData =
                    TabMetaData(
                        title = TitleType.Text(title),
                        icon = IconType.Material(IconType.Material.MaterialIcon.List),
                    ),
            )
    }
