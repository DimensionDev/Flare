package dev.dimension.flare.data.datasource.pleroma

import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.ReactionDataSource
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.model.MicroBlogKey

internal class PleromaDataSource(
    override val accountKey: MicroBlogKey,
    instance: String,
    accessToken: String,
) : MastodonDataSource(
        accountKey = accountKey,
        instance = instance,
        accessToken = accessToken,
    ),
    ReactionDataSource,
    StatusEvent.Pleroma {
    override fun react(
        statusKey: MicroBlogKey,
        hasReacted: Boolean,
        reaction: String,
    ) {
    }
}
