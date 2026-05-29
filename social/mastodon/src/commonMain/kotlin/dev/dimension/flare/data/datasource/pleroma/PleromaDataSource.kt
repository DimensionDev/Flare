package dev.dimension.flare.data.datasource.pleroma

import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.ReactionDataSource
import dev.dimension.flare.data.platform.MastodonCredential
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

internal class PleromaDataSource(
    override val accountKey: MicroBlogKey,
    instance: String,
    credentialFlow: Flow<MastodonCredential>,
) : MastodonDataSource(
        accountKey = accountKey,
        instance = instance,
        credentialFlow = credentialFlow,
    ),
    ReactionDataSource {
//    override fun react(
//        statusKey: MicroBlogKey,
//        hasReacted: Boolean,
//        reaction: String,
//    ) {
//    }
}
