package dev.dimension.flare.ui.model

import dev.dimension.flare.data.model.Bluesky.FeedTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.IconType.Mixed
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.Misskey
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.AccountType.Specific
import dev.dimension.flare.model.MicroBlogKey

public fun UiList.toTabItem(accountKey: MicroBlogKey): TabItem =
    when (type) {
        UiList.Type.Feed -> {
            FeedTabItem(
                account = Specific(accountKey),
                uri = id,
                metaData =
                    TabMetaData(
                        title = TitleType.Text(title),
                        icon =
                            Mixed(
                                icon = IconType.Material.MaterialIcon.List,
                                userKey = accountKey,
                            ),
                    ),
            )
        }

        UiList.Type.List ->
            ListTimelineTabItem(
                account = AccountType.Specific(accountKey),
                listId = id,
                metaData =
                    TabMetaData(
                        title = TitleType.Text(title),
                        icon =
                            IconType.Mixed(
                                icon = IconType.Material.MaterialIcon.List,
                                userKey = accountKey,
                            ),
                    ),
            )

        UiList.Type.Antenna ->
            Misskey.AntennasTimelineTabItem(
                account = AccountType.Specific(accountKey),
                id = id,
                metaData =
                    TabMetaData(
                        title = TitleType.Text(title),
                        icon =
                            IconType.Mixed(
                                icon = IconType.Material.MaterialIcon.Rss,
                                userKey = accountKey,
                            ),
                    ),
            )
    }
