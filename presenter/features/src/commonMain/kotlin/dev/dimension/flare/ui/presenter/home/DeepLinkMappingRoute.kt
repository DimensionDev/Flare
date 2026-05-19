package dev.dimension.flare.ui.presenter.home

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.route.DeeplinkRoute

internal fun DeepLinkMapping.Type.toDeeplinkRoute(accountKey: MicroBlogKey): DeeplinkRoute =
    when (this) {
        is DeepLinkMapping.Type.BlueskyPost ->
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey =
                    MicroBlogKey(
                        "at://$handle/app.bsky.feed.post/$id",
                        accountKey.host,
                    ),
            )

        is DeepLinkMapping.Type.Post ->
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = MicroBlogKey(id, accountKey.host),
            )

        is DeepLinkMapping.Type.PostMedia ->
            DeeplinkRoute.Media.StatusMedia(
                accountType = AccountType.Specific(accountKey),
                statusKey = MicroBlogKey(id, accountKey.host),
                index = index,
                preview = null,
            )

        is DeepLinkMapping.Type.Profile -> {
            val (userName, host) =
                if (handle.contains('@')) {
                    MicroBlogKey.valueOf(handle)
                } else {
                    MicroBlogKey(handle, accountKey.host)
                }
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(accountKey),
                userName = userName,
                host = host,
            )
        }
    }
