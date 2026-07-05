package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.model.MicroBlogKey
import kotlin.test.Test
import kotlin.test.assertFalse

class StatusDetailRemoteMediatorTest {
    @Test
    fun statusDetailDoesNotCollapseVisibleThreadRepliesIntoInlineParents() {
        val accountKey = MicroBlogKey(id = "account", host = "x.com")

        val mediator =
            StatusDetailRemoteMediator(
                statusKey = MicroBlogKey(id = "2073225283688161359", host = "x.com"),
                service = XQTService(),
                accountKey = accountKey,
                statusOnly = false,
            )

        assertFalse(mediator.collapseReplyChains)
    }
}
