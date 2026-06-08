package dev.dimension.flare.data.datasource.mastodon

import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertFalse

class StatusDetailRemoteMediatorTest {
    @Test
    fun statusContextDoesNotUseGenericReplyChainCollapse() {
        val accountKey = MicroBlogKey(id = "me", host = "mastodon.social")
        val mediator =
            StatusDetailRemoteMediator(
                statusKey = MicroBlogKey(id = "116526337257255071", host = "mastodon.social"),
                service = MastodonService("https://mastodon.social/", flowOf("")),
                accountKey = accountKey,
                statusOnly = false,
            )

        assertFalse(mediator.collapseReplyChains)
    }
}
