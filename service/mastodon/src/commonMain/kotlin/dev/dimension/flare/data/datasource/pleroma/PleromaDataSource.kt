package dev.dimension.flare.data.datasource.pleroma

import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.ReactionDataSource
import dev.dimension.flare.model.MicroBlogKey

public class PleromaDataSource(
    override val accountKey: MicroBlogKey,
    instance: String,
) : MastodonDataSource(
        accountKey = accountKey,
        instance = instance,
    ),
    ReactionDataSource {
//    override fun react(
//        statusKey: MicroBlogKey,
//        hasReacted: Boolean,
//        reaction: String,
//    ) {
//    }
}
